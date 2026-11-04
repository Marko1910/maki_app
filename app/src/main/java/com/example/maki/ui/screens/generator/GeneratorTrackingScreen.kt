package com.example.maki.ui.screens.generator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.HeroCard
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MakiLoading
import com.example.maki.ui.components.MetricTile
import com.example.maki.ui.components.OsmMap
import com.example.maki.ui.components.OsmMarker
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SecondaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.StatusChip
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType
import org.osmdroid.util.GeoPoint

private val SampleTracking = TrackingUi(
    headerTitle = "Tu Eco-Rider está llegando",
    riderLine = "Carlos · Moto · ★ 4.8",
    etaText = "~5 min",
    itemsText = "8 PET + 2 latas",
    statusMessage = "Carlos va en camino. Míralo acercarse en el mapa.",
    distanceText = "A 900 m",
    canCancel = true,
    riderPhone = "+51987650010",
)

/**
 * Live view of the family's pickup: the house, the rider and the street path
 * between them, re-read every few seconds by [com.example.maki.navigation.GeneratorRoot].
 * The family can call the rider or cancel before they reach the door — closing
 * the pickup is the rider's job, never the family's.
 */
@Composable
fun GeneratorTrackingScreen(
    state: TrackingUi? = null,
    busy: Boolean = false,
    onBack: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    // Sample only for @Preview: on a device a fake "Carlos" is worse than a spinner.
    val s = state ?: SampleTracking.takeIf { LocalInspectionMode.current }
    Column(Modifier.fillMaxSize().makiScreen().statusBarsPadding()) {
        DetailHeader(s?.headerTitle ?: "Tu recojo", onBack)
        when {
            s == null -> MakiLoading("Buscando tu recojo…", accent = MakiColors.Gen)
            s.pickupId == null && state != null -> MakiEmpty(
                icon = Icons.Filled.LocalShipping,
                title = "Aún no tienes recojos",
                hint = s.statusMessage,
                accent = MakiColors.Gen,
            )
            else -> TrackingBody(s, busy, onCancel)
        }
    }
}

@Composable
private fun TrackingBody(s: TrackingUi, busy: Boolean, onCancel: () -> Unit) {
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = MakiSpace.gutter, end = MakiSpace.gutter,
            top = MakiSpace.xs, bottom = MakiSpace.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
    ) {
        item {
            HeroCard(
                accent = MakiColors.Gen,
                overline = "TU RECOJO",
                metric = s.etaText.ifBlank { "—" },
                support = s.statusMessage,
            ) {
                if (s.riderLine.isNotBlank()) {
                    Text(s.riderLine, color = MakiColors.OnDark, style = MakiType.bodySmall)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.stack)) {
                val enVuelo = s.distanceText != null
                MetricTile(
                    Icons.Filled.Navigation,
                    s.distanceText ?: s.etaText.ifBlank { "—" },
                    if (enVuelo) "Distancia / tiempo" else "Estado del recojo",
                    MakiColors.Rider, Modifier.weight(1f),
                )
                MetricTile(
                    Icons.Filled.Inventory2, s.itemsText, "Ítems a recoger",
                    MakiColors.Gen, Modifier.weight(1f),
                )
            }
        }
        item { SectionLabel("Seguimiento") }
        item {
            Box(
                Modifier.fillMaxWidth().height(MakiSpace.xxl * 10)
                    .clip(RoundedCornerShape(MakiRadius.card)).background(MakiColors.SurfaceSoft),
            ) {
                if (s.hasMap) {
                    val home = GeoPoint(s.homeLat!!, s.homeLng!!)
                    val rider = if (s.riderLat != null && s.riderLng != null) GeoPoint(s.riderLat, s.riderLng) else null
                    OsmMap(
                        center = home,
                        zoom = 16.0,
                        markers = listOfNotNull(
                            // The pickup point is wherever the phone was when the family asked.
                            OsmMarker(home, "Punto de recojo", MakiColors.Gen.toArgb()),
                            rider?.let { OsmMarker(it, s.riderLine, MakiColors.Rider.toArgb()) },
                        ),
                        // Street path when the router answered; a straight line otherwise.
                        route = s.path.map { GeoPoint(it.lat, it.lng) }
                            .ifEmpty { if (rider != null) listOf(rider, home) else emptyList() },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MakiSpace.sm),
                    ) {
                        Icon(Icons.Filled.Place, null, tint = MakiColors.Gen, modifier = Modifier.size(MakiSpace.lg * 2))
                        Text(
                            "Tu dirección no tiene ubicación en el mapa",
                            color = MakiColors.Text2, style = MakiType.bodySmall,
                        )
                    }
                }
            }
        }
        val chip = listOfNotNull(s.distanceText, s.etaText.ifBlank { null }, s.staleText).joinToString(" · ")
        if (chip.isNotBlank()) {
            item {
                StatusChip(chip, if (s.staleText == null) MakiColors.Success else MakiColors.Streak)
            }
        }
        s.riderPhone?.let { phone ->
            item {
                PrimaryButton("Llamar al Eco-Rider", Modifier.fillMaxWidth(), icon = Icons.Filled.Phone, onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
                })
            }
        }
        if (s.canCancel) {
            item {
                if (busy) {
                    PrimaryButton(
                        "Cancelando…", Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Close, enabled = false, color = MakiColors.Error,
                    )
                } else {
                    SecondaryButton(
                        "Cancelar recojo", Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Close, color = MakiColors.Error,
                        onClick = { if (!busy) onCancel() },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TrackingPreview() {
    MAKITheme { GeneratorTrackingScreen() }
}
