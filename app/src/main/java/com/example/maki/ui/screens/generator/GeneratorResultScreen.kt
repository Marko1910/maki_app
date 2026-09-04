package com.example.maki.ui.screens.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.BackButton
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SecondaryButton
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

@Composable
fun GeneratorResultScreen(
    result: DetectionResultUi? = null,
    challenge: ChallengeUi? = null,
    /** Live count per material code; falls back to what the AI detected. */
    quantities: Map<String, Int> = emptyMap(),
    onAdjust: (code: String, delta: Int) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    onPedirRecojo: () -> Unit = {},
    onOtraFoto: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Box(Modifier.size(26.dp).clip(CircleShape).background(MakiColors.Success), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Text("Residuos Detectados", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val items = result?.items ?: SampleItems
            items.forEach { item ->
                DetectedItem(
                    icon = iconForCode(item.code),
                    name = item.name,
                    qty = quantities[item.code] ?: item.detectedQty,
                    detectedQty = item.detectedQty,
                    meta = item.meta,
                    note = item.note,
                    onPlus = { onAdjust(item.code, +1) },
                    onMinus = { onAdjust(item.code, -1) },
                )
            }
            PointsCard(points = result?.totalPoints ?: 92, streakText = result?.streakText ?: "Racha de 21 días")
            MarketValueCard(
                price = result?.marketPrice ?: "S/1.20/kg",
                pct = result?.marketPct ?: "+3%",
                note = result?.marketNote ?: "El PET subió 3% esta semana.",
            )
            challenge?.let { ChallengeCard(it) }
            // Two actions only: send it for collection, or shoot again.
            PrimaryButton("Pedir Recojo", Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.DirectionsBike, onClick = onPedirRecojo)
            SecondaryButton("Tomar otra foto", Modifier.fillMaxWidth(), icon = Icons.Outlined.PhotoCamera, onClick = onOtraFoto)
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
    onPlus: () -> Unit,
    onMinus: () -> Unit,
) {
    Row(
        Modifier.makiShadow(16.dp, 4.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface).fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, MakiColors.Gen, MakiColors.GenTint, boxSize = 48.dp, iconSize = 24.dp, radius = 14.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(meta, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            // Only surfaced once the count differs: it is the receipt for the edit.
            if (qty != detectedQty) {
                Text(
                    "La IA contó $detectedQty",
                    color = MakiColors.Money, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                )
            } else if (note != null) {
                Text(note, color = MakiColors.Success, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            QtyButton(Icons.Filled.Remove, filled = false, onClick = onMinus)
            Text(
                "$qty", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                textAlign = TextAlign.Center, modifier = Modifier.width(22.dp),
            )
            QtyButton(Icons.Filled.Add, filled = true, onClick = onPlus)
        }
    }
}

/** The +/- affordance on a detected material. */
@Composable
private fun QtyButton(icon: ImageVector, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape)
            .background(if (filled) MakiColors.Gen else MakiColors.Bg)
            .then(if (!filled) Modifier.border(1.dp, MakiColors.Border, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (filled) Color.White else MakiColors.Text, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun PointsCard(points: Int, streakText: String) {
    Column(
        Modifier.makiShadow(20.dp, 10.dp, Color(0x400F6E56)).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(MakiColors.Gen, MakiColors.GenDark))).fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(MakiColors.WhiteTint), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MonetizationOn, null, tint = Color(0xFFFFE7B0), modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("+$points Eco-Puntos", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = Color(0xFFFFD9A8), modifier = Modifier.size(14.dp))
                    Text(streakText, color = MakiColors.OnDarkSoft, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        Text("Se acreditarán al confirmar el recojo", color = Color(0xFFA9D4C5), fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    }
}

@Composable
private fun MarketValueCard(price: String, pct: String, note: String) {
    Column(
        Modifier.makiShadow(16.dp, 4.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface).fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.AccountBalanceWallet, MakiColors.Money, MakiColors.MoneyTint, boxSize = 44.dp, iconSize = 23.dp, radius = 14.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Precio de mercado", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(price, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(MakiColors.SuccessTint).padding(start = 7.dp, end = 9.dp, top = 5.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = MakiColors.Success, modifier = Modifier.size(14.dp))
                Text(pct, color = MakiColors.Success, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }
        Text(note, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    }
}

@Composable
private fun ChallengeCard(ch: ChallengeUi) {
    val accent = if (ch.completed) MakiColors.Success else MakiColors.Gen
    Column(
        Modifier.makiShadow(16.dp, 4.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface)
            .border(1.dp, MakiColors.GenTintStrong, RoundedCornerShape(16.dp)).fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Inventory2, null, tint = accent, modifier = Modifier.size(20.dp))
            Text(ch.title, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(ch.rewardText, color = accent, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MakiColors.Border)) {
            if (ch.progress > 0f) {
                Box(Modifier.fillMaxWidth(ch.progress).height(8.dp).clip(RoundedCornerShape(4.dp)).background(accent))
            }
        }
        Text(ch.progressText, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

private fun iconForCode(code: String): ImageVector = when (code) {
    "PET", "VID" -> Icons.Filled.LocalDrink
    else -> Icons.Filled.Inventory2   // ALU, CAR, PIL, …
}

/** Shown only in @Preview / before a real scan exists. */
private val SampleItems = listOf(
    DetectedItemUi("PET", "Botella PET", "x8", "Confianza 92% · Calidad 0.9", "“Limpias y compactadas”", detectedQty = 8),
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
