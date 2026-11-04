package com.example.maki.ui.screens.rider

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.maki.data.MakiLocation
import com.example.maki.data.RoutePlanner
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.BackButton
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.OsmMap
import com.example.maki.ui.components.OsmMarker
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SecondaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.StatusChip
import com.example.maki.ui.components.makiCard
import com.example.maki.ui.components.makiHero
import com.example.maki.ui.screens.generator.MakiTime
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiGradient
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint

// Immersive navigation screen for the stop the rider actually accepted.
data class RiderNavigationUiState(
    val stopName: String,
    val address: String,
    val distance: String,
    val eta: String,
    val instruction: String,
    val materials: List<String> = emptyList(),
    /** Destination coordinates; null when the pickup has none recorded yet. */
    val lat: Double? = null,
    val lng: Double? = null,
    /** Fastest street path from the rider to the door, when the router answered. */
    val path: List<RoutePlanner.LatLng> = emptyList(),
    val roadKm: Double? = null,
    val roadMinutes: Int? = null,
    /** The family's phone, to ring the bell from the street. */
    val phone: String? = null,
    /** "Parada 2 de 4" when the rider has a route; empty for a single stop. */
    val position: String = "",
    /** Where the stop stands: en camino / llegando / contando en la puerta. */
    val statusLabel: String = "En camino",
    /** At the door the ETA stops being the headline — the count does. */
    val atDoor: Boolean = false,
)

/**
 * Map on top, destination sheet below. The sheet used to float over the map,
 * and the map framed both pins across the full screen — so "Tú" ended up hidden
 * behind the card. The map now only gets the space nothing covers.
 */
