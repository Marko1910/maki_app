package com.example.maki.ui.components

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.example.maki.data.StreetRouter
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** A point to drop on the map, with an optional title shown on tap and a pin colour (ARGB). */
data class OsmMarker(val point: GeoPoint, val label: String? = null, val color: Int? = null, val current: Boolean = false)

/**
 * OpenStreetMap (osmdroid) map — no API key required. Renders nothing in
 * @Preview (osmdroid needs a real context + network), so previews keep working.
 *
 * Two things osmdroid needs that are easy to miss, and both showed as a blank
 * grey map: [Configuration.load] must run before the first MapView (otherwise the
 * tile cache defaults to external storage, which scoped storage denies), and the
 * app needs ACCESS_NETWORK_STATE for its tile downloader to see the connection.
 *
 * Frames initially, then respects manual gestures. Navigation can follow the
 * current-location marker and explicitly recenter without fighting a manual pan.
 */
@Composable
fun OsmMap(
    center: GeoPoint,
    modifier: Modifier = Modifier,
    zoom: Double = 16.0,
    markers: List<OsmMarker> = emptyList(),
    /** Optional path drawn on the map, in travel order (street geometry or straight legs). */
    route: List<GeoPoint> = emptyList(),
    followFirst: Boolean = false,
    recenter: Int = 0,
    onMapGesture: () -> Unit = {},
) {
    if (LocalInspectionMode.current) return

    val context = LocalContext.current
    val gesture by rememberUpdatedState(onMapGesture)
    var framed by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var hadRoute by remember { mutableStateOf(false) }
    var previousRecenter by remember { mutableStateOf(recenter) }
    val mapView = remember {
        ensureOsmConfig(context)
        MapView(context).apply {
            setTileSource(OsmTiles)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_MOVE) { touched = true; gesture() }
                false
            }
            // 256 px tiles drawn 1:1 on a 3x phone make street names unreadable.
            setTilesScaledToDpi(true)
            controller.setZoom(zoom)
            controller.setCenter(center)
        }
    }

    // Markers and the route arrive after the first composition (the data is
    // loading), so they are applied here rather than baked into the constructor.
    LaunchedEffect(markers, route, followFirst, recenter) {
        mapView.overlays.clear()
        if (route.size >= 2) {
            val density = context.resources.displayMetrics.density
            mapView.overlays.add(Polyline(mapView).apply {
                setPoints(route)
                outlinePaint.strokeWidth = 8f * density
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.color = MakiColors.Surface.toArgb()
            })
            mapView.overlays.add(
                Polyline(mapView).apply {
                    setPoints(route)
                    outlinePaint.strokeWidth = 4f * density
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.color = MakiColors.Rider.toArgb()
                }
            )
        }
        markers.forEach { m ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = m.point
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = m.label
                    if (m.current) {
                        val density = context.resources.displayMetrics.density
                        icon = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(MakiColors.Rider.toArgb())
                            setStroke((3 * density).toInt(), MakiColors.Surface.toArgb())
                            setSize((22 * density).toInt(), (22 * density).toInt())
                        }
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    // newDrawable(): the default pin is shared by every Marker, so
                    // tinting it in place would repaint all of them.
                    if (m.color != null && !m.current) {
                        icon = icon?.constantState?.newDrawable()?.mutate()?.apply { setTint(m.color) } ?: icon
                    }
                }
            )
        }
        val requestedCenter = previousRecenter != recenter
        if (requestedCenter) { touched = false; previousRecenter = recenter }
        if (followFirst && !touched) {
            mapView.controller.setCenter(markers.firstOrNull { it.current }?.point ?: center)
            if (!framed || requestedCenter) mapView.controller.setZoom(zoom)
            framed = true
        } else if (!framed || (!hadRoute && route.size >= 2 && !touched)) {
            frame(mapView, markers.map { it.point } + route, center, zoom)
            framed = markers.isNotEmpty() || route.isNotEmpty()
        }
        hadRoute = route.size >= 2
        mapView.invalidate()
    }

    // clipToBounds: Compose does not clip an AndroidView by itself, and osmdroid
    // paints tiles across the whole canvas — the map covered the screen's header
    // and showed through the cards below it.
    Box(modifier.clipToBounds()) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        Text("© OpenStreetMap contributors", style = MakiType.caption, color = MakiColors.Text2,
            modifier = Modifier.align(Alignment.BottomEnd).background(MakiColors.Surface).padding(horizontal = MakiSpace.xs))
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }
}

