package pe.ruma.app.ui.screens.generator

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import pe.ruma.app.ui.components.rumaCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.HeroCard
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaLoading
import pe.ruma.app.ui.components.MetricTile
import pe.ruma.app.ui.components.OsmMap
import pe.ruma.app.ui.components.OsmMarker
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.SecondaryButton
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.components.StepDots
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.components.NavRow
import pe.ruma.app.data.RoutePlanner
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType
import org.osmdroid.util.GeoPoint

private val SampleTracking = TrackingUi(
    headerTitle = "Eco-Rider asignado",
    riderLine = "Carlos · Moto · ★ 4.8",
    etaText = "",
    itemsText = "8 PET + 2 latas",
    statusMessage = "",
    pickupId = "p1",
    canCancel = true,
    riderPhone = "+51987650010",
    status = "assigned",
    code = "PU-AD5395F1",
    windowText = "Hoy 3:00 - 6:00 pm",
    addressLine = "Jr. Las Flores 123",
    riderName = "Carlos",
    riderMeta = "Moto · ★ 4.8",
    items = listOf(TrackingItemUi("PET", "PET", 8, null), TrackingItemUi("Aluminio", "ALU", 2, null)),
    flowSteps = listOf("Pedido" to "2:41 p. m.", "Asignado" to null, "En camino" to null, "Llegando" to null, "Recogido" to null),
    flowCurrent = 1,
    estPoints = 92,
)

/** What the inbox gets from trg_pickup_notify. Push with the app closed doesn't exist yet: promise only the inbox. */
private val NotifyWhen = listOf("Un Eco-Rider tome tu recojo", "Salga hacia tu casa", "Recoja tus residuos")

/**
 * The family's pickup, in the shape its status calls for (Stitch "Seguimiento — en espera"
 * and "Detalle del recojo"):
 * - asked for or taken: a calm view with no map — what is happening, what comes next,
 *   and that the phone can be put down;
 * - rider on the way or at the door: the live map, re-read every few seconds by
 *   [pe.ruma.app.navigation.GeneratorRoot];
 * - completed, cancelled or failed: the detail, with where the points stand and when each step happened.
 * The family can call the rider or cancel before they reach the door — closing
 * the pickup is the rider's job, never the family's.
 */
@Composable
fun GeneratorTrackingScreen(
    state: TrackingUi? = null,
    busy: Boolean = false,
    onBack: () -> Unit = {},
    onCancel: () -> Unit = {},
    onNewPickup: () -> Unit = {},
    onOpenPickup: (String) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onWallet: () -> Unit = {},
) {
    // Sample only for @Preview: on a device a fake "Carlos" is worse than a spinner.
    val s = state ?: SampleTracking.takeIf { LocalInspectionMode.current }
    val title = when {
        s == null -> "Tu recojo"
        s.closed -> "Detalle del recojo"
        s.waiting -> "Tu recojo"
        else -> s.headerTitle
    }
    Column(Modifier.fillMaxSize().rumaScreen().statusBarsPadding()) {
        DetailHeader(title, onBack, trailing = s?.takeIf { it.pickupId != null }?.let {
            {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                    HeaderChip(it)
                    // Every pickup, the newest first, one tap away.
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(RumaColors.SurfaceGlass)
                            .clickable(onClickLabel = "Ver mis recojos", role = Role.Button, onClick = onOpenHistory),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.LocalShipping, "Mis recojos", tint = RumaColors.Gen, modifier = Modifier.size(22.dp)) }
                }
            }
        })
        when {
            s == null -> pe.ruma.app.ui.components.RumaSkeleton(listOf(180.dp, 120.dp, 96.dp, 140.dp), Modifier.padding(horizontal = RumaSpace.gutter), "Buscando tu recojo…")
            s.pickupId == null && state != null -> RumaEmpty(
                icon = Icons.Filled.LocalShipping,
                title = "Aún no tienes recojos",
                hint = s.statusMessage,
                accent = RumaColors.Gen,
                actionLabel = "Registrar residuos",
                onAction = onNewPickup,
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = RumaSpace.gutter, end = RumaSpace.gutter,
                    top = RumaSpace.xs, bottom = RumaSpace.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
            ) {
                when {
                    s.closed -> detailItems(s, onNewPickup, onWallet)
                    s.waiting -> waitingItems(s, busy, onCancel)
                    else -> liveItems(s, busy, onCancel)
                }
            }
        }
    }
}

@Composable
private fun HeaderChip(s: TrackingUi) {
    when {
        s.status == "completed" -> StatusChip("Completado", RumaColors.Success)
        s.closed -> StatusChip(if (s.status == "cancelled") "Cancelado" else "No se pudo", RumaColors.Text2)
        s.waiting -> StatusChip(if (s.riderName != null && s.status == "assigned") "Asignado" else "En espera", RumaColors.Money)
        else -> StatusChip("En vivo", RumaColors.Money)
    }
}

