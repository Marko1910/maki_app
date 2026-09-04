package com.example.maki.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.data.MakiLocation
import com.example.maki.data.RoutePlanner
import com.example.maki.ui.components.OsmMap
import com.example.maki.ui.components.OsmMarker
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint
import androidx.compose.material.icons.filled.Navigation
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.MakiEmpty

// Immersive turn-by-turn screen for the stop the rider actually accepted.
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
)

@Composable
fun RiderNavigationScreen(
    state: RiderNavigationUiState? = null,
    onBack: () -> Unit = {},
    onArrived: () -> Unit = {},
) {
    if (state == null) {
        // No accepted stop: say so instead of drawing a map to nowhere.
        Column(
            Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding(),
        ) {
            DetailHeader(title = "Navegación", onBack = onBack)
            MakiEmpty(
                icon = Icons.Filled.Navigation,
                title = "No hay un recojo en curso",
                hint = "Acepta una solicitud desde el inicio para empezar a navegar.",
            )
        }
        return
    }
    val destination = state.lat?.let { lat -> state.lng?.let { lng -> GeoPoint(lat, lng) } }

    // The rider's own position, refreshed while this screen is open — the map is
    // useless without "you are here", and the distance banner is a guess without it.
    val ctx = LocalContext.current
    var here by remember { mutableStateOf(MakiLocation.lastKnown(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            MakiLocation.current(ctx)?.let { here = it }
            delay(HERE_REFRESH_MS)
        }
    }
    val herePoint = here?.let { GeoPoint(it.lat, it.lng) }

    // Straight-line remaining distance; the stop order already came from the
    // route planner, this is just how far the current leg still is.
    val remainingKm = if (here != null && state.lat != null && state.lng != null) {
        RoutePlanner.distanceKm(here!!, RoutePlanner.LatLng(state.lat, state.lng))
    } else null
    val distanceLabel = remainingKm?.let { RoutePlanner.distanceLabel(it) } ?: state.distance
    val etaLabel = remainingKm?.let { "~${RoutePlanner.etaMinutes(it)} min" } ?: state.eta

    Box(Modifier.fillMaxSize().background(MakiColors.Bg)) {
        if (destination != null) {
            OsmMap(
                center = herePoint ?: destination,
                modifier = Modifier.matchParentSize(),
                zoom = if (herePoint != null) 16.0 else 17.0,
                markers = listOfNotNull(
                    OsmMarker(destination, state.stopName),
                    herePoint?.let { OsmMarker(it, "Tú") },
                ),
                route = listOfNotNull(herePoint, destination),
            )
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
            // Top: back + next-turn instruction banner.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.makiShadow(20.dp, 4.dp).size(40.dp).clip(CircleShape).background(MakiColors.Surface).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = MakiColors.Text, modifier = Modifier.size(20.dp)) }
                Row(
                    Modifier.weight(1f).makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(14.dp)).background(MakiColors.Rider).padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.ArrowUpward, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(distanceLabel, color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text(state.instruction, color = MakiColors.OnDarkSoft, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Bottom: destination card with the "arrived" action.
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().makiShadow(20.dp, 10.dp).clip(RoundedCornerShape(18.dp)).background(MakiColors.Surface).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("DESTINO", color = MakiColors.Rider, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(state.stopName, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, null, tint = MakiColors.Text2, modifier = Modifier.size(14.dp))
                    Text(state.address, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Schedule, null, tint = MakiColors.Text2, modifier = Modifier.size(14.dp))
                    Text(etaLabel, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.materials.forEach { MaterialChip(it) }
                }
                PrimaryButton("He llegado al punto", Modifier.fillMaxWidth(), color = MakiColors.Rider, onClick = onArrived)
            }
        }
    }
}

/** How often the navigation screen re-reads where the rider is. */
private const val HERE_REFRESH_MS = 10_000L

@Composable
private fun MaterialChip(label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(MakiColors.RiderTint).padding(start = 9.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Inventory2, null, tint = MakiColors.Rider, modifier = Modifier.size(13.dp))
        Text(label, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun RiderNavigationPreview() {
    MAKITheme {
        RiderNavigationScreen(
            state = RiderNavigationUiState(
                stopName = "Fam. Torres", address = "Jr. Las Flores 98", distance = "200 m",
                eta = "2 min", instruction = "Dirígete al punto de recojo",
                materials = listOf("3 cartones"),
            ),
        )
    }
}
