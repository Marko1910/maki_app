package pe.ruma.app.ui.screens.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import pe.ruma.app.ui.components.*
import pe.ruma.app.ui.theme.*

@Composable
fun GeneratorResultScreen(
    result: DetectionResultUi? = null,
    photo: ScanPhoto? = null,
    quantities: Map<String, Int> = emptyMap(),
    onAdjust: (code: String, delta: Int) -> Unit = { _, _ -> },
    brandQuantities: Map<String, Map<String, Int>> = emptyMap(),
    onAdjustBrand: (code: String, brand: String, delta: Int) -> Unit = { _, _, _ -> },
    onBack: () -> Unit = {},
    onPedirRecojo: () -> Unit = {},
    onOtraFoto: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().rumaScreen().statusBarsPadding()) {
        DetailHeader("Residuos detectados", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = RumaSpace.gutter, vertical = RumaSpace.sm),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            if (result == null) {
                item { RumaEmpty(Icons.Filled.Inventory2, "No hay un escaneo guardado", "Registra tus residuos para ver el resultado.", accent = RumaColors.Gen) }
                item { PrimaryButton("Registrar residuos", Modifier.fillMaxWidth(), onClick = onOtraFoto) }
            } else {
                item { PaySteps() }
                photo?.let { capture ->
                    item {
                        SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.md) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                                Icon(Icons.Outlined.PhotoCamera, null, tint = RumaColors.Gen)
                                Text("Tu foto", style = RumaType.rowTitle, color = RumaColors.Text)
                            }
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val ratio = capture.bitmap.width.toFloat() / capture.bitmap.height
                                val photoHeight = minOf(maxWidth / ratio, RumaSpace.xl * 24)
                                PhotoBoxes(capture, Modifier.fillMaxWidth().height(photoHeight))
                            }
                        }
                    }
                }
                item {
                    // The reward lands with a short pop: the first thing the scan gives back.
                    val pop = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0.92f) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy))
                    }
                    HeroCard(
                        modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                        accent = RumaColors.Gen,
                        overline = "ECO-PUNTOS ESTIMADOS",
                        metric = "+ ${groupInt(result.totalPoints)} pts",
                        support = "Se acreditan cuando el acopio pesa tu material.",
                    )
                }
                item { PointsRuleCard(result.items.sumOf { quantities[it.code] ?: it.detectedQty }) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                            Text("Lo que encontramos", Modifier.weight(1f), style = RumaType.cardTitle, color = RumaColors.Text)
                            val total = result.items.sumOf { quantities[it.code] ?: it.detectedQty }
                            StatusChip("$total ${if (total == 1) "ítem" else "ítems"}", RumaColors.Gen)
                        }
                        Text("Si contamos mal, corrige con + o −.", style = RumaType.bodySmall, color = RumaColors.Text2)
                    }
                }
                if (result.items.isEmpty()) item {
                    RumaEmpty(Icons.Filled.Inventory2, "No se detectaron residuos", accent = RumaColors.Gen)
                }
                items(result.items, key = { it.code }) { item ->
                    val brands = brandQuantities[item.code] ?: item.brandRows
                    val rows = brands.ifEmpty { mapOf(UNNAMED_BRAND to (quantities[item.code] ?: item.detectedQty)) }
                    Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.md)) {
                        rows.forEach { (brand, units) ->
                            FoundRow(
                                brand = if (brand == UNNAMED_BRAND) "Sin marca" else brand,
                                material = "${item.code} · ${materialKind(item.code, item.name)}",
                                units = units,
                                onAdjust = { delta ->
                                    if (brands.isEmpty()) onAdjust(item.code, delta)
                                    else onAdjustBrand(item.code, brand, delta)
                                },
                            )
                        }
                    }
                }
                item { SecondaryButton("Otra foto", Modifier.fillMaxWidth(), icon = Icons.Outlined.PhotoCamera, onClick = onOtraFoto) }
                item { Text("¿Tienes más? Empieza un escaneo nuevo; este ya quedó guardado.", Modifier.fillMaxWidth(), style = RumaType.bodySmall, color = RumaColors.Text2, textAlign = TextAlign.Center) }
            }
        }
        if (result != null) PrimaryButton("Pedir recojo", Modifier.fillMaxWidth().padding(RumaSpace.gutter), onClick = onPedirRecojo)
    }
}

private val paymentSteps = listOf(
    "Registraste" to "tus residuos",
    "El Eco-Rider" to "confirma en tu puerta",
    "El acopio" to "lo pesa",
    "Puntos a" to "tu saldo",
)

/** The bonus rule and the units so far against the next level. It states the rule; the database prices. */
@Composable
internal fun PointsRuleCard(units: Int) {
    val (next, label) = when {
        units < 30 -> 30 to "para +10 %"
        units < 80 -> 80 to "para +20 %"
        else -> 80 to "nivel máximo: +20 %"
    }
    SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.sm) {
        Text("Cada botella limpia = 10 pts · Junta 30 o más: +10 % · 80 o más: +20 %", style = RumaType.bodySmall, color = RumaColors.Text)
        ProgressTrack(units.toFloat() / next, track = RumaColors.Hair, fill = RumaColors.Gen)
        Text("$units de $next unidades · $label", style = RumaType.caption, color = RumaColors.Text2)
    }
}

@Composable
private fun PaySteps() {
    SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.md) {
        Text("Cómo recibes tu pago", style = RumaType.rowTitle, color = RumaColors.Gen)
        StepDots(paymentSteps, current = 1)
    }
}

@Composable
private fun FoundRow(brand: String, material: String, units: Int, onAdjust: (Int) -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.2f
    SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.sm) {
        if (largeText) {
            BrandLabel(brand, material)
            Stepper(units, "$brand, $material", Modifier.align(Alignment.End), onAdjust)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
            Column(Modifier.weight(1f)) { BrandLabel(brand, material) }
            Stepper(units, "$brand, $material", Modifier, onAdjust)
        }
    }
}

@Composable
private fun BrandLabel(brand: String, material: String) {
    Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.xs)) {
        Text(brand, style = RumaType.cardTitle, color = RumaColors.Text)
        StatusChip(material, RumaColors.Gen)
    }
}

@Composable
private fun Stepper(value: Int, name: String, modifier: Modifier, onAdjust: (Int) -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RumaSpace.xs)) {
        QtyButton(Icons.Filled.Remove, false, "Quitar una unidad de $name", value > 0) { onAdjust(-1) }
        Text("$value", Modifier.widthIn(min = RumaSpace.xxl), style = RumaType.rowTitle, color = RumaColors.Text, textAlign = TextAlign.Center)
        QtyButton(Icons.Filled.Add, true, "Añadir una unidad de $name", value < 999) { onAdjust(1) }
    }
}

@Composable
private fun QtyButton(icon: ImageVector, filled: Boolean, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(RumaSpace.xl * 2 + RumaSpace.sm).clip(CircleShape)
            .background(if (filled) RumaColors.Gen else RumaColors.SurfaceSoft)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (filled) RumaColors.OnDark else RumaColors.Text, modifier = Modifier.size(RumaSpace.lg)) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun ResultPreview() {
    RumaTheme {
        GeneratorResultScreen(result = DetectionResultUi(
            items = listOf(DetectedItemUi("PET", "Botella PET", "x2", "", null, 2, mapOf("Vocé" to 1, UNNAMED_BRAND to 1))),
            totalPoints = 20, totalValue = "", streakText = "",
        ))
    }
}
