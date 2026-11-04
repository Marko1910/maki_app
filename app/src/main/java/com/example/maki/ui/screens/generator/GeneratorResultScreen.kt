package com.example.maki.ui.screens.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.HeroCard
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MetricTile
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.ProgressTrack
import com.example.maki.ui.components.SecondaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.SoftCard
import com.example.maki.ui.components.StatusChip
import com.example.maki.ui.components.makiCard
import com.example.maki.ui.components.makiOutlined
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

@Composable
fun GeneratorResultScreen(
    result: DetectionResultUi? = null,
    challenge: ChallengeUi? = null,
    /** Live count per material code; falls back to what the AI detected. */
    quantities: Map<String, Int> = emptyMap(),
    onAdjust: (code: String, delta: Int) -> Unit = { _, _ -> },
    /** Live units per brand per material code; falls back to what the AI read. */
    brandQuantities: Map<String, Map<String, Int>> = emptyMap(),
    onAdjustBrand: (code: String, brand: String, delta: Int) -> Unit = { _, _, _ -> },
    onBack: () -> Unit = {},
    onPedirRecojo: () -> Unit = {},
    onOtraFoto: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().makiScreen().statusBarsPadding()) {
        DetailHeader(
            "Residuos Detectados", onBack,
            leadingIcon = Icons.Filled.Check, leadingIconTint = MakiColors.Success,
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = MakiSpace.gutter, end = MakiSpace.gutter,
                top = MakiSpace.xs, bottom = MakiSpace.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
        ) {
            item {
                PointsCard(
                    points = result?.totalPoints ?: 92,
                    value = result?.totalValue ?: "S/0.09",
                    streakText = result?.streakText ?: "Racha de 21 días",
                )
            }
            item { SectionLabel("Residuos detectados") }
            val detectedItems = result?.items ?: SampleItems
            if (detectedItems.isEmpty()) {
                item {
                    MakiEmpty(Icons.Filled.Inventory2, "No se detectaron residuos", accent = MakiColors.Gen)
                }
            }
            items(detectedItems) { item ->
                DetectedItem(
                    icon = iconForCode(item.code),
                    name = item.name,
                    qty = quantities[item.code] ?: item.detectedQty,
                    detectedQty = item.detectedQty,
                    meta = item.meta,
                    note = item.note,
                    brandRows = brandQuantities[item.code] ?: item.brandRows,
                    onAdjustBrand = { brand, delta -> onAdjustBrand(item.code, brand, delta) },
                    onPlus = { onAdjust(item.code, +1) },
                    onMinus = { onAdjust(item.code, -1) },
                )
            }
            item { SectionLabel("Cómo recibes tu pago") }
            item {
                PaymentFlowCard(points = result?.totalPoints ?: 92, value = result?.totalValue ?: "S/0.09")
            }
            item { SectionLabel("Precio de mercado") }
            item {
                MarketValueCard(
                    price = result?.marketPrice ?: "S/1.20/kg",
                    pct = result?.marketPct ?: "+3%",
                    note = result?.marketNote ?: "El PET subió 3% esta semana.",
                )
            }
            challenge?.let { ch ->
                item { SectionLabel("Reto") }
                item { ChallengeCard(ch) }
            }
            item {
                PrimaryButton("Pedir Recojo", Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.DirectionsBike, onClick = onPedirRecojo)
            }
            item {
                // A new, separate scan: adding to this one is "Otra foto" inside the camera.
                // Labelled "Tomar otra foto", it read as that and booked the same bottles twice.
                SecondaryButton("Nuevo escaneo", Modifier.fillMaxWidth(), icon = Icons.Outlined.PhotoCamera, onClick = onOtraFoto)
            }
        }
    }
}

@Composable
private fun DetectedItem(
    icon: ImageVector,
    name: String,
    qty: Int,
    detectedQty: Int,
    meta: String,
    note: String?,
    brandRows: Map<String, Int>,
    onAdjustBrand: (brand: String, delta: Int) -> Unit,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().makiCard().padding(MakiSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, MakiColors.Gen, MakiColors.GenTint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs)) {
                Text(name, color = MakiColors.Text, style = MakiType.rowTitle)
                Text(meta, color = MakiColors.Text2, style = MakiType.label)
                // Only surfaced once the count differs: it is the receipt for the edit.
                if (qty != detectedQty) {
                    Text("La IA contó $detectedQty", color = MakiColors.Money, style = MakiType.label)
                } else if (note != null) {
                    Text(note, color = MakiColors.Success, style = MakiType.label)
                }
            }
        }
        if (brandRows.isEmpty()) {
            Stepper(qty, name, Modifier.align(Alignment.End), onMinus, onPlus)
        } else {
            // One row per brand: the total is their sum, and the rider still checks it.
            Text("Total: $qty · corrige si la IA se equivocó", color = MakiColors.Text2, style = MakiType.label)
            brandRows.forEach { (brand, units) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(brand, color = MakiColors.Text, style = MakiType.body, modifier = Modifier.weight(1f))
                    Stepper(units, brand, Modifier, { onAdjustBrand(brand, -1) }, { onAdjustBrand(brand, +1) })
                }
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, name: String, modifier: Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QtyButton(Icons.Filled.Remove, filled = false, label = "Quitar una unidad de $name", onClick = onMinus)
        Text(
            "$value", color = MakiColors.Text, style = MakiType.metricMedium,
            textAlign = TextAlign.Center, modifier = Modifier.width(MakiSpace.xxl),
        )
        QtyButton(Icons.Filled.Add, filled = true, label = "Añadir una unidad de $name", onClick = onPlus)
    }
}

