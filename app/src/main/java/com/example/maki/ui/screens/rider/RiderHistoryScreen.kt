package com.example.maki.ui.screens.rider

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MakiLoading
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

enum class HistoryStatus { COMPLETED, PARTIAL, CANCELLED }

data class RiderHistoryEntryUi(
    val id: String,
    val name: String,
    val detail: String,
    val earnings: String,        // "+S/1.30" or "—"
    val time: String,
    val status: HistoryStatus,
    val statusLabel: String,
    val note: String? = null,
    val timestamp: String = "",  // raw ISO stamp; drives grouping + the date filter
)

data class RiderHistorySectionUi(val label: String, val entries: List<RiderHistoryEntryUi>)

data class RiderHistoryUiState(
    val filters: List<String> = listOf("Todos", "Hoy", "Semana", "Mes"),
    val sections: List<RiderHistorySectionUi> = emptyList(),
)

/**
 * Settled stops, newest first, grouped by day. The date pills actually filter
 * (they used to be decorative), and the list is lazy so a long history scrolls
 * without composing every card up front.
 */
@Composable
fun RiderHistoryScreen(
    state: RiderHistoryUiState? = null,
    error: String? = null,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    var selectedFilter by rememberSaveable { mutableIntStateOf(0) }
    val filters = state?.filters ?: RiderHistoryUiState().filters
    val sections = state?.let { filterSections(it.sections, selectedFilter) }

    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()) {
        DetailHeader(title = "Mi Historial", onBack = onBack)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEachIndexed { i, f ->
                FilterPill(f, selected = i == selectedFilter) { selectedFilter = i }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                error != null -> item { MakiError(error, onRetry) }
                sections == null -> item { MakiLoading("Cargando tu historial…") }
                sections.isEmpty() -> item {
                    MakiEmpty(
                        icon = Icons.Filled.Inbox,
                        title = if (selectedFilter == 0) "Todavía no has completado recojos"
                                else "Sin recojos en este periodo",
                        hint = if (selectedFilter == 0) "Acepta una solicitud desde el inicio y aparecerá aquí."
                               else "Prueba con otro filtro de fecha.",
                    )
                }
                else -> sections.forEach { section ->
                    item(key = "h-${section.label}") {
                        Text(
                            section.label,
                            color = MakiColors.Text2, fontFamily = MakiFont,
                            fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(section.entries, key = { it.id }) { HistoryCard(it) }
                }
            }
        }
    }
}

/** "Hoy" / "Semana" / "Mes" against the raw timestamps; index 0 keeps everything. */
private fun filterSections(
    sections: List<RiderHistorySectionUi>,
    filter: Int,
): List<RiderHistorySectionUi> {
    if (filter == 0) return sections
    val days = when (filter) { 1 -> 1; 2 -> 7; else -> 30 }
    val cutoff = System.currentTimeMillis() - days * 86_400_000L
    return sections.mapNotNull { section ->
        val kept = section.entries.filter { entry ->
            val ts = com.example.maki.ui.screens.generator.MakiTime.millis(entry.timestamp)
            ts == null || ts >= cutoff
        }
        if (kept.isEmpty()) null else section.copy(entries = kept)
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) MakiColors.Rider else MakiColors.Surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, MakiColors.Border, RoundedCornerShape(20.dp)))
            .clickable(onClick = onClick)
            // 44dp minimum touch target — the pill only *looks* 33dp tall.
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = if (selected) "Filtro $label, activo" else "Filtrar por $label" },
    ) {
        Text(
            label,
            color = if (selected) Color.White else MakiColors.Text2,
            fontFamily = MakiFont,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun HistoryCard(entry: RiderHistoryEntryUi) {
    val (icon, color) = statusVisuals(entry.status)
    Row(
        Modifier
            .makiShadow(14.dp, 4.dp, Color(0x0A1A1A1A))
            .clip(RoundedCornerShape(14.dp))
            .background(MakiColors.Surface)
            .fillMaxWidth()
            .padding(14.dp)
            // One announcement per card instead of six disconnected fragments.
            .clearAndSetSemantics {
                contentDescription = "${entry.name}, ${entry.statusLabel}. ${entry.detail}. " +
                    "${if (entry.earnings == "—") "sin ganancia" else entry.earnings}, ${entry.time}." +
                    (entry.note?.let { " Nota: $it" } ?: "")
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(entry.name, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                StatusBadge(entry.statusLabel, color)
            }
            Text(entry.detail, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.earnings,
                    color = if (entry.earnings == "—") MakiColors.Text2 else MakiColors.Success,
                    fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                )
                Text(entry.time, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
            entry.note?.let {
                Text(it, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.10f)).padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, color = color, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

private fun statusVisuals(status: HistoryStatus): Pair<ImageVector, Color> = when (status) {
    HistoryStatus.COMPLETED -> Icons.Filled.CheckCircle to MakiColors.Success
    HistoryStatus.PARTIAL -> Icons.Filled.Warning to MakiColors.Admin
    HistoryStatus.CANCELLED -> Icons.Filled.Cancel to MakiColors.Error
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1000)
@Composable
private fun RiderHistoryPreview() {
    MAKITheme {
        RiderHistoryScreen(
            state = RiderHistoryUiState(
                sections = listOf(
                    RiderHistorySectionUi(
                        "HOY",
                        listOf(
                            RiderHistoryEntryUi("1", "Fam. Torres", "3 cartones · Jr. Las Flores 98", "+S/0.60", "Hace 2h", HistoryStatus.COMPLETED, "Completado"),
                        ),
                    ),
                ),
            ),
        )
    }
}
