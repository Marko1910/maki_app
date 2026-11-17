package pe.ruma.app.ui.screens.rider

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.ruma.app.data.RoutePlanner
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.HeroRule
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaSkeleton
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.OsmMap
import pe.ruma.app.ui.components.OsmMarker
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.ScreenHeader
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.SmallButton
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType
import org.osmdroid.util.GeoPoint

/** A map pin for a stop; absent when the pickup has no coordinates yet. */
data class RouteMarkerUi(val lat: Double, val lng: Double, val label: String)

data class RouteStopUi(
    val number: String,             // "1".."N"; blank for endpoints
    val endpoint: String? = null,   // "start" | "end" | null (normal stop)
    val name: String,
    val subtitle: String,           // address, or endpoint subtitle
    val eta: String? = null,
    /** Clock time this stop is reached ("Llegas 4:35 p. m."), from the street legs. */
    val arrival: String? = null,
    val material: String? = null,
    val streak: String? = null,
    val badge: String? = null,
    val highlighted: Boolean = false,
    /** Already collected or skipped: shown dimmed, and not navigable. */
    val done: Boolean = false,
    val marker: RouteMarkerUi? = null,
    /** Which pickup "Navegar" opens. */
    val pickupId: String? = null,
)

data class RiderRouteUiState(
    val title: String = "Ruta del Día",
    val optimizedNote: String = "",
    val stops: List<RouteStopUi> = emptyList(),
    val totalDistance: String = "—",
    val totalTime: String = "—",
    /** When the whole route ends ("Terminas ~6:10 p. m."); blank without a street answer. */
    val finishLabel: String = "",
    val estimatedEarnings: String = "",
    val markers: List<RouteMarkerUi> = emptyList(),
    /** Street geometry through the stops, when the router answered. */
    val path: List<RoutePlanner.LatLng> = emptyList(),
    /** Where the rider is: the route starts here, so the map shows it. */
    val here: RoutePlanner.LatLng? = null,
)

/**
 * The rider's stops for today: the operator's planned route when one exists,
 * otherwise the pickups this rider has claimed, in the fastest order by road.
 */
@Composable
fun RiderRouteScreen(
    state: RiderRouteUiState? = null,
    error: String? = null,
    onBack: () -> Unit = {},
    /** Opens navigation to that pickup (null = the next one in order). */
    onNavigateStop: (String?) -> Unit = {},
    onStartNavigation: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    // A tab, like the PWA's: no back button; the map, the hero, then the stops.
    Column(Modifier.fillMaxSize().rumaScreen(RumaColors.Rider).statusBarsPadding()) {
        ScreenHeader(state?.title ?: "Ruta del Día")

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            when {
                error != null -> item { RumaError(error, onRetry, accent = RumaColors.Rider) }
                state == null -> item { RumaSkeleton(listOf(96.dp, 72.dp, 72.dp), label = "Cargando tu ruta…") }
                state.stops.isEmpty() -> item {
                    RumaEmpty(
                        icon = Icons.Filled.Route,
                        title = "Sin paradas por ahora",
                        hint = "Toma un recojo desde el tablero y aparecerá aquí, en orden de ruta.",
                        accent = RumaColors.Rider,
                    )
                }
                else -> {
                    if (state.markers.isNotEmpty()) item { MiniMap(state.markers, state.path, state.here) }
                    item { RouteHero(state) }
                    item { SectionLabel("Paradas de tu ruta") }
                    item { RumaList(state.stops) { stop -> StopRow(stop) { onNavigateStop(stop.pickupId) } } }
                    item {
                        PrimaryButton(
                            "Iniciar navegación", Modifier.fillMaxWidth(), icon = Icons.Filled.Navigation,
                            color = RumaColors.Rider, onClick = onStartNavigation,
                        )
                    }
                }
            }
        }
    }
}

