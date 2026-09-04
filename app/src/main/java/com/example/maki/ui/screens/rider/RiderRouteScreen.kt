package com.example.maki.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.OsmMap
import com.example.maki.ui.components.OsmMarker
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont
import org.osmdroid.util.GeoPoint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MakiLoading

// Surface tints for this screen, derived from the rider accent so they stay in
// step with the palette instead of being one-off hex values.
private val OptimizedBg = MakiColors.Rider.copy(alpha = 0.05f)
private val NavBtnSoft = MakiColors.Rider.copy(alpha = 0.08f)
private val ResumenBg = MakiColors.Rider.copy(alpha = 0.05f)
private val MaterialGenTint = MakiColors.GenTint

/** A map pin for a stop; absent when the pickup has no coordinates yet. */
data class RouteMarkerUi(val lat: Double, val lng: Double, val label: String)

data class RouteStopUi(
    val number: String,             // "1".."N"; blank for endpoints
    val endpoint: String? = null,   // "start" | "end" | null (normal stop)
    val name: String,
    val subtitle: String,           // address, or endpoint subtitle
    val eta: String? = null,
    val material: String? = null,
    val streak: String? = null,
    val badge: String? = null,
    val highlighted: Boolean = false,
    /** Already collected or skipped: shown dimmed, and not navigable. */
    val done: Boolean = false,
    val marker: RouteMarkerUi? = null,
)

data class RiderRouteUiState(
    val title: String = "Ruta del Día",
    val optimizedNote: String = "",
    val stops: List<RouteStopUi> = emptyList(),
    val totalDistance: String = "—",
    val totalTime: String = "—",
    val estimatedEarnings: String = "",
    val markers: List<RouteMarkerUi> = emptyList(),
)

/**
 * The rider's stops for today: the operator's planned route when one exists,
 * otherwise the pickups this rider has already claimed. The map plots the real
 * pickup coordinates — it used to show four hard-coded points in SJL.
 */
@Composable
fun RiderRouteScreen(
    state: RiderRouteUiState? = null,
    error: String? = null,
    onBack: () -> Unit = {},
    onNavigateStop: () -> Unit = {},
    onStartNavigation: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()) {
        DetailHeader(title = state?.title ?: "Ruta del Día", onBack = onBack)

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                error != null -> item { MakiError(error, onRetry) }
                state == null -> item { MakiLoading("Cargando tu ruta…") }
                state.stops.isEmpty() -> item {
                    MakiEmpty(
                        icon = Icons.Filled.Route,
                        title = "Sin paradas por ahora",
                        hint = "Acepta un recojo desde el inicio y aparecerá aquí, en orden de ruta.",
                    )
                }
                else -> {
                    if (state.optimizedNote.isNotBlank()) item { OptimizedBadge(state.optimizedNote) }
                    if (state.markers.isNotEmpty()) item { MiniMap(state.markers) }
                    items(state.stops, key = { it.number + it.name }) { StopRow(it, onNavigateStop) }
                    item { ResumenCard(state) }
                    item { StartNavigationButton(onStartNavigation) }
                }
            }
        }
    }
}

@Composable
private fun OptimizedBadge(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(OptimizedBg).padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = MakiColors.Rider, modifier = Modifier.size(16.dp))
        Text(text, color = MakiColors.Rider, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun MiniMap(markers: List<RouteMarkerUi>) {
    val first = markers.first()
    Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp))) {
        OsmMap(
            center = GeoPoint(first.lat, first.lng),
            modifier = Modifier.matchParentSize(),
            zoom = 15.0,
            markers = markers.map { OsmMarker(GeoPoint(it.lat, it.lng), it.label) },
            // Straight legs in visiting order: the ordering is optimised, the
            // street geometry is not (that needs a road router — see RoutePlanner).
            route = markers.map { GeoPoint(it.lat, it.lng) },
        )
    }
}

