package pe.ruma.app.ui.screens.generator

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaSpace
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pe.ruma.app.data.RoutePlanner
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.OsmMap
import pe.ruma.app.ui.components.OsmMarker
import pe.ruma.app.ui.components.SecondaryButton
import pe.ruma.app.ui.components.SoftCard
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import pe.ruma.app.ui.components.rumaCard
import pe.ruma.app.ui.components.GlassChip
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.theme.RumaTheme
import androidx.compose.material.icons.filled.Storefront
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaLoading
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaType
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
    // Tapping a card flies the map to that center and marks it; the rest step back.
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = withCoords.firstOrNull { it.name == picked }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // The chosen one is drawn last so its pin sits on top, and joins the map even
    // when it lies beyond the nearby radius.
    val shown = (nearby.filter { it != chosen } + listOfNotNull(chosen))

    Column(Modifier.fillMaxSize().rumaScreen().statusBarsPadding()) {
        DetailHeader("Puntos de Acopio", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(scroll)
                .padding(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            Box(
                Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(RumaRadius.card))
                    .background(RumaColors.SurfaceSoft).border(1.dp, RumaColors.Hairline, RoundedCornerShape(RumaRadius.card)),
            ) {
                OsmMap(
                    center = mapCenter,
                    modifier = Modifier.fillMaxSize(),
                    zoom = 14.0,
                    markers = shown.map {
                        val tint = if (chosen == null || it == chosen) RumaColors.Gen else RumaColors.Text2
                        OsmMarker(GeoPoint(it.lat!!, it.lng!!), it.name, tint.toArgb())
                    },
                    focus = chosen?.let { GeoPoint(it.lat!!, it.lng!!) },
                )
            }
            if (prices.isNotEmpty()) PricesCard(prices)
            when {
                centers == null -> RumaLoading("Cargando puntos de acopio…", accent = RumaColors.Gen)
                centers.isEmpty() -> RumaEmpty(
                    icon = Icons.Filled.Storefront,
                    title = "Aún no hay acopios cerca",
                    hint = "Cuando se registre un centro en tu zona aparecerá aquí con sus precios.",
                    accent = RumaColors.Gen,
                )
                else -> centers.forEach { c ->
                    CenterCard(
                        c,
                        selected = c.name == picked,
                        onSelect = {
                            if (c.lat == null || c.lng == null) onInfo("Este centro no tiene ubicación en el mapa.")
                            else { picked = c.name; scope.launch { scroll.animateScrollTo(0) } }
                        },
                        onRoute = { openInMaps(context, c, onInfo) },
                        onCall = { dial(context, c.phone, onInfo) },
                    )
                }
            }
        }
    }
}

/**
 * Today's market price per kilo and the Eco-Puntos it pays, as the PWA's grid of
 * small glass cells. Separating a bag is a decision about money: this is the number
 * that makes it one.
 */
@Composable
private fun PricesCard(rows: List<PriceRowUi>) {
    SoftCard(gap = RumaSpace.md) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Precios de hoy", color = RumaColors.Text, style = RumaType.sectionHeading, modifier = Modifier.weight(1f))
            Text("1,000 pts = S/1.00", color = RumaColors.Text2, style = RumaType.caption)
        }
        // Rows of two, not FlowRow: FlowRow crashes on device with the current BOM.
        rows.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                pair.forEach { PriceCell(it, Modifier.weight(1f)) }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        Text(
            "Los puntos se acreditan cuando el Eco-Rider recoge y el acopio pesa el material.",
            color = RumaColors.Text2, style = RumaType.bodySmall,
        )
    }
}

@Composable
private fun PriceCell(r: PriceRowUi, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().rumaCard(RumaRadius.card).padding(RumaSpace.md),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.xs / 2),
    ) {
        Text(r.name, color = RumaColors.Text2, style = RumaType.bodySmall)
        Text(r.pricePerKg, color = RumaColors.Money, style = RumaType.rowTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(r.pointsPerKg, color = RumaColors.Text2, style = RumaType.caption)
            r.changePct?.let { Text(it, color = if (r.rising) RumaColors.Success else RumaColors.Error, style = RumaType.caption) }
        }
    }
}

/** Beyond this a center is another town's, not "cerca". */
private const val NEARBY_KM = 25.0

@Composable
private fun CenterCard(c: CenterUi, selected: Boolean, onSelect: () -> Unit, onRoute: () -> Unit, onCall: () -> Unit) {
    SoftCard(
        Modifier
            .then(if (selected) Modifier.border(RumaSpace.xs / 2, RumaColors.Gen, RoundedCornerShape(RumaRadius.card)) else Modifier)
            .semantics { this.selected = selected }
            .clip(RoundedCornerShape(RumaRadius.card))
            .clickable(onClickLabel = "Ver en el mapa", role = Role.Button, onClick = onSelect),
        gap = RumaSpace.sm,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, color = RumaColors.Text, style = RumaType.rowTitle, modifier = Modifier.weight(1f))
            if (c.open) StatusChip("Abierto", RumaColors.Gen) else GlassChip("Cerrado", color = RumaColors.Text2)
        }
        val where = listOfNotNull(c.address.takeIf { it.isNotBlank() }, c.km?.let { "a ${RoutePlanner.distanceLabel(it)}" })
        if (where.isNotEmpty()) Text(where.joinToString(" · "), color = RumaColors.Text2, style = RumaType.bodySmall)
        if (c.materials.isNotBlank()) Text(c.materials, color = RumaColors.Text, style = RumaType.bodySmall)
        Row(Modifier.fillMaxWidth().padding(top = RumaSpace.xs), horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
            PrimaryButton("Cómo llegar", Modifier.weight(1f), enabled = c.lat != null && c.lng != null, onClick = onRoute)
            SecondaryButton("Llamar", Modifier.weight(1f), enabled = !c.phone.isNullOrBlank(), onClick = onCall)
        }
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
    RumaTheme {
        GeneratorCentersScreen(
            centers = listOf(
                CenterUi("Centro de Acopio Cartavio", "Av. Industrial 600, Cartavio", true, "PET, Aluminio, Cartón", -7.89, -79.22, null, 0.9),
                CenterUi("Centro de Acopio SJL Norte", "Av. Próceres 800, SJL", true, "PET, Vidrio", -12.0, -77.0, "01-555-0100", 510.0),
            ),
        )
    }
}
