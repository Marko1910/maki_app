package com.example.maki.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.maki.data.RoutePlanner
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.HeroCard
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MakiLoading
import com.example.maki.ui.components.MetricTile
import com.example.maki.ui.components.NavRow
import com.example.maki.ui.components.OsmMap
import com.example.maki.ui.components.OsmMarker
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.StatusChip
import com.example.maki.ui.components.makiCard
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType
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
    Column(Modifier.fillMaxSize().makiScreen(MakiColors.Rider).statusBarsPadding()) {
        DetailHeader(title = state?.title ?: "Ruta del Día", onBack = onBack)

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = MakiSpace.gutter, end = MakiSpace.gutter, top = MakiSpace.xs / 2, bottom = MakiSpace.xl),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
        ) {
            when {
                error != null -> item { MakiError(error, onRetry) }
                state == null -> item { MakiLoading("Cargando tu ruta…", accent = MakiColors.Rider) }
                state.stops.isEmpty() -> item {
                    MakiEmpty(
                        icon = Icons.Filled.Route,
                        title = "Sin paradas por ahora",
                        hint = "Acepta un recojo desde el inicio y aparecerá aquí, en orden de ruta.",
                        accent = MakiColors.Rider,
                    )
                }
                else -> {
                    item {
                        HeroCard(MakiColors.Rider, "Ruta del Día", state.totalTime,
                            state.finishLabel.takeIf { it.isNotBlank() })
                    }
                    item { ResumenCard(state) }
                    if (state.optimizedNote.isNotBlank()) item { OptimizedBadge(state.optimizedNote) }
                    if (state.markers.isNotEmpty()) item { MiniMap(state.markers, state.path, state.here) }
                    item { SectionLabel("Paradas de tu ruta") }
                    items(state.stops, key = { it.pickupId ?: (it.number + it.name) }) { stop ->
                        StopRow(stop) { onNavigateStop(stop.pickupId) }
                    }
                    item { StartNavigationButton(onStartNavigation) }
                }
            }
        }
    }
}

@Composable
private fun OptimizedBadge(text: String) {
    StatusChip(text, MakiColors.Rider)
}

@Composable
private fun MiniMap(markers: List<RouteMarkerUi>, path: List<RoutePlanner.LatLng>, here: RoutePlanner.LatLng? = null) {
    val first = markers.first()
    Box(Modifier.fillMaxWidth().height(MakiSpace.xl * 11).clip(RoundedCornerShape(MakiRadius.badge))) {
        OsmMap(
            center = GeoPoint(first.lat, first.lng),
            modifier = Modifier.matchParentSize(),
            zoom = 15.0,
            // The route starts at the rider: without "Tú" the first leg starts nowhere.
            markers = markers.map { OsmMarker(GeoPoint(it.lat, it.lng), it.label, MakiColors.Gen.toArgb()) } +
                listOfNotNull(here?.let { OsmMarker(GeoPoint(it.lat, it.lng), "Tú", current = true) }),
            // Without a road response, pins remain visible but no road is invented.
            route = path.map { GeoPoint(it.lat, it.lng) },
        )
    }
}

@Composable
private fun StopRow(stop: RouteStopUi, onNavigateStop: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.md), verticalAlignment = Alignment.CenterVertically) {
        Rail(stop)
        Box(Modifier.weight(1f)) {
            if (stop.endpoint != null) EndpointCard(stop) else StopCard(stop, onNavigateStop)
        }
    }
}

@Composable
private fun Rail(stop: RouteStopUi) {
    val (bg, icon) = when (stop.endpoint) {
        "start" -> MakiColors.Success to Icons.Filled.TripOrigin
        "end" -> MakiColors.Text2 to Icons.Filled.Flag
        else -> MakiColors.Rider to null
    }
    Box(
        Modifier.size(MakiSpace.lg * 2).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = MakiColors.OnDark, modifier = Modifier.size(MakiSpace.lg))
        else Text(stop.number, color = MakiColors.OnDark, style = MakiType.rowTitle)
    }
}

