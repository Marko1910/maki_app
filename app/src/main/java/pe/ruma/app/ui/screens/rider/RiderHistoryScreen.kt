package pe.ruma.app.ui.screens.rider

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.FilterPill
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.GlassChip
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaSkeleton
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

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

    Column(Modifier.fillMaxSize().rumaScreen(RumaColors.Rider).statusBarsPadding()) {
        DetailHeader(title = "Mi Historial", onBack = onBack)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                    filters.forEachIndexed { i, f -> FilterPill(f, i == selectedFilter, RumaColors.Rider) { selectedFilter = i } }
                }
            }
            when {
                error != null -> item { RumaError(error, onRetry, accent = RumaColors.Rider) }
                sections == null -> item { RumaSkeleton(listOf(96.dp, 72.dp, 72.dp), label = "Cargando tu historial…") }
                sections.isEmpty() -> item {
                    RumaEmpty(
                        icon = Icons.Filled.History,
                        title = if (selectedFilter == 0) "Todavía no has completado recojos"
                                else "Sin recojos en este periodo",
                        hint = if (selectedFilter == 0) "Toma una solicitud del tablero y aparecerá aquí."
                               else "Prueba con otro filtro de fecha.",
                    )
                }
                else -> sections.forEach { section ->
                    item(key = "h-${section.label}") {
                        Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                            SectionLabel(section.label)
                            RumaList(section.entries) { HistoryRow(it) }
                        }
                    }
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
            val ts = pe.ruma.app.ui.screens.generator.RumaTime.millis(entry.timestamp)
            ts == null || ts >= cutoff
        }
        if (kept.isEmpty()) null else section.copy(entries = kept)
    }
}

/** One closed stop (PWA `RiderHistory` row): who, where, its state, and what it paid. */
@Composable
private fun HistoryRow(entry: RiderHistoryEntryUi) {
    RumaRow(
        entry.name,
        // One announcement per row instead of six disconnected fragments.
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "${entry.name}, ${entry.statusLabel}. ${entry.detail}. " +
                "${if (entry.earnings == "—") "sin ganancia" else entry.earnings}, ${entry.time}." +
                (entry.note?.let { " Nota: $it" } ?: "")
        },
        subtitle = entry.detail,
        leading = { GlassBadge(icon = Icons.Filled.Recycling) },
        extra = {
            Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.xs + RumaSpace.xs / 2), verticalAlignment = Alignment.CenterVertically) {
                when (entry.status) {
                    HistoryStatus.COMPLETED -> StatusChip(entry.statusLabel, RumaColors.Rider)
                    HistoryStatus.PARTIAL -> StatusChip(entry.statusLabel, RumaColors.Admin)
                    HistoryStatus.CANCELLED -> GlassChip(entry.statusLabel, color = RumaColors.Text2)
                }
                Text(entry.time, color = RumaColors.Text2, style = RumaType.caption)
            }
            entry.note?.let { Text(it, color = RumaColors.Text2, style = RumaType.caption) }
        },
        trailing = if (entry.earnings != "—") {
            { Text(entry.earnings, color = RumaColors.Money, style = RumaType.rowTitle) }
        } else null,
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1000)
@Composable
private fun RiderHistoryPreview() {
    RumaTheme {
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