@Composable
private fun StopRow(stop: RouteStopUi, onNavigateStop: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Modifier.size(32.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
        else Text(stop.number, color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun EndpointCard(stop: RouteStopUi) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stop.name, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text(stop.subtitle, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
private fun StopCard(stop: RouteStopUi, onNavigateStop: () -> Unit) {
    val cardBg = if (stop.highlighted) OptimizedBg else MakiColors.Surface
    Row(
        Modifier.makiShadow(14.dp, 4.dp, Color(0x0A1A1A1A)).clip(RoundedCornerShape(14.dp)).background(cardBg).fillMaxWidth().height(IntrinsicSize.Min),
    ) {
        if (stop.highlighted) Box(Modifier.width(4.dp).fillMaxHeight().background(MakiColors.Rider))
        Column(
            Modifier.weight(1f).padding(14.dp).semantics(mergeDescendants = true) {
                contentDescription = "Parada ${stop.number}, ${stop.name}, ${stop.subtitle}" +
                    (stop.eta?.let { ", a $it" } ?: "") + (stop.material?.let { ", $it" } ?: "") +
                    (if (stop.done) ", completada" else "")
            },
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stop.name, color = if (stop.highlighted) MakiColors.Rider else MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                if (stop.eta != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, null, tint = MakiColors.Text2, modifier = Modifier.size(13.dp))
                        Text(stop.eta, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, null, tint = MakiColors.Text2, modifier = Modifier.size(13.dp))
                Text(stop.subtitle, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                stop.material?.let { MaterialChip(it, stop.highlighted) }
                stop.streak?.let { StreakTag(it, stop.highlighted) }
            }
            if (stop.badge != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = MakiColors.Money, modifier = Modifier.size(13.dp))
                    Text(stop.badge, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
            if (stop.done) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MakiColors.Success, modifier = Modifier.size(14.dp))
                    Text("Parada completada", color = MakiColors.Success, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            } else {
                NavigateButton(highlighted = stop.highlighted, onClick = onNavigateStop)
            }
        }
    }
}

@Composable
private fun MaterialChip(label: String, highlighted: Boolean) {
    val tint = if (highlighted) MakiColors.Rider else MakiColors.Gen
    val bg = if (highlighted) MakiColors.RiderTint else MaterialGenTint
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(bg).padding(start = 8.dp, top = 4.dp, end = 9.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Inventory2, null, tint = tint, modifier = Modifier.size(12.dp))
        Text(label, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun StreakTag(label: String, highlighted: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (highlighted) Icon(Icons.Filled.LocalFireDepartment, null, tint = MakiColors.Streak, modifier = Modifier.size(13.dp))
        else Icon(Icons.Filled.Star, null, tint = MakiColors.Money, modifier = Modifier.size(13.dp))
        Text(label, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun NavigateButton(highlighted: Boolean, onClick: () -> Unit) {
    val bg = if (highlighted) MakiColors.Rider else NavBtnSoft
    val fg = if (highlighted) Color.White else MakiColors.Rider
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Navigation, null, tint = fg, modifier = Modifier.size(14.dp))
        Text("Navegar", color = fg, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun ResumenCard(state: RiderRouteUiState) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ResumenBg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryStat(Icons.Filled.Route, state.totalDistance)
            SummaryStat(Icons.Filled.Schedule, state.totalTime)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AccountBalanceWallet, null, tint = MakiColors.Money, modifier = Modifier.size(15.dp))
            Text(state.estimatedEarnings, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SummaryStat(icon: ImageVector, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MakiColors.Rider, modifier = Modifier.size(15.dp))
        Text(label, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun StartNavigationButton(onClick: () -> Unit) {
    Row(
        Modifier.makiShadow(14.dp, 6.dp, Color(0x59185FA5)).clip(RoundedCornerShape(14.dp)).background(MakiColors.Rider)
            .fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Navigation, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text("Iniciar Navegación", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun RiderRoutePreview() {
    MAKITheme {
        RiderRouteScreen(
            state = RiderRouteUiState(
                optimizedNote = "2 paradas asignadas",
                stops = listOf(
                    RouteStopUi("1", null, "Fam. Torres", "Jr. Las Flores 98", eta = "3 min", material = "3 cartones"),
                    RouteStopUi("2", null, "Fam. Nureña", "Jr. Las Flores 123", material = "8 PET", highlighted = true),
                ),
                totalDistance = "8.2 km total", totalTime = "2h 15m",
                estimatedEarnings = "S/12.50 estimado por esta ruta",
            ),
        )
    }
}
