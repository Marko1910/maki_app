package com.example.maki.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import com.example.maki.data.RoutePlanner
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.HeroCard
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MakiLoading
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

/** One open request anybody can take, with what it costs the rider to go. */
data class BoardStopUi(
    val pickupId: String,
    val family: String,
    val address: String,
    val items: String,
    val window: String,
    val distance: String,
    val eta: String,
    /** Straight-line km from the rider; null when the request has no coordinates. */
    val km: Double? = null,
    val waiting: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
)

data class RiderBoardUiState(
    val stops: List<BoardStopUi> = emptyList(),
    val here: RoutePlanner.LatLng? = null,
)

private val SampleBoard = RiderBoardUiState(
    stops = listOf(
        BoardStopUi("1", "Fam. Quispe", "Jr. Las Flores 240", "12 PET · 4 latas",
            "Hoy 3:00 - 6:00 pm", "a 800 m", "~4 min", 0.8, "Hace 10m", -8.143, -79.036),
        BoardStopUi("2", "Fam. Torres", "Av. Industrial 120", "5 cartones · 3 vidrios",
            "Hoy 3:00 - 6:00 pm", "a 2.1 km", "~9 min", 2.1, "Hace 1h", -8.156, -79.031),
    ),
)

/**
 * The open board: every request no rider has taken yet, nearest first.
 *
 * The dashboard only ever offered the oldest one, so a request two blocks away
 * stayed invisible behind one across town. Here the rider sees the whole board
 * and picks — distance and time window are the two things that decide it.
 */
@Composable
fun RiderBoardScreen(
    state: RiderBoardUiState? = null,
    busy: Boolean = false,
    error: String? = null,
    onBack: () -> Unit = {},
    onClaim: (String) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val s = state ?: SampleBoard.takeIf { androidx.compose.ui.platform.LocalInspectionMode.current }

    Column(Modifier.fillMaxSize().makiScreen(MakiColors.Rider).statusBarsPadding()) {
        DetailHeader(title = "Tablero de solicitudes", onBack = onBack)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = MakiSpace.gutter, end = MakiSpace.gutter, top = MakiSpace.xs, bottom = MakiSpace.lg * 1.5f),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
        ) {
            when {
                error != null -> item { MakiError(error, onRetry) }
                s == null -> item { MakiLoading("Buscando solicitudes abiertas…", accent = MakiColors.Rider) }
                s.stops.isEmpty() -> item {
                    MakiEmpty(
                        icon = Icons.Filled.Inbox,
                        title = "El tablero está vacío",
                        hint = "Cuando alguien pida un recojo en tu zona aparecerá aquí y podrás tomarlo.",
                        accent = MakiColors.Rider,
                    )
                }
                else -> {
                    item { Summary(s.stops.size) }
                    s.stops.mapNotNull { stop -> stop.lat?.let { lat -> stop.lng?.let { lng -> Triple(stop, lat, lng) } } }
                        .takeIf { it.isNotEmpty() }
                        ?.let { pins -> item { BoardMap(pins, s.here) } }
                    item { SectionLabel("Solicitudes abiertas") }
                    items(s.stops, key = { it.pickupId }) { stop ->
                        BoardCard(stop, busy) { onClaim(stop.pickupId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(count: Int) {
    HeroCard(
        accent = MakiColors.Rider,
        overline = "Tablero de solicitudes",
        metric = if (count == 1) "1 solicitud" else "$count solicitudes",
        support = if (count == 1) "1 solicitud esperando · la más cercana primero"
            else "$count solicitudes esperando · las más cercanas primero",
    )
}

@Composable
private fun BoardMap(pins: List<Triple<BoardStopUi, Double, Double>>, here: RoutePlanner.LatLng?) {
    val center = here?.let { GeoPoint(it.lat, it.lng) } ?: GeoPoint(pins.first().second, pins.first().third)
    Box(Modifier.fillMaxWidth().height(MakiSpace.xl * 10).clip(RoundedCornerShape(MakiRadius.card))) {
        OsmMap(
            center = center,
            modifier = Modifier.matchParentSize(),
            zoom = 14.0,
            markers = pins.map { (stop, lat, lng) -> OsmMarker(GeoPoint(lat, lng), stop.family, MakiColors.Gen.toArgb()) } +
                listOfNotNull(here?.let { OsmMarker(GeoPoint(it.lat, it.lng), "Tú", MakiColors.Rider.toArgb()) }),
        )
    }
}

@Composable
private fun BoardCard(stop: BoardStopUi, busy: Boolean, onClaim: () -> Unit) {
    Column(
        Modifier.makiCard()
            .fillMaxWidth().padding(MakiSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.sm),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs / 2)) {
                Text(stop.family, color = MakiColors.Text, style = MakiType.sectionHeading)
                Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, null, tint = MakiColors.Text2, modifier = Modifier.size(MakiSpace.md))
                    Text(stop.address, color = MakiColors.Text2, style = MakiType.bodySmall)
                }
            }
            DistanceBadge(stop)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            Chip(stop.items)
            if (stop.waiting.isNotBlank()) {
                Text(stop.waiting, color = MakiColors.Text2, style = MakiType.label)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, null, tint = MakiColors.Rider, modifier = Modifier.size(MakiSpace.md))
            Text(stop.window, color = MakiColors.Rider, style = MakiType.label)
        }
        PrimaryButton(
            "Tomar este recojo",
            Modifier.fillMaxWidth(),
            color = MakiColors.Rider,
            enabled = !busy,
            onClick = onClaim,
        )
    }
}

@Composable
private fun DistanceBadge(stop: BoardStopUi) {
    Column(
        Modifier.clip(RoundedCornerShape(MakiRadius.badge)).background(MakiColors.RiderTint).padding(horizontal = MakiSpace.md, vertical = MakiSpace.sm),
        horizontalAlignment = Alignment.End,
    ) {
        Text(stop.distance, color = MakiColors.Rider, style = MakiType.bodySmall)
        if (stop.eta.isNotBlank()) {
            Text(stop.eta, color = MakiColors.Rider, style = MakiType.caption)
        }
    }
}

@Composable
private fun Chip(label: String) {
    StatusChip(label, MakiColors.Rider)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun RiderBoardPreview() {
    MAKITheme { RiderBoardScreen(state = SampleBoard) }
}
