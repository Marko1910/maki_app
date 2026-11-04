package com.example.maki.ui.screens.generator

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.maki.data.RoutePlanner
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.OsmMap
import com.example.maki.ui.components.OsmMarker
import com.example.maki.ui.components.SecondaryButton
import com.example.maki.ui.components.SoftCard
import com.example.maki.ui.theme.MAKITheme
import androidx.compose.material.icons.filled.Storefront
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MakiLoading
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiType
import org.osmdroid.util.GeoPoint

/**
 * Collection centers nearest first (the list arrives sorted by distance from the
 * family's home) on an OSM map + a list with directions/call actions.
 */
@Composable
fun GeneratorCentersScreen(
    centers: List<CenterUi>? = null,
    /** Today's price per material; empty while it loads or when nothing is quoted. */
    prices: List<PriceRowUi> = emptyList(),
    onBack: () -> Unit = {},
    onInfo: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val withCoords = centers.orEmpty().filter { it.lat != null && it.lng != null }
    // Frame what is actually reachable: one center in another city would zoom the
    // map out to half of Peru and hide the one around the corner.
    val nearby = withCoords.filter { (it.km ?: 0.0) <= NEARBY_KM }.ifEmpty { withCoords.take(1) }
    val mapCenter = nearby.firstOrNull()?.let { GeoPoint(it.lat!!, it.lng!!) }
        ?: GeoPoint(-12.0464, -77.0428) // Lima

    Column(Modifier.fillMaxSize().makiScreen().statusBarsPadding()) {
        DetailHeader("Puntos de Acopio", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface)) {
                OsmMap(
                    center = mapCenter,
                    modifier = Modifier.fillMaxSize(),
                    zoom = 14.0,
                    markers = nearby.map { OsmMarker(GeoPoint(it.lat!!, it.lng!!), it.name, MakiColors.Gen.toArgb()) },
                )
            }
            if (prices.isNotEmpty()) PricesCard(prices)
            when {
                centers == null -> MakiLoading("Cargando puntos de acopio…", accent = MakiColors.Gen)
                centers.isEmpty() -> MakiEmpty(
                    icon = Icons.Filled.Storefront,
                    title = "Aún no hay acopios cerca",
                    hint = "Cuando se registre un centro en tu zona aparecerá aquí con sus precios.",
                    accent = MakiColors.Gen,
                )
                else -> centers.forEach { c ->
                    CenterCard(c, onRoute = { openInMaps(context, c, onInfo) }, onCall = { dial(context, c.phone, onInfo) })
                }
            }
        }
    }
}

/**
 * Today's market price per kilo and the Eco-Puntos it pays. Separating a bag is a
 * decision about money: this is the number that makes it one.
 */
@Composable
private fun PricesCard(rows: List<PriceRowUi>) {
    SoftCard(gap = 12.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Payments, null, tint = MakiColors.Money, modifier = Modifier.size(18.dp))
                Text("Precios de hoy", color = MakiColors.Text, style = MakiType.sectionHeading)
            }
            Text("1,000 pts = S/1.00", color = MakiColors.Text2, style = MakiType.chip)
        }
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(r.name, color = MakiColors.Text, style = MakiType.rowTitle)
                    Text(r.yours, color = MakiColors.Text2, style = MakiType.caption)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(r.pricePerKg, color = MakiColors.Text, style = MakiType.cardTitle)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        r.changePct?.let {
                            Text(
                                it,
                                color = if (r.rising) MakiColors.Success else MakiColors.Error,
                                style = MakiType.overline,
                            )
                        }
                        Text(r.pointsPerKg, color = MakiColors.Gen, style = MakiType.chip)
                    }
                }
            }
        }
        Text(
            "Los puntos se acreditan cuando el Eco-Rider recoge y el acopio pesa el material.",
            color = MakiColors.Text2, style = MakiType.caption,
        )
    }
}

/** Beyond this a center is another town's, not "cerca". */
private const val NEARBY_KM = 25.0

@Composable
private fun CenterCard(c: CenterUi, onRoute: () -> Unit, onCall: () -> Unit) {
    SoftCard(gap = 10.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, color = MakiColors.Text, style = MakiType.sectionHeading, modifier = Modifier.weight(1f))
            OpenBadge(c.open)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Place, null, tint = MakiColors.Text2, modifier = Modifier.size(15.dp))
            Text(c.address, color = MakiColors.Text2, style = MakiType.bodySmall, modifier = Modifier.weight(1f))
            c.km?.let {
                Text("a ${RoutePlanner.distanceLabel(it)}", color = MakiColors.Gen, style = MakiType.chip)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Recycling, null, tint = MakiColors.Gen, modifier = Modifier.size(15.dp))
            Text("Recibe: ${c.materials}", color = MakiColors.Text, style = MakiType.label)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cómo llegar", Modifier.weight(1f), icon = Icons.Filled.Directions, onClick = onRoute)
            SecondaryButton("Llamar", Modifier.weight(1f), icon = Icons.Filled.Phone, onClick = onCall)
        }
    }
}

@Composable
private fun OpenBadge(open: Boolean) {
    val bg = if (open) MakiColors.SuccessTint else MakiColors.Border
    val fg = if (open) MakiColors.Success else MakiColors.Text2
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp)) {
        Text(if (open) "Abierto" else "Cerrado", color = fg, style = MakiType.overline)
    }
}

private fun openInMaps(context: Context, c: CenterUi, onInfo: (String) -> Unit) {
    if (c.lat == null || c.lng == null) { onInfo("Este centro no tiene ubicación en el mapa."); return }
    val uri = Uri.parse("geo:${c.lat},${c.lng}?q=${c.lat},${c.lng}(${Uri.encode(c.name)})")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        .onFailure { onInfo("No hay una app de mapas instalada.") }
}

private fun dial(context: Context, phone: String?, onInfo: (String) -> Unit) {
    if (phone.isNullOrBlank()) { onInfo("Este centro no tiene teléfono registrado."); return }
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
        .onFailure { onInfo("No se pudo abrir el marcador.") }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun CentersPreview() {
    MAKITheme {
        GeneratorCentersScreen(
            centers = listOf(
                CenterUi("Centro de Acopio Cartavio", "Av. Industrial 600, Cartavio", true, "PET, Aluminio, Cartón", -7.89, -79.22, null, 0.9),
                CenterUi("Centro de Acopio SJL Norte", "Av. Próceres 800, SJL", true, "PET, Vidrio", -12.0, -77.0, "01-555-0100", 510.0),
            ),
        )
    }
}
