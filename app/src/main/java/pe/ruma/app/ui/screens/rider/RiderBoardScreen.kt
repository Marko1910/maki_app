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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.ruma.app.data.RoutePlanner
import pe.ruma.app.ui.components.GlassChip
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaSkeleton
import pe.ruma.app.ui.components.OsmMap
import pe.ruma.app.ui.components.OsmMarker
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.ScreenHeader
import pe.ruma.app.ui.components.SecondaryButton
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType
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

    // A tab, like the PWA's: no back button, the map first and the requests under it.
    Column(Modifier.fillMaxSize().rumaScreen(RumaColors.Rider).statusBarsPadding()) {
        ScreenHeader("Tablero de solicitudes")

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            when {
                error != null -> item { RumaError(error, onRetry, accent = RumaColors.Rider) }
                s == null -> item { RumaSkeleton(listOf(96.dp, 72.dp, 72.dp), label = "Buscando solicitudes abiertas…") }
                s.stops.isEmpty() -> item {
                    RumaEmpty(
                        icon = Icons.Filled.Inbox,
                        title = "El tablero está vacío",
                        hint = "Cuando alguien pida un recojo en tu zona aparecerá aquí y podrás tomarlo.",
                        accent = RumaColors.Rider,
                    )
                }
                else -> {
                    s.stops.mapNotNull { stop -> stop.lat?.let { lat -> stop.lng?.let { lng -> Triple(stop, lat, lng) } } }
                        .takeIf { it.isNotEmpty() }
                        ?.let { pins -> item { BoardMap(pins, s.here) } }
                    item { SectionLabel("Solicitudes abiertas") }
                    itemsIndexed(s.stops, key = { _, stop -> stop.pickupId }) { i, stop ->
                        BoardCard(stop, first = i == 0, busy = busy) { onClaim(stop.pickupId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardMap(pins: List<Triple<BoardStopUi, Double, Double>>, here: RoutePlanner.LatLng?) {
    val center = here?.let { GeoPoint(it.lat, it.lng) } ?: GeoPoint(pins.first().second, pins.first().third)
    Box(
        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(RumaRadius.card))
            .border(1.dp, RumaColors.Hairline, RoundedCornerShape(RumaRadius.card)),
    ) {
        OsmMap(
            center = center,
            modifier = Modifier.matchParentSize(),
            zoom = 14.0,
            // Requests step back in grey so "Tú" reads first, as on the PWA.
            markers = pins.map { (stop, lat, lng) -> OsmMarker(GeoPoint(lat, lng), stop.family, RumaColors.Text2.toArgb()) } +
                listOfNotNull(here?.let { OsmMarker(GeoPoint(it.lat, it.lng), "Tú", RumaColors.Rider.toArgb()) }),
        )
    }
}

/** One open request: who, where, when, what; the nearest one gets the filled button. */
@Composable
private fun BoardCard(stop: BoardStopUi, first: Boolean, busy: Boolean, onClaim: () -> Unit) {
    SoftCard(gap = RumaSpace.sm) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(stop.family, color = RumaColors.Text, style = RumaType.sectionHeading, modifier = Modifier.weight(1f))
            val distance = listOf(stop.distance, stop.eta).filter { it.isNotBlank() }.joinToString(" · ")
            if (distance.isNotBlank()) GlassChip(distance)
        }
        Text(stop.address, color = RumaColors.Text2, style = RumaType.body)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stop.window, color = RumaColors.Text2, style = RumaType.bodySmall, modifier = Modifier.weight(1f))
            if (stop.waiting.isNotBlank()) Text(stop.waiting, color = RumaColors.Text2, style = RumaType.bodySmall)
        }
        if (stop.items.isNotBlank()) Text(stop.items, color = RumaColors.Text, style = RumaType.bodySmall)
        if (first) {
            PrimaryButton("Tomar este recojo", Modifier.fillMaxWidth(), color = RumaColors.Rider, enabled = !busy, onClick = onClaim)
        } else {
            SecondaryButton("Tomar este recojo", Modifier.fillMaxWidth(), color = RumaColors.Rider, enabled = !busy, onClick = onClaim)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun RiderBoardPreview() {
    RumaTheme { RiderBoardScreen(state = SampleBoard) }
}