/** The one hero of each view: a line of context, a big line, and an optional soft line. */
@Composable
private fun TrackingHero(overline: String, title: String, support: String?, content: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth()
            .rumaHero(RumaGradient.forAccent(RumaColors.Gen), glow = RumaGradient.glowOf(RumaColors.Gen))
            .padding(RumaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        Text(overline, color = RumaGradient.softOn(RumaColors.Gen), style = RumaType.overline)
        Text(title, color = Color.White, style = RumaType.metricLarge)
        if (!support.isNullOrBlank()) Text(support, color = RumaGradient.softOn(RumaColors.Gen), style = RumaType.bodySmall)
        content()
    }
}

// ---- Asked for / taken: the calm view ----

private fun LazyListScope.waitingItems(s: TrackingUi, busy: Boolean, onCancel: () -> Unit) {
    val name = s.riderName.takeIf { s.status == "assigned" }
    // Keyed by the rider: the moment someone accepts, the scene is new and the arrival plays.
    item(key = "dispatch-${name ?: "searching"}") {
        DispatchScene(name, at = if (s.homeLat != null && s.homeLng != null) RoutePlanner.LatLng(s.homeLat, s.homeLng) else null)
    }
    item {
        TrackingHero(
            overline = "VENTANA DE RECOJO",
            title = s.windowText.ifBlank { "Por confirmar" },
            support = listOf(s.code, s.addressLine).filter { it.isNotBlank() }.joinToString(" · "),
        )
    }
    item { SoftCard(Modifier.fillMaxWidth()) { StepDots(s.flowSteps, s.flowCurrent) } }
    item {
        SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.lg) {
            NowNext(
                Icons.AutoMirrored.Filled.DirectionsBike.takeIf { name != null } ?: Icons.Filled.Place,
                RumaColors.Gen, "QUÉ PASA AHORA",
                if (name != null) "$name tomó tu recojo." else "Buscamos al Eco-Rider más cercano.",
                strong = true,
            )
            NowNext(
                Icons.Filled.Notifications, RumaColors.Money, "QUÉ SIGUE",
                if (name != null) "Saldrá hacia ti dentro de tu ventana. Cuando esté en camino verás su ubicación aquí."
                else "Cuando uno lo tome verás su nombre aquí y en tus notificaciones.",
            )
        }
    }
    if (name != null) item { RiderCard(s, call = true) }
    item {
        SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.md) {
            Text("Te avisamos cuando", style = RumaType.rowTitle, color = RumaColors.Text)
            NotifyWhen.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = RumaColors.Gen, modifier = Modifier.size(RumaSpace.xl))
                    Text(line, style = RumaType.body, color = RumaColors.Text)
                }
            }
        }
    }
    item {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(RumaRadius.card)).background(RumaColors.GenTintStrong)
                .padding(RumaSpace.lg),
            horizontalArrangement = Arrangement.spacedBy(RumaSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(RumaSpace.xxl).clip(CircleShape).background(RumaColors.Gen), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = RumaColors.OnDark, modifier = Modifier.size(RumaSpace.lg))
            }
            Text("Puedes cerrar la app. Cada cambio queda en tus notificaciones.", style = RumaType.body, color = RumaColors.Text)
        }
    }
    item {
        SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.md) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lo que entregarás", Modifier.weight(1f), style = RumaType.rowTitle, color = RumaColors.Text)
                if (s.estPoints > 0) StatusChip("~${groupInt(s.estPoints)} pts por confirmar", RumaColors.Money)
            }
            s.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RumaSpace.md)) {
                    Icon(Icons.Filled.Inventory2, null, tint = RumaColors.Text2, modifier = Modifier.size(RumaSpace.xl))
                    Text(item.name, Modifier.weight(1f), style = RumaType.body, color = RumaColors.Text)
                    Text("x${item.reported}", style = RumaType.rowTitle, color = RumaColors.Text)
                }
            }
        }
    }
    if (s.canCancel) item { CancelButton(busy, onCancel) }
    item {
        Text(
            "Puedes cancelarlo mientras el Eco-Rider no haya llegado.",
            Modifier.fillMaxWidth(), style = RumaType.caption, color = RumaColors.Text2, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NowNext(icon: ImageVector, tint: Color, label: String, text: String, strong: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.md)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.xs)) {
            Text(label, style = RumaType.overline, color = if (strong) RumaColors.Gen else RumaColors.Text2)
            Text(text, style = if (strong) RumaType.rowTitle else RumaType.body, color = RumaColors.Text)
        }
    }
}

