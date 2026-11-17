package pe.ruma.app.ui.screens.rider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.RumaBar
import pe.ruma.app.ui.components.RumaBars
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaSkeleton
import pe.ruma.app.ui.components.MetricCard
import pe.ruma.app.ui.components.MetricRow
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

data class StatTileUi(val icon: ImageVector, val tint: Color, val value: String, val label: String)
/** [value] is the raw count: the bar height encodes it, the label states it. */
data class StatBarUi(val label: String, val heightDp: Int, val active: Boolean = false, val value: String = "")
data class StatBadgeUi(val icon: ImageVector, val label: String, val locked: Boolean = false)

data class RiderStatsUiState(
    val tiles: List<StatTileUi>,
    val chartTitle: String = "Recojos por día · última semana",
    val bars: List<StatBarUi> = emptyList(),
    val badges: List<StatBadgeUi> = emptyList(),
    val weeklyTotal: String = "",
)

/**
 * The rider's own totals from `rider_profiles`, a 7-day pickup chart built from
 * `rider_earnings`, and unlocked badges from `user_achievements`. Nothing here is
 * estimated: a metric with no data reads as a dash, never as an invented number.
 */
@Composable
fun RiderStatsScreen(
    state: RiderStatsUiState? = null,
    error: String? = null,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().rumaScreen(RumaColors.Rider).statusBarsPadding()) {
        DetailHeader(title = "Mis Estadísticas", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            when {
                error != null -> item { RumaError(error, onRetry, accent = RumaColors.Rider) }
                state == null -> item { RumaSkeleton(listOf(96.dp, 72.dp, 72.dp), label = "Cargando tus estadísticas…") }
                else -> {
                    // The two environmental totals lead, as in the PWA; the rest follow as tiles.
                    val lead = state.tiles.filter { it.label == "reciclado" || it.label.startsWith("CO") }
                        .takeIf { it.size == 2 } ?: state.tiles.take(2)
                    item { StatsHero(lead) }
                    item { ChartCard(state) }
                    (state.tiles - lead.toSet()).chunked(2).forEach { pair ->
                        item {
                            MetricRow {
                                pair.forEach { t -> MetricCard(t.value, t.label.replaceFirstChar { it.uppercase() }, Modifier.weight(1f)) }
                                if (pair.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                    item { SectionLabel("Insignias") }
                    item {
                        if (state.badges.isEmpty()) {
                            RumaEmpty(
                                icon = Icons.Filled.EmojiEvents,
                                title = "Aún sin insignias",
                                hint = "Completa recojos para desbloquear tus primeros logros.",
                            )
                        } else {
                            BadgesCard(state.badges)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHero(tiles: List<StatTileUi>) {
    Row(
        Modifier.rumaHero(RumaGradient.rider, glow = RumaGradient.glowOf(RumaColors.Rider))
            .fillMaxWidth().padding(RumaSpace.xl),
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        tiles.forEach { t ->
            Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = t.value + " " + t.label }) {
                Text(t.value, color = RumaColors.OnDark, style = RumaType.screenTitle)
                Text(t.label.replaceFirstChar { it.uppercase() }, color = RumaColors.OnRiderSoft, style = RumaType.bodySmall)
            }
        }
    }
}

@Composable
private fun ChartCard(state: RiderStatsUiState) {
    SoftCard(gap = RumaSpace.md) {
        Text(state.chartTitle, color = RumaColors.Text, style = RumaType.rowTitle)
        if (state.weeklyTotal.isNotBlank()) Text(state.weeklyTotal, color = RumaColors.Text2, style = RumaType.bodySmall)
        RumaBars(
            state.bars.map { RumaBar(it.label, it.heightDp.toFloat(), it.value.ifBlank { "0" }, it.active) },
            RumaColors.Rider, unit = " recojos",
        )
    }
}

/** Unlocked badges ringed in gold, locked ones faded (PWA `Stats` insignias), three a row. */
@Composable
private fun BadgesCard(badges: List<StatBadgeUi>) {
    SoftCard(gap = RumaSpace.lg) {
        badges.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                row.forEach { b ->
                    Column(
                        Modifier.weight(1f).alpha(if (b.locked) 0.55f else 1f)
                            .clearAndSetSemantics {
                                contentDescription = (if (b.locked) "Insignia bloqueada: " else "Insignia conseguida: ") + b.label
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
                    ) {
                        GlassBadge(
                            icon = if (b.locked) Icons.Filled.Lock else b.icon,
                            size = 52.dp, circle = true,
                            ring = if (b.locked) RumaColors.Hairline else RumaColors.Gold,
                        )
                        Text(
                            b.label, color = if (b.locked) RumaColors.Text2 else RumaColors.Text,
                            style = RumaType.bodySmall, textAlign = TextAlign.Center, maxLines = 2,
                        )
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1200)
@Composable
private fun RiderStatsPreview() {
    RumaTheme {
        RiderStatsScreen(
            state = RiderStatsUiState(
                tiles = listOf(
                    StatTileUi(Icons.Filled.Home, RumaColors.Rider, "340", "recolecciones"),
                    StatTileUi(Icons.Filled.Star, RumaColors.Money, "4.8", "de 52 reseñas"),
                ),
                bars = listOf(StatBarUi("L", 40, value = "2"), StatBarUi("M", 90, active = true, value = "5")),
                badges = listOf(StatBadgeUi(Icons.Filled.EmojiEvents, "1er recojo")),
                weeklyTotal = "7 recojos esta semana",
            ),
        )
    }
}
