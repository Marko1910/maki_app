package com.example.maki.ui.screens.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

private val SampleHistory = HistoryUi(
    listOf(
        HistorySectionUi("HOY", listOf(
            HistoryEntryUi(false, "PET", "8 PET + 2 latas detectados", "+92 pts · S/0.34", "Recojo asignado", "assigned", null, null),
        )),
        HistorySectionUi("AYER", listOf(
            HistoryEntryUi(false, "CAR", "3 cartones detectados", "+45 pts · S/0.12", "Recojo completado", "done", "Carlos recogió a las 10:30am", null),
        )),
        HistorySectionUi("LUNES", listOf(
            HistoryEntryUi(false, "VID", "5 vidrios detectados", "+60 pts · S/0.08", "Recojo completado", "done", null, null),
        )),
        HistorySectionUi("CANJES", listOf(
            HistoryEntryUi(true, "redeem", "Canje Yape S/5.00", "10 Jun", null, null, null, "−5,000 pts"),
        )),
    ),
)

@Composable
fun GeneratorHistoryScreen(state: HistoryUi? = null, onBack: () -> Unit = {}) {
    val s = state ?: SampleHistory
    val filters = listOf("Todo", "Detecciones", "Recojos", "Canjes")
    var sel by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()) {
        DetailHeader("Mi Historial", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEachIndexed { i, f ->
                    val on = i == sel
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (on) MakiColors.Gen else MakiColors.Surface)
                            .then(if (!on) Modifier.border(1.dp, MakiColors.Border, RoundedCornerShape(20.dp)) else Modifier)
                            .clickable { sel = i }.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(f, color = if (on) Color.White else MakiColors.Text2, fontFamily = MakiFont,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            s.sections.forEach { section ->
                val entries = section.entries.filter { it.matchesFilter(sel) }
                if (entries.isNotEmpty()) {
                    DayLabel(section.label)
                    entries.forEach { e ->
                        if (e.isRedeem) {
                            RedeemEntry(e.title, e.meta, e.amount ?: "")
                        } else {
                            DetectionEntry(
                                detectionIcon(e.code), e.title, e.meta,
                                e.statusKind, e.statusText, note = e.note,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Filter chips: 0=Todo, 1=Detecciones, 2=Recojos (has pickup status), 3=Canjes. */
private fun HistoryEntryUi.matchesFilter(sel: Int): Boolean = when (sel) {
    1 -> !isRedeem
    2 -> !isRedeem && statusKind != null
    3 -> isRedeem
    else -> true
}

private fun detectionIcon(code: String): ImageVector = when (code) {
    "CAR" -> Icons.Filled.Inventory2
    "VID" -> Icons.Filled.WineBar
    else -> Icons.Outlined.PhotoCamera
}

@Composable
private fun DayLabel(text: String) {
    Text(text, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
}

@Composable
private fun DetectionEntry(icon: ImageVector, title: String, meta: String, statusKind: String?, statusText: String?, note: String? = null) {
    val statusIcon = if (statusKind == "assigned") Icons.AutoMirrored.Filled.DirectionsBike else Icons.Filled.CheckCircle
    val statusColor = if (statusKind == "assigned") MakiColors.Rider else MakiColors.Success
    Row(
        Modifier.makiShadow(14.dp, 4.dp).clip(RoundedCornerShape(14.dp)).background(MakiColors.Surface).fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        IconBadge(icon, MakiColors.Gen, MakiColors.GenTint, boxSize = 42.dp, iconSize = 21.dp, radius = 12.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(meta, color = MakiColors.Success, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (statusText != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(14.dp))
                    Text(statusText, color = statusColor, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
            if (note != null) Text(note, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RedeemEntry(title: String, date: String, amount: String) {
    Row(
        Modifier.makiShadow(14.dp, 4.dp).clip(RoundedCornerShape(14.dp)).background(MakiColors.Surface).fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Filled.Smartphone, MakiColors.Money, Color(0x0FBA7517), boxSize = 42.dp, iconSize = 21.dp, radius = 12.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(date, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        }
        Text(amount, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HistoryPreview() {
    MAKITheme { GeneratorHistoryScreen() }
}
