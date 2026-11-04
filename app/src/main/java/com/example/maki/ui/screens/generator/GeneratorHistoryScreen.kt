package com.example.maki.ui.screens.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.components.makiCard
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MakiLoading
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiType

private val SampleHistory = HistoryUi(
    listOf(
        HistorySectionUi("HOY", listOf(
            HistoryEntryUi(false, "PET", "Recojo · 8 PET + 2 latas", "Hoy 3:00 - 6:00 pm · Carlos", "En camino · toca para seguirlo", "assigned", "+92 pts al recoger", null, pickupId = "p1"),
            HistoryEntryUi(false, "PET", "8 PET + 2 latas detectados", "+92 pts · S/0.34", "Recojo pedido", null, null, null),
        )),
        HistorySectionUi("AYER", listOf(
            HistoryEntryUi(false, "CAR", "Recojo · 3 cartones", "Mañana 9:00 - 12:00 m · Carlos", "Recojo completado", "done", "+45 pts por confirmar en el acopio", null, pickupId = "p2"),
        )),
        HistorySectionUi("CANJES", listOf(
            HistoryEntryUi(true, "redeem", "Canje Yape S/5.00", "10 Jun", null, null, null, "−5,000 pts"),
        )),
    ),
)

/** 0 = Todo, 1 = Recojos, 2 = Detecciones, 3 = Canjes. */
private val Filters = listOf("Todo", "Recojos", "Detecciones", "Canjes")

/**
 * Everything the family did, newest first — scans, pickups (tap an active one to
 * see the rider on the map) and redemptions.
 */
@Composable
fun GeneratorHistoryScreen(
    state: HistoryUi? = null,
    onBack: () -> Unit = {},
    onOpenPickup: (String) -> Unit = {},
) {
    val s = state ?: SampleHistory.takeIf { LocalInspectionMode.current }
    var sel by rememberSaveable { mutableIntStateOf(0) }
    val sections = s?.sections.orEmpty()
        .map { it.label to it.entries.filter { e -> e.matchesFilter(sel) } }
        .filter { it.second.isNotEmpty() }

    Column(Modifier.fillMaxSize().makiScreen().statusBarsPadding()) {
        DetailHeader("Mi Historial", onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { FilterChips(sel) { sel = it } }
            when {
                s == null -> item { MakiLoading("Cargando tu historial…") }
                sections.isEmpty() -> item {
                    MakiEmpty(
                        icon = Icons.Filled.History,
                        title = "Nada por aquí todavía",
                        hint = if (sel == 1) "Cuando pidas un recojo lo verás aquí, con su estado."
                        else "Escanea tus residuos con la cámara para empezar.",
                        accent = MakiColors.Gen,
                    )
                }
                else -> sections.forEach { (label, entries) ->
                    item(key = "day-$label") { DayLabel(label) }
                    items(entries) { e ->
                        if (e.isRedeem) {
                            RedeemEntry(e.title, e.meta, e.amount ?: "")
                        } else {
                            val open = e.pickupId?.takeIf { e.statusKind == "assigned" }
                            HistoryEntry(e, onClick = open?.let { id -> { onOpenPickup(id) } })
                        }
                    }
                }
            }
        }
    }
}

private fun HistoryEntryUi.matchesFilter(sel: Int): Boolean = when (sel) {
    1 -> pickupId != null
    2 -> !isRedeem && pickupId == null
    3 -> isRedeem
    else -> true
}

@Composable
private fun FilterChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Filters.forEachIndexed { i, f ->
            val on = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (on) MakiColors.Gen else MakiColors.Surface)
                    .then(if (!on) Modifier.border(1.dp, MakiColors.Border, RoundedCornerShape(20.dp)) else Modifier)
                    .clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    f,
                    color = if (on) MakiColors.OnDark else MakiColors.Text2,
                    style = if (on) MakiType.chip else MakiType.label,
                )
            }
        }
    }
}

private fun entryIcon(e: HistoryEntryUi): ImageVector = when {
    e.pickupId != null -> Icons.Filled.LocalShipping
    e.code == "CAR" -> Icons.Filled.Inventory2
    e.code == "VID" -> Icons.Filled.WineBar
    else -> Icons.Outlined.PhotoCamera
}

@Composable
private fun DayLabel(text: String) {
    Text(text, color = MakiColors.Text2, style = MakiType.chip)
}

@Composable
private fun HistoryEntry(e: HistoryEntryUi, onClick: (() -> Unit)?) {
    val (statusIcon, statusColor) = when (e.statusKind) {
        "assigned" -> Icons.AutoMirrored.Filled.DirectionsBike to MakiColors.Rider
        "cancelled" -> Icons.Filled.Cancel to MakiColors.Text2
        "done" -> Icons.Filled.CheckCircle to MakiColors.Success
        else -> Icons.Filled.CheckCircle to MakiColors.Gen
    }
    Row(
        Modifier.makiCard(MakiRadius.badge).fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        IconBadge(entryIcon(e), MakiColors.Gen, MakiColors.GenTint, boxSize = 42.dp, iconSize = 21.dp, radius = 12.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(e.title, color = MakiColors.Text, style = MakiType.rowTitle)
            // Scans lead with the points they earned; pickups with when and who.
            Text(e.meta, color = if (e.pickupId == null) MakiColors.Success else MakiColors.Text2,
                style = MakiType.chip)
            if (e.statusText != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(14.dp))
                    Text(e.statusText, color = statusColor, style = MakiType.label)
                }
            }
            if (e.note != null) Text(e.note, color = MakiColors.Text2, style = MakiType.caption)
        }
        if (onClick != null) Icon(Icons.Filled.ChevronRight, "Ver en el mapa", tint = MakiColors.Rider, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RedeemEntry(title: String, date: String, amount: String) {
    Row(
        Modifier.makiCard(MakiRadius.badge).fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Filled.Smartphone, MakiColors.Money, MakiColors.MoneyTint, boxSize = 42.dp, iconSize = 21.dp, radius = 12.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = MakiColors.Text, style = MakiType.rowTitle)
            Text(date, color = MakiColors.Text2, style = MakiType.caption)
        }
        Text(amount, color = MakiColors.Text2, style = MakiType.cardTitle)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HistoryPreview() {
    MAKITheme { GeneratorHistoryScreen() }
}