@Composable
fun RiderNavigationScreen(
    state: RiderNavigationUiState? = null,
    busy: Boolean = false,
    onBack: () -> Unit = {},
    onArrived: () -> Unit = {},
    /** Every fresh fix: the VM re-routes and flags "llegando" near the door. */
    onPosition: (RoutePlanner.LatLng) -> Unit = {},
    onRelease: () -> Unit = {},
) {
    if (state == null) {
        // No accepted stop: say so instead of drawing a map to nowhere.
        Column(
            Modifier.fillMaxSize().makiScreen().statusBarsPadding(),
        ) {
            Box(Modifier.padding(MakiSpace.gutter)) { BackButton(onBack) }
            MakiEmpty(
                icon = Icons.Filled.Navigation,
                title = "No hay un recojo en curso",
                hint = "Acepta una solicitud desde el inicio para empezar a navegar.",
                accent = MakiColors.Rider,
            )
        }
        return
    }
    val destination = state.lat?.let { lat -> state.lng?.let { lng -> GeoPoint(lat, lng) } }
    val ctx = LocalContext.current
    var askRelease by remember { mutableStateOf(false) }

    // The rider's own position, refreshed while this screen is open — the map is
    // useless without "you are here", and the distance banner is a guess without it.
    var here by remember { mutableStateOf(MakiLocation.lastKnown(ctx, maxAgeMs = FIX_MAX_AGE_MS)) }
    val report by rememberUpdatedState(onPosition)
    LaunchedEffect(Unit) {
        while (true) {
            MakiLocation.current(ctx, maxAgeMs = FIX_MAX_AGE_MS)?.let {
                here = it
                report(it)
            }
            delay(HERE_REFRESH_MS)
        }
    }
    val herePoint = here?.let { GeoPoint(it.lat, it.lng) }

    // Road distance/time when the router answered; straight line as the fallback.
    val straightKm = if (here != null && state.lat != null && state.lng != null) {
        RoutePlanner.distanceKm(here!!, RoutePlanner.LatLng(state.lat, state.lng))
    } else null
    val km = state.roadKm ?: straightKm
    val distanceLabel = km?.let { RoutePlanner.distanceLabel(it) } ?: state.distance
    val minutes = state.roadMinutes ?: straightKm?.let { RoutePlanner.etaMinutes(it) }
    val etaLabel = minutes?.let { "~$it min" } ?: state.eta
    // The headline a rider can act on: the hour they knock, not a countdown.
    val arrivalLabel = when {
        state.atDoor -> "Ya estás en el punto"
        minutes != null -> "Llegas ${MakiTime.clockPlus(minutes)}"
        else -> state.statusLabel
    }

    Column(Modifier.fillMaxSize().makiScreen()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (destination != null) {
                OsmMap(
                    center = herePoint ?: destination,
                    modifier = Modifier.matchParentSize(),
                    zoom = 17.0,
                    markers = listOfNotNull(
                        OsmMarker(destination, state.stopName, MakiColors.Gen.toArgb()),
                        herePoint?.let { OsmMarker(it, "Tú", MakiColors.Rider.toArgb()) },
                    ),
                    route = state.path.map { GeoPoint(it.lat, it.lng) }
                        .ifEmpty { listOfNotNull(herePoint, destination).takeIf { it.size == 2 }.orEmpty() },
                )
            }
            // Back + distance / how to find the door, over the top of the map.
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(MakiSpace.gutter),
                horizontalArrangement = Arrangement.spacedBy(MakiSpace.md), verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onBack)
                Row(
                    Modifier.weight(1f).makiHero(MakiGradient.rider, glow = MakiGradient.glowOf(MakiColors.Rider)).padding(horizontal = MakiSpace.lg, vertical = MakiSpace.md),
                    horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (state.atDoor) Icons.Filled.Place else Icons.Filled.ArrowUpward,
                        null, tint = MakiColors.OnDark, modifier = Modifier.size(MakiSpace.xl),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            arrivalLabel,
                            color = MakiColors.OnDark, style = MakiType.sectionHeading,
                        )
                        Text(
                            listOf(
                                if (state.roadKm != null) "$distanceLabel por calles" else distanceLabel,
                                etaLabel,
                            ).filter { it.isNotBlank() && it != "—" }.joinToString(" · "),
                            color = MakiColors.OnRiderSoft, style = MakiType.label,
                        )
                    }
                }
            }
            // Where this stop sits in the day, pinned to the map's bottom edge.
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = MakiSpace.gutter, bottom = MakiSpace.lg),
                horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(state.statusLabel, MakiColors.Rider)
                if (state.position.isNotBlank()) StatusPill(state.position, MakiColors.Text2)
            }
        }

        // Destination sheet with the actions.
        Column(
            Modifier.fillMaxWidth().makiCard(MakiRadius.hero)
                .navigationBarsPadding().padding(horizontal = MakiSpace.gutter, vertical = MakiSpace.lg),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs / 2)) {
                    SectionLabel("DESTINO")
                    Text(state.stopName, color = MakiColors.Text, style = MakiType.sectionHeading)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, null, tint = MakiColors.Rider, modifier = Modifier.size(MakiSpace.lg))
                        Text(etaLabel, color = MakiColors.Rider, style = MakiType.cardTitle)
                    }
                    if (!state.atDoor) {
                        Text(arrivalLabel, color = MakiColors.Text2, style = MakiType.caption)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, null, tint = MakiColors.Text2, modifier = Modifier.size(MakiSpace.lg))
                Text(state.address, color = MakiColors.Text2, style = MakiType.bodySmall)
            }
            // The reference ("portón verde") is read at the door, not while riding.
            if (state.instruction.isNotBlank()) {
                Text(
                    state.instruction,
                    color = MakiColors.Text, style = MakiType.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm)) {
                state.materials.forEach { MaterialChip(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm)) {
                if (state.lat != null && state.lng != null) {
                    SecondaryButton(
                        "Guía por voz", Modifier.weight(1f), icon = Icons.Filled.Directions, color = MakiColors.Rider,
                        onClick = { openTurnByTurn(ctx, state.lat, state.lng, state.stopName) },
                    )
                }
                state.phone?.let { phone ->
                    SecondaryButton(
                        "Llamar", Modifier.weight(1f), icon = Icons.Filled.Phone, color = MakiColors.Rider,
                        onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } },
                    )
                }
            }
            PrimaryButton("He llegado al punto", Modifier.fillMaxWidth(), color = MakiColors.Rider, enabled = !busy, onClick = onArrived)
            Text(
                "No puedo ir · liberar este recojo",
                color = MakiColors.Text2, style = MakiType.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(MakiRadius.chip))
                    .alpha(if (busy) 0.45f else 1f)
                    .clickable(enabled = !busy, onClickLabel = "Liberar este recojo", role = Role.Button) { askRelease = true }
                    .heightIn(min = MakiSpace.lg * 3).padding(MakiSpace.sm),
            )
        }
    }

    if (askRelease) {
        AlertDialog(
            onDismissRequest = { askRelease = false },
            containerColor = MakiColors.Surface,
            title = { Text("Liberar recojo", style = MakiType.cardTitle, color = MakiColors.Text) },
            text = {
                Text(
                    "Volverá al tablero para que otro Eco-Rider lo tome y avisaremos a ${state.stopName}.",
                    style = MakiType.body, color = MakiColors.Text2,
                )
            },
            confirmButton = {
                TextButton(modifier = Modifier.semantics { contentDescription = "Liberar recojo" }, onClick = { askRelease = false; onRelease() }) {
                    Text("Liberar", style = MakiType.button, color = MakiColors.Error)
                }
            },
            dismissButton = {
                TextButton(modifier = Modifier.semantics { contentDescription = "Seguir con el recojo" }, onClick = { askRelease = false }) { Text("Seguir", style = MakiType.button, color = MakiColors.Text2) }
            },
        )
    }
}

/**
 * Hands the leg to the phone's navigation app for spoken turn-by-turn — the one
 * thing an in-app OSM map can't do well. Google Maps when present, any map app
 * that understands geo: otherwise.
 */
private fun openTurnByTurn(context: Context, lat: Double, lng: Double, label: String) {
    val googleNav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng"))
    val anyMap = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})"))
    runCatching { context.startActivity(googleNav) }
        .recoverCatching { context.startActivity(anyMap) }
}

/** How often the navigation screen re-reads where the rider is. */
private const val HERE_REFRESH_MS = 5_000L
/** A cached fix older than this is not "here" for someone riding a moto. */
private const val FIX_MAX_AGE_MS = 20_000L

/** Small state pill over the map: status first, then where this stop sits in the day. */
@Composable
private fun StatusPill(label: String, tint: Color) {
    Box(Modifier.makiCard(MakiRadius.chip)) {
        StatusChip(label, tint)
    }
}

@Composable
private fun MaterialChip(label: String) {
    StatusChip(label, MakiColors.Rider)
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun RiderNavigationPreview() {
    MAKITheme {
        RiderNavigationScreen(
            state = SampleNavigation,
        )
    }
}

private val SampleNavigation = RiderNavigationUiState(
    stopName = "Fam. Torres", address = "Calle Santa Rosa 215, Cartavio", distance = "1.2 km",
    eta = "5 min", instruction = "Frente al colegio, portón gris",
    materials = listOf("6 cartones", "4 vidrios"), phone = "+51987650003",
)
