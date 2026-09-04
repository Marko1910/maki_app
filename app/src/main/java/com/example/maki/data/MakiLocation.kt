package com.example.maki.data

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
object MakiLocation {

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
    fun lastKnown(context: Context): RoutePlanner.LatLng? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val fix = runCatching {
            lm.getProviders(true)
                .mapNotNull { provider -> lm.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return null
        return RoutePlanner.LatLng(fix.latitude, fix.longitude)
    }

    /**
     * A fix good enough to register a home on: the cached one when it exists,
     * otherwise waits up to [timeoutMs] for the hardware to produce one. Null when
     * the user denied permission, has location off, or is somewhere with no signal.
     */
    suspend fun current(context: Context, timeoutMs: Long = 12_000): RoutePlanner.LatLng? {
        lastKnown(context)?.let { return it }
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return withTimeoutOrNull(timeoutMs) { requestSingleFix(lm, context) }
    }

    @Suppress("MissingPermission")   // hasPermission() gates every call site above
    private suspend fun requestSingleFix(lm: LocationManager, context: Context): RoutePlanner.LatLng? =
        suspendCancellableCoroutine { cont ->
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) { cont.resume(null); return@suspendCancellableCoroutine }

            val deliver: (Location?) -> Unit = { loc ->
                if (cont.isActive) cont.resume(loc?.let { RoutePlanner.LatLng(it.latitude, it.longitude) })
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
