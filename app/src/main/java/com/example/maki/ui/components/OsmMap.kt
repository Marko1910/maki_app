package com.example.maki.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** A point to drop on the map, with an optional title shown on tap. */
data class OsmMarker(val point: GeoPoint, val label: String? = null)

/**
 * OpenStreetMap (osmdroid) map — no API key required. Renders nothing in
 * @Preview (osmdroid needs a real context + network), so previews keep working.
 *
 * Two things osmdroid needs that are easy to miss, and both showed as a blank
 * grey map: [Configuration.load] must run before the first MapView (otherwise the
 * tile cache defaults to external storage, which scoped storage denies), and the
 * app needs ACCESS_NETWORK_STATE for its tile downloader to see the connection.
 */
@Composable
fun OsmMap(
    center: GeoPoint,
    modifier: Modifier = Modifier,
    zoom: Double = 16.0,
    markers: List<OsmMarker> = emptyList(),
    /** Optional path drawn through the stops, in visiting order. */
    route: List<GeoPoint> = emptyList(),
) {
    if (LocalInspectionMode.current) return

    val context = LocalContext.current
    val mapView = remember {
        ensureOsmConfig(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(zoom)
            controller.setCenter(center)
        }
    }

    // Markers and the route line arrive after the first composition (the data is
    // loading), so they are applied here rather than baked into the constructor.
    LaunchedEffect(markers, route) {
        mapView.overlays.clear()
        if (route.size >= 2) {
            mapView.overlays.add(
                Polyline(mapView).apply {
                    setPoints(route)
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.color = ROUTE_COLOR
                }
            )
        }
        markers.forEach { m ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = m.point
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = m.label
                }
            )
        }
        mapView.invalidate()
    }

    LaunchedEffect(center) { mapView.controller.setCenter(center) }

    AndroidView(factory = { mapView }, modifier = modifier)

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }
}

/** Rider blue (#185FA5) for the drawn route. */
private const val ROUTE_COLOR = 0xFF185FA5.toInt()

/**
 * The User-Agent OSM sees. It must identify *this* app: the tile policy blocks
 * generic and tutorial UAs — `com.example.*` among them — and a blocked client
 * gets served a tile that literally reads "Access blocked", which is what the
 * rider's map was showing.
 */
private const val OSM_USER_AGENT = "MAKI-Reciclaje/1.0 (Android)"

/**
 * Loads osmdroid's configuration once and pins its cache inside the app's own
 * cacheDir — no storage permission, and nothing left behind on uninstall.
 */
private fun ensureOsmConfig(context: Context) {
    val config = Configuration.getInstance()
    if (config.userAgentValue == OSM_USER_AGENT) return
    val prefs = context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
    config.load(context, prefs)
    config.userAgentValue = OSM_USER_AGENT
    config.osmdroidBasePath = File(context.cacheDir, "osmdroid").apply { mkdirs() }
    config.osmdroidTileCache = File(config.osmdroidBasePath, "tiles").apply { mkdirs() }

    // Those "Access blocked" tiles were cached on disk, so a new UA alone would
    // still redraw them. Wipe the cache once, the first run after the UA changes.
    // ponytail: blocking delete on the main thread — it runs once, on a cache
    // measured in a few MB, before the first MapView exists.
    if (prefs.getString(UA_PREF_KEY, null) != OSM_USER_AGENT) {
        config.osmdroidTileCache.deleteRecursively()
        config.osmdroidTileCache.mkdirs()
        prefs.edit().putString(UA_PREF_KEY, OSM_USER_AGENT).apply()
    }
}

private const val UA_PREF_KEY = "maki_user_agent"
