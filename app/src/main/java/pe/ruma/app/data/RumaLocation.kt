package pe.ruma.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Device position, shared by both roles: the family registers where their pickup
 * happens, the rider reports where they are so the family can watch them approach.
 *
 * Uses the platform LocationManager — no Play Services dependency, works on a
 * plain AOSP device, and is enough for street-level accuracy.
 */
object RumaLocation {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Newest cached fix. Null when permission is missing, location is off, or
     * nothing is cached yet — instant, no GPS wake-up, right for ordering stops
     * a few hundred metres apart.
     */
    fun lastKnown(context: Context, maxAgeMs: Long = Long.MAX_VALUE): RoutePlanner.LatLng? {
        val fix = lastKnownFix(context, maxAgeMs) ?: return null
        return RoutePlanner.LatLng(fix.latitude, fix.longitude)
    }

    private fun lastKnownFix(context: Context, maxAgeMs: Long): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val fix = runCatching {
            lm.getProviders(true)
                .mapNotNull { provider -> lm.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return null
        if (System.currentTimeMillis() - fix.time > maxAgeMs) return null
        return fix
    }

    data class NavigationFix(val point: RoutePlanner.LatLng, val accuracyMeters: Float, val time: Long)

    /** Navigation keeps accuracy and age; an imprecise fix must not trigger turns or arrival. */
    suspend fun navigationFix(context: Context): NavigationFix? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val location = lastKnownFix(context, 3_000)?.takeIf { it.hasAccuracy() && it.accuracy <= 60 }
            ?: withTimeoutOrNull(8_000) { requestNavigationFix(lm) } ?: return null
        if (System.currentTimeMillis() - location.time > 15_000 || !location.hasAccuracy()) return null
        return NavigationFix(RoutePlanner.LatLng(location.latitude, location.longitude), location.accuracy, location.time)
    }

    /** Race enabled sources: satellite acquisition indoors must not block a precise network fix. */
    @Suppress("MissingPermission")
    private suspend fun requestNavigationFix(lm: LocationManager): Location? =
        suspendCancellableCoroutine { cont ->
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            if (providers.isEmpty()) { cont.resume(null); return@suspendCancellableCoroutine }
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!location.hasAccuracy() || location.accuracy > 60 ||
                        System.currentTimeMillis() - location.time > 15_000) return
                    runCatching { lm.removeUpdates(this) }
                    if (cont.isActive) cont.resume(location)
                }
                @Deprecated("Required on API < 29")
                override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                override fun onProviderDisabled(p: String) {}
                override fun onProviderEnabled(p: String) {}
            }
            cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            var registered = false
            providers.forEach { provider ->
                if (cont.isActive) {
                    registered = runCatching {
                        lm.requestLocationUpdates(provider, 0L, 0f, listener, android.os.Looper.getMainLooper())
                    }.isSuccess || registered
                }
            }
            // Covers cancellation racing with listener registration.
            if (!cont.isActive) runCatching { lm.removeUpdates(listener) }
            else if (!registered) cont.resume(null)
        }

    /**
     * A fix good enough to register a home on: the cached one when it exists (and
     * is younger than [maxAgeMs]), otherwise waits up to [timeoutMs] for the
     * hardware to produce one. Null when the user denied permission, has location
     * off, or is somewhere with no signal.
     *
     * A moving rider must pass a small [maxAgeMs]: an hours-old cached fix got
     * published as "where Carlos is now" and put him 500 km from his route.
     */
    suspend fun current(context: Context, timeoutMs: Long = 12_000, maxAgeMs: Long = Long.MAX_VALUE): RoutePlanner.LatLng? {
        lastKnown(context, maxAgeMs)?.let { return it }
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return withTimeoutOrNull(timeoutMs) { requestSingleFix(lm, context) }?.let { RoutePlanner.LatLng(it.latitude, it.longitude) }
    }

    @Suppress("MissingPermission")   // hasPermission() gates every call site above
    private suspend fun requestSingleFix(lm: LocationManager, context: Context): Location? =
        suspendCancellableCoroutine { cont ->
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) { cont.resume(null); return@suspendCancellableCoroutine }

            val deliver: (Location?) -> Unit = { loc ->
                if (cont.isActive) cont.resume(loc)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { runCatching { signal.cancel() } }
                    lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { deliver(it) }
                } else {
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            lm.removeUpdates(this)
                            deliver(location)
                        }
                        @Deprecated("Required on API < 29")
                        override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                        override fun onProviderDisabled(p: String) { deliver(null) }
                        override fun onProviderEnabled(p: String) {}
                    }
                    cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, android.os.Looper.getMainLooper())
                }
            }.onFailure { cont.resume(null) }
        }

    /**
     * Street address for a fix, or null when the device has no geocoder backend
     * (common on AOSP/emulator images) or is offline. The caller falls back to
     * letting the user type the address.
     */
    suspend fun describe(context: Context, at: RoutePlanner.LatLng): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION")   // the async overload is API 33+; minSdk here is 24
            Geocoder(context, Locale("es", "PE")).getFromLocation(at.lat, at.lng, 1)
                ?.firstOrNull()
                ?.let { a ->
                    // "Jr. Las Flores 123, San Juan de Lurigancho" — street + district
                    // is what a rider actually needs; the country adds nothing.
                    val street = listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(" ")
                        .ifBlank { a.featureName.orEmpty() }
                    val area = a.locality ?: a.subAdminArea ?: a.adminArea
                    listOf(street, area).filter { !it.isNullOrBlank() }.joinToString(", ")
                        .takeIf { it.isNotBlank() }
                }
        }.getOrNull()
    }
}