@Composable
private fun RiderCard(s: TrackingUi, call: Boolean) {
    val context = LocalContext.current
    SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.md) {
        Text("TU ECO-RIDER", style = RumaType.overline, color = RumaColors.Text2)
        Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(RumaColors.Gen), contentAlignment = Alignment.Center) {
                Text(s.riderName.orEmpty().take(1).uppercase(), style = RumaType.rowTitle, color = RumaColors.OnDark)
            }
            Column(Modifier.weight(1f)) {
                Text(s.riderName.orEmpty(), style = RumaType.rowTitle, color = RumaColors.Text)
                if (s.riderMeta.isNotBlank()) Text(s.riderMeta, style = RumaType.caption, color = RumaColors.Text2)
            }
        }
        val phone = s.riderPhone
        if (call && phone != null) {
            SecondaryButton("Llamar al Eco-Rider", Modifier.fillMaxWidth(), icon = Icons.Filled.Phone, onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
            })
        }
    }
}

@Composable
private fun CancelButton(busy: Boolean, onCancel: () -> Unit) {
    if (busy) {
        PrimaryButton(
            "Cancelando…", Modifier.fillMaxWidth(),
            icon = Icons.Filled.Close, enabled = false, color = RumaColors.Error,
        )
    } else {
        SecondaryButton(
            "Cancelar recojo", Modifier.fillMaxWidth(),
            icon = Icons.Filled.Close, color = RumaColors.Error,
            onClick = { if (!busy) onCancel() },
        )
    }
}

// ---- On the way / at the door: the live map ----

private fun LazyListScope.liveItems(s: TrackingUi, busy: Boolean, onCancel: () -> Unit) {
    item {
        HeroCard(
            accent = RumaColors.Gen,
            overline = "TU RECOJO",
            metric = s.etaText.ifBlank { "—" },
            support = s.statusMessage,
        ) {
            if (s.riderLine.isNotBlank()) {
                Text(s.riderLine, color = RumaColors.OnDark, style = RumaType.bodySmall)
            }
        }
    }
    item { SoftCard(Modifier.fillMaxWidth()) { StepDots(s.flowSteps, s.flowCurrent) } }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.stack)) {
            val enVuelo = s.distanceText != null
            MetricTile(
                Icons.Filled.Navigation,
                s.distanceText ?: s.etaText.ifBlank { "—" },
                if (enVuelo) "Distancia / tiempo" else "Estado del recojo",
                RumaColors.Rider, Modifier.weight(1f),
            )
            MetricTile(
                Icons.Filled.Inventory2, s.itemsText, "Ítems a recoger",
                RumaColors.Gen, Modifier.weight(1f),
            )
        }
    }
    item { SectionLabel("Seguimiento") }
    item {
        Box(
            Modifier.fillMaxWidth().height(RumaSpace.xxl * 10)
                .clip(RoundedCornerShape(RumaRadius.card)).background(RumaColors.SurfaceSoft),
        ) {
            if (s.hasMap) {
                val home = GeoPoint(s.homeLat!!, s.homeLng!!)
                val rider = if (s.riderLat != null && s.riderLng != null) GeoPoint(s.riderLat, s.riderLng) else null
                OsmMap(
                    center = home,
                    zoom = 16.0,
                    markers = listOfNotNull(
                        // The pickup point is wherever the phone was when the family asked.
                        OsmMarker(home, "Punto de recojo", RumaColors.Gen.toArgb()),
                        rider?.let { OsmMarker(it, s.riderLine, RumaColors.Rider.toArgb()) },
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
                    verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
                ) {
                    Icon(Icons.Filled.Place, null, tint = RumaColors.Gen, modifier = Modifier.size(RumaSpace.lg * 2))
                    Text(
                        "Tu dirección no tiene ubicación en el mapa",
                        color = RumaColors.Text2, style = RumaType.bodySmall,
                    )
                }
            }
        }
    }
    val chip = listOfNotNull(s.distanceText, s.etaText.ifBlank { null }, s.staleText).joinToString(" · ")
    if (chip.isNotBlank()) {
        item {
            StatusChip(chip, if (s.staleText == null) RumaColors.Success else RumaColors.Streak)
        }
    }
    s.riderPhone?.let { phone ->
        item {
            val context = LocalContext.current
            PrimaryButton("Llamar al Eco-Rider", Modifier.fillMaxWidth(), icon = Icons.Filled.Phone, onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
            })
        }
    }
    if (s.canCancel) item { CancelButton(busy, onCancel) }
}

// ---- Completed / cancelled / failed: the detail ----