/** How many stops, when the rider is done, how far and what it pays (PWA route hero). */
@Composable
private fun RouteHero(state: RiderRouteUiState) {
    val count = state.stops.count { it.endpoint == null }
    Column(
        Modifier.fillMaxWidth().rumaHero(RumaGradient.rider, glow = RumaGradient.glowOf(RumaColors.Rider))
            .padding(RumaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        Text(
            state.optimizedNote.ifBlank { "Tu ruta · $count parada${if (count == 1) "" else "s"}" }.uppercase(),
            color = RumaColors.OnRiderSoft, style = RumaType.overline,
        )
        Text(state.finishLabel.ifBlank { state.totalTime }, color = RumaColors.OnDark, style = RumaType.screenTitle)
        if (state.finishLabel.isNotBlank() && state.totalTime != "—") {
            Text("${state.totalTime} en ruta", color = RumaColors.OnRiderSoft, style = RumaType.bodySmall)
        }
        HeroRule()
        Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
            HeroFigure("Distancia", state.totalDistance, Modifier.weight(1f))
            HeroFigure("Ganancia estimada", state.estimatedEarnings.ifBlank { "—" }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroFigure(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = RumaColors.OnRiderSoft, style = RumaType.bodySmall)
        Text(value, color = RumaColors.OnDark, style = RumaType.sectionHeading)
    }
}

@Composable
private fun MiniMap(markers: List<RouteMarkerUi>, path: List<RoutePlanner.LatLng>, here: RoutePlanner.LatLng? = null) {
    val first = markers.first()
    Box(
        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(RumaRadius.card))
            .border(1.dp, RumaColors.Hairline, RoundedCornerShape(RumaRadius.card)),
    ) {
        OsmMap(
            center = GeoPoint(first.lat, first.lng),
            modifier = Modifier.matchParentSize(),
            zoom = 15.0,
            // The route starts at the rider: without "Tú" the first leg starts nowhere.
            markers = markers.map { OsmMarker(GeoPoint(it.lat, it.lng), it.label, RumaColors.Text.toArgb()) } +
                listOfNotNull(here?.let { OsmMarker(GeoPoint(it.lat, it.lng), "Tú", current = true) }),
            // Without a road response, pins remain visible but no road is invented.
            route = path.map { GeoPoint(it.lat, it.lng) },
        )
    }
}

/** A numbered stop with its own "Navegar"; endpoints carry an icon, done stops fade. */
@Composable
private fun StopRow(stop: RouteStopUi, onNavigateStop: () -> Unit) {
    val details = listOfNotNull(
        stop.eta?.let { "a $it" }, stop.arrival, stop.material, stop.streak, stop.badge,
    ).joinToString(" · ").ifBlank { null }
    RumaRow(
        stop.name,
        modifier = Modifier.alpha(if (stop.done) 0.6f else 1f).semantics(mergeDescendants = true) {
            contentDescription = "Parada ${stop.number}, ${stop.name}, ${stop.subtitle}" +
                (details?.let { ", $it" } ?: "") + (if (stop.done) ", completada" else "")
        },
        subtitle = stop.subtitle,
        highlight = if (stop.highlighted) RumaColors.Rider else null,
        leading = {
            when (stop.endpoint) {
                "start" -> GlassBadge(icon = Icons.Filled.TripOrigin, size = 32.dp, circle = true, fill = RumaColors.Success, content = RumaColors.OnDark, ring = RumaColors.Success)
                "end" -> GlassBadge(icon = Icons.Filled.Flag, size = 32.dp, circle = true)
                else -> GlassBadge(text = stop.number, size = 32.dp, circle = true, fill = RumaColors.Rider, content = RumaColors.OnDark, ring = RumaColors.Rider)
            }
        },
        extra = {
            if (details != null) Text(details, color = RumaColors.Text2, style = RumaType.caption)
            if (stop.done) StatusChip("Parada completada", RumaColors.Success)
        },
        trailing = if (stop.endpoint == null && !stop.done) {
            { SmallButton("Navegar", color = RumaColors.Rider, onClick = onNavigateStop) }
        } else null,
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun RiderRoutePreview() {
    RumaTheme {
        RiderRouteScreen(
            state = SampleRoute,
        )
    }
}

private val SampleRoute = RiderRouteUiState(
    optimizedNote = "Ruta más rápida por calles · 2 paradas",
    stops = listOf(
        RouteStopUi("1", null, "Fam. Nureña", "Jr. Las Flores 123", material = "8 PET + 3 latas", highlighted = true),
        RouteStopUi("2", null, "Fam. Torres", "Jr. Río Huallaga 312", material = "6 cartones + 4 vidrios"),
    ),
    totalDistance = "2.3 km total", totalTime = "19 min",
    estimatedEarnings = "S/5.62",
)