/**
 * Keeps both ends in view — the rider AND the house, or every stop of the round.
 * A fixed centre on the rider showed one end of the trip and an empty
 * neighbourhood around it, which reads as "the map doesn't work".
 *
 * ponytail: re-frames on every data refresh, so a pinch-zoom lasts until the next
 * poll (10 s). Track "user moved the map" and skip the re-frame if that annoys.
 */
private fun frame(map: MapView, points: List<GeoPoint>, fallback: GeoPoint, zoom: Double) {
    val apply = {
        when {
            points.size >= 2 -> map.zoomToBoundingBox(
                BoundingBox.fromGeoPointsSafe(points).increaseByScale(1.6f), false, FRAME_PADDING_PX, MAX_FRAME_ZOOM, null,
            )
            points.size == 1 -> { map.controller.setZoom(zoom); map.controller.setCenter(points.first()) }
            else -> map.controller.setCenter(fallback)
        }
    }
    // Framing needs the view's size; before the first layout it would fit a 0×0 box.
    if (map.isLayoutOccurred) apply() else map.addOnFirstLayoutListener { _, _, _, _, _ -> apply() }
}

/** Two pins a few metres apart would otherwise zoom past the last tile level. */
private const val MAX_FRAME_ZOOM = 17.5
private const val FRAME_PADDING_PX = 72

/**
 * OSM's standard tiles with our own request policy. osmdroid's built-in MAPNIK
 * asks for a "normalized" User-Agent, which osmdroid computes inside
 * [Configuration.load] from the package + version — "com.example.maki/1" — and the
 * tile servers answer that with the 403 "Access blocked" tile. Setting
 * userAgentValue afterwards never reached the request. Without the NORMALIZED flag
 * the request carries [StreetRouter.USER_AGENT] (verified on a device, 2026-09-11).
 * A new name also keeps osmdroid from reusing tiles cached under "Mapnik".
 */
private val OsmTiles = XYTileSource(
    "MakiOsm", 0, 19, 256, ".png",
    arrayOf("https://tile.openstreetmap.org/"),
    "© OpenStreetMap contributors",
    TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_NO_BULK or TileSourcePolicy.FLAG_NO_PREVENTIVE or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL,
    ),
)

/**
 * Loads osmdroid's configuration once and pins its cache inside the app's own
 * cacheDir — no storage permission, and nothing left behind on uninstall.
 */
private fun ensureOsmConfig(context: Context) {
    val config = Configuration.getInstance()
    if (config.userAgentValue == StreetRouter.USER_AGENT) return
    val prefs = context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
    config.load(context, prefs)
    config.userAgentValue = StreetRouter.USER_AGENT
    config.osmdroidBasePath = File(context.cacheDir, "osmdroid").apply { mkdirs() }
    config.osmdroidTileCache = File(config.osmdroidBasePath, "tiles").apply { mkdirs() }

    // "Access blocked" tiles already on disk would keep being drawn. Wipe the
    // cache once whenever the way we fetch tiles changes.
    // ponytail: blocking delete on the main thread — it runs once, on a cache
    // measured in a few MB, before the first MapView exists.
    if (prefs.getString(CACHE_MARKER_KEY, null) != CACHE_MARKER) {
        config.osmdroidTileCache.deleteRecursively()
        config.osmdroidTileCache.mkdirs()
        prefs.edit().putString(CACHE_MARKER_KEY, CACHE_MARKER).apply()
    }
}

private const val CACHE_MARKER_KEY = "maki_user_agent"
private const val CACHE_MARKER = "${StreetRouter.USER_AGENT}|MakiOsm"