@Composable
private fun QtyButton(icon: ImageVector, filled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(MakiSpace.xl * 2 + MakiSpace.sm).clip(CircleShape)
            .background(if (filled) MakiColors.Gen else MakiColors.Bg)
            .then(if (!filled) Modifier.makiOutlined(MakiColors.Border, MakiRadius.pill) else Modifier)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (filled) MakiColors.OnDark else MakiColors.Text, modifier = Modifier.size(MakiSpace.lg)) }
}

@Composable
private fun PointsCard(points: Int, value: String, streakText: String) {
    HeroCard(
        accent = MakiColors.Gen,
        overline = "ECO-PUNTOS ESTIMADOS",
        metric = "+$points",
        support = "≈ $value · se confirman al pesar en el centro de acopio",
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.LocalFireDepartment, null, tint = MakiColors.OnMoneySoft, modifier = Modifier.size(MakiSpace.lg))
            Text(streakText, color = MakiColors.OnGenSoft, style = MakiType.label)
        }
    }
}

/**
 * The road from this scan to money, so the estimate on the hero isn't mistaken for a
 * payment: the rider checks at the door, the acopio weighs and pays by weight, and the
 * balance is redeemed whenever the family wants.
 */
@Composable
private fun PaymentFlowCard(points: Int, value: String) {
    SoftCard(gap = MakiSpace.md) {
        PayStep(Icons.Filled.Check, "Escaneo con IA", "Estimamos +$points pts (≈ $value).", done = true)
        PayStep(Icons.AutoMirrored.Filled.DirectionsBike, "El Eco-Rider revisa en tu puerta", "Confirma que lo que entregas coincide con lo reportado.")
        PayStep(Icons.Filled.Scale, "El centro de acopio lo pesa", "Junto con el de tu zona; tus puntos se ajustan al peso real y pasan a tu saldo.")
        PayStep(Icons.Filled.AccountBalanceWallet, "Canjea cuando quieras", "Por dinero (Yape o Plin, 1.000 pts = S/1.00) o como descuento en tus arbitrios.")
    }
}

@Composable
private fun PayStep(icon: ImageVector, title: String, body: String, done: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.md), verticalAlignment = Alignment.Top) {
        IconBadge(icon, if (done) MakiColors.Success else MakiColors.Text2, if (done) MakiColors.SuccessTint else MakiColors.SurfaceSoft)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = MakiColors.Text, style = MakiType.rowTitle, modifier = Modifier.weight(1f, fill = false))
                if (done) StatusChip("Hecho", MakiColors.Success)
            }
            Text(body, color = MakiColors.Text2, style = MakiType.label)
        }
    }
}

@Composable
private fun MarketValueCard(price: String, pct: String, note: String) {
    Column(verticalArrangement = Arrangement.spacedBy(MakiSpace.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.stack)) {
            MetricTile(Icons.Filled.AccountBalanceWallet, price, "Precio de mercado", MakiColors.Money, Modifier.weight(1f))
            MetricTile(Icons.AutoMirrored.Filled.TrendingUp, pct, "Esta semana", MakiColors.Success, Modifier.weight(1f))
        }
        Text(note, color = MakiColors.Text2, style = MakiType.bodySmall)
    }
}

@Composable
private fun ChallengeCard(ch: ChallengeUi) {
    val accent = if (ch.completed) MakiColors.Success else MakiColors.Gen
    Column(
        Modifier.makiCard()
            .makiOutlined(MakiColors.GenTintStrong).fillMaxWidth().padding(MakiSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Inventory2, null, tint = accent, modifier = Modifier.size(MakiSpace.xl))
            Text(ch.title, color = MakiColors.Text, style = MakiType.rowTitle, modifier = Modifier.weight(1f))
            Text(ch.rewardText, color = accent, style = MakiType.label)
        }
        StatusChip(if (ch.completed) "Completado" else "En progreso", accent)
        ProgressTrack(ch.progress, track = MakiColors.Border, fill = accent)
        Text(ch.progressText, color = MakiColors.Text2, style = MakiType.label)
    }
}

private fun iconForCode(code: String): ImageVector = when (code) {
    "PET", "VID" -> Icons.Filled.LocalDrink
    else -> Icons.Filled.Inventory2   // ALU, CAR, PIL, …
}

/** Shown only in @Preview / before a real scan exists. */
private val SampleItems = listOf(
    DetectedItemUi("PET", "Botella PET", "x8", "Confianza 92% · Calidad 0.9", "“Limpias y compactadas”", detectedQty = 8, brandRows = mapOf("Coca-Cola" to 5, "Inca Kola" to 3)),
    DetectedItemUi("ALU", "Lata de Aluminio", "x2", "Confianza 87%", null, detectedQty = 2),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun ResultPreview() {
    MAKITheme {
        GeneratorResultScreen(
            challenge = ChallengeUi("Reto: La Gran Aplastada", "+100 pts", 0.53f, "8 / 15 botellas · +100 pts al completar", false),
        )
    }
}