private fun LazyListScope.detailItems(s: TrackingUi, onNewPickup: () -> Unit, onWallet: () -> Unit) {
    val done = s.status == "completed"
    val credited = s.pointsFinal != null
    item {
        val overline = listOf(s.code, s.dateText).filter { it.isNotBlank() }.joinToString(" · ").uppercase()
        if (done) {
            TrackingHero(
                overline = overline,
                title = "${groupInt(s.pointsFinal ?: s.pointsAwarded)} pts",
                support = if (credited) "Acreditados · ya puedes canjearlos"
                else "Por confirmar · se acreditan cuando el acopio pese tu material",
            ) {
                s.pointsFinal?.let {
                    Text(
                        "= S/%.2f".format(java.util.Locale.US, it / POINTS_PER_SOL),
                        color = RumaColors.OnDark, style = RumaType.chip,
                    )
                }
            }
        } else {
            TrackingHero(
                overline = overline,
                title = if (s.status == "cancelled") "Recojo cancelado" else "No se pudo recoger",
                support = s.cancelReason,
            )
        }
    }
    if (done) item {
        SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.md) {
            Text("EVOLUCIÓN DE TUS PUNTOS", style = RumaType.overline, color = RumaColors.Text2)
            StepDots(
                listOf(
                    "Recogido" to s.pointsAwarded.takeIf { it > 0 }?.let { "${groupInt(it)} por confirmar" },
                    "Pesado" to "en el acopio",
                    "Acreditado" to s.pointsFinal?.let { "${groupInt(it)} pts" },
                ),
                current = if (credited) 3 else 1,
            )
            Text(
                when {
                    !credited -> "Se acreditan cuando el acopio pese el material del lote."
                    s.pointsAwarded > 0 && s.pointsFinal != s.pointsAwarded -> "El acopio pesó tu material y ajustó los puntos."
                    else -> "El acopio pesó tu material y confirmó los puntos."
                },
                style = RumaType.caption, color = RumaColors.Text2,
            )
        }
    }
    item {
        SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.md) {
            Text(if (done) "Lo que entregaste" else "Lo que ibas a entregar", style = RumaType.rowTitle, color = RumaColors.Text)
            QtyRow("Material", "Declaraste", if (done) "Confirmado" else null, header = true)
            s.items.forEach { QtyRow(it.name, "${it.reported}", if (done) it.confirmed?.toString() ?: "—" else null) }
            if (done) Text(
                "Lo confirmado lo cuenta el Eco-Rider en tu puerta y lo ajusta el peso en el acopio. Tus puntos salen de ahí, no de lo declarado.",
                style = RumaType.caption, color = RumaColors.Text2,
            )
        }
    }
    if (s.riderName != null) item { RiderCard(s, call = false) }
    if (s.timeline.isNotEmpty()) item {
        SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.sm) {
            Text("Línea de tiempo", style = RumaType.rowTitle, color = RumaColors.Text)
            Timeline(s.timeline)
        }
    }
    // A closed pickup always ends on the next step: the next scan, and the money once it is in.
    item { PrimaryButton("Escanear de nuevo", Modifier.fillMaxWidth(), icon = Icons.Filled.PhotoCamera, onClick = onNewPickup) }
    if (credited) item { SecondaryButton("Ver mi wallet", Modifier.fillMaxWidth(), onClick = onWallet) }
}

@Composable
private fun QtyRow(material: String, reported: String, confirmed: String?, header: Boolean = false) {
    val style = if (header) RumaType.caption else RumaType.body
    val color = if (header) RumaColors.Text2 else RumaColors.Text
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(material, Modifier.weight(1.4f), style = style, color = color)
        Text(reported, Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.End)
        if (confirmed != null) {
            Text(confirmed, Modifier.weight(1f), style = if (header) style else RumaType.rowTitle, color = color, textAlign = TextAlign.End)
        }
    }
}

/** Dots on a rail, one per step that happened; the last one is where the pickup ended. */
@Composable
private fun Timeline(steps: List<Pair<String, String>>) {
    Column(
        Modifier.fillMaxWidth().drawBehind {
            val x = 6.dp.toPx()   // centre of the 12 dp dots
            drawLine(RumaColors.GenTintStrong, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        },
    ) {
        steps.forEachIndexed { i, (label, time) ->
            val last = i == steps.lastIndex
            Row(
                Modifier.fillMaxWidth().padding(vertical = RumaSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(RumaSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(RumaColors.Gen))
                Text(label, Modifier.weight(1f), style = if (last) RumaType.rowTitle else RumaType.body, color = if (last) RumaColors.Gen else RumaColors.Text)
                Text(time, style = if (last) RumaType.rowTitle else RumaType.body, color = if (last) RumaColors.Gen else RumaColors.Text)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1400)
@Composable
private fun TrackingPreview() {
    RumaTheme { GeneratorTrackingScreen() }
}