@Composable
private fun EndpointCard(stop: RouteStopUi) {
    Column(verticalArrangement = Arrangement.spacedBy(MakiSpace.xs / 2)) {
        Text(stop.name, color = MakiColors.Text, style = MakiType.cardTitle)
        Text(stop.subtitle, color = MakiColors.Text2, style = MakiType.bodySmall)
    }
}

@Composable
private fun StopCard(stop: RouteStopUi, onNavigateStop: () -> Unit) {
    Row(
        Modifier.alpha(if (stop.done) 0.6f else 1f).makiCard().fillMaxWidth().height(IntrinsicSize.Min),
    ) {
        if (stop.highlighted) Box(Modifier.width(MakiSpace.xs).fillMaxHeight().background(MakiColors.Rider))
        Column(
            Modifier.weight(1f).padding(MakiSpace.lg).semantics(mergeDescendants = true) {
                contentDescription = "Parada ${stop.number}, ${stop.name}, ${stop.subtitle}" +
                    (stop.eta?.let { ", a $it" } ?: "") + (stop.material?.let { ", $it" } ?: "") +
                    (if (stop.done) ", completada" else "")
            },
            verticalArrangement = Arrangement.spacedBy(MakiSpace.sm),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stop.name, color = if (stop.highlighted) MakiColors.Rider else MakiColors.Text, style = MakiType.cardTitle)
                if (stop.eta != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Schedule, null, tint = MakiColors.Rider, modifier = Modifier.size(MakiSpace.md))
                            Text(stop.eta, color = MakiColors.Rider, style = MakiType.label)
                        }
                        stop.arrival?.let {
                            Text(it, color = MakiColors.Text2, style = MakiType.caption)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, null, tint = MakiColors.Text2, modifier = Modifier.size(MakiSpace.md))
                Text(stop.subtitle, color = MakiColors.Text2, style = MakiType.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                stop.material?.let { MaterialChip(it, stop.highlighted) }
                stop.streak?.let { StreakTag(it, stop.highlighted) }
            }
            if (stop.badge != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = MakiColors.Money, modifier = Modifier.size(MakiSpace.md))
                    Text(stop.badge, color = MakiColors.Text2, style = MakiType.label)
                }
            }
            if (stop.done) {
                StatusChip("Parada completada", MakiColors.Success)
            } else {
                NavigateButton(onClick = onNavigateStop)
            }
        }
    }
}

@Composable
private fun MaterialChip(label: String, highlighted: Boolean) {
    StatusChip(label, if (highlighted) MakiColors.Rider else MakiColors.Gen)
}

@Composable
private fun StreakTag(label: String, highlighted: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.xs), verticalAlignment = Alignment.CenterVertically) {
        if (highlighted) Icon(Icons.Filled.LocalFireDepartment, null, tint = MakiColors.Streak, modifier = Modifier.size(MakiSpace.md))
        else Icon(Icons.Filled.Star, null, tint = MakiColors.Money, modifier = Modifier.size(MakiSpace.md))
        Text(label, color = MakiColors.Text2, style = MakiType.label)
    }
}

@Composable
private fun NavigateButton(onClick: () -> Unit) {
    NavRow(Icons.Filled.Navigation, "Navegar",
        accent = MakiColors.Rider, onClick = onClick)
}

@Composable
private fun ResumenCard(state: RiderRouteUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.stack)) {
        MetricTile(Icons.Filled.Route, state.totalDistance, "Distancia", MakiColors.Rider, Modifier.weight(1f))
        MetricTile(Icons.Filled.AccountBalanceWallet, state.estimatedEarnings, "Ganancia estimada",
            MakiColors.Rider, Modifier.weight(1f))
    }
}

@Composable
private fun StartNavigationButton(onClick: () -> Unit) {
    PrimaryButton("Iniciar Navegación", Modifier.fillMaxWidth(), icon = Icons.Filled.Navigation,
        color = MakiColors.Rider, onClick = onClick)
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun RiderRoutePreview() {
    MAKITheme {
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
