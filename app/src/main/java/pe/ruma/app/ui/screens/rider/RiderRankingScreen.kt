package pe.ruma.app.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaSkeleton
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.RankHero
import pe.ruma.app.ui.components.RankRow
import pe.ruma.app.ui.components.initialsOf
import pe.ruma.app.ui.components.rumaCard
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

internal val Gold = RumaColors.Gold
internal val Silver = RumaColors.Silver
internal val Bronze = RumaColors.Bronze

data class PodiumUi(
    val rank: Int,
    val name: String,
    val sub: String,
    val points: String,
    val monthEarnings: String,
    val medalColor: Color,
    val crown: Boolean = false,
    val isYou: Boolean = false,
    val gap: String? = null,
)

data class RankRowUi(val rank: Int, val initial: String, val name: String, val points: String, val isYou: Boolean = false)
data class PrizeUi(val rank: Int, val value: String, val medalColor: Color)

data class RiderRankingUiState(
    val zone: String,
    /** Real period range, e.g. "1 Sep - 30 Sep" — replaces the old fake tab bar. */
    val periodLabel: String = "",
    val podium: List<PodiumUi> = emptyList(),
    val rows: List<RankRowUi> = emptyList(),
    val prizes: List<PrizeUi> = emptyList(),
    val prizeNote: String = "",
)

/**
 * The rider leaderboard for the open period (`leaderboard_periods.participant_role
 * = 'eco_rider'`). The old "Este mes / Semana pasada" tabs were decorative — there
 * is one open board — so the real period range takes their place.
 */
@Composable
fun RiderRankingScreen(
    state: RiderRankingUiState? = null,
    error: String? = null,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().rumaScreen(RumaColors.Rider).statusBarsPadding()) {
        DetailHeader(
            title = state?.zone ?: "Ranking de riders",
            onBack = onBack,
            subtitle = state?.periodLabel?.ifBlank { null },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            when {
                error != null -> item { RumaError(error, onRetry, accent = RumaColors.Rider) }
                state == null -> item { RumaSkeleton(listOf(96.dp, 72.dp, 72.dp), label = "Cargando el ranking…") }
                state.podium.isEmpty() && state.rows.isEmpty() -> item {
                    RumaEmpty(
                        icon = Icons.Filled.EmojiEvents,
                        title = "Todavía no hay ranking abierto",
                        hint = "Cuando arranque el periodo verás tu posición entre los Eco-Riders de tu zona.",
                    )
                }
                else -> {
                    // One list for everybody, as in the PWA: the podium is the first three rows.
                    val all = state.podium.map {
                        Entry(it.rank, initialsOf(it.name), it.name, it.points, it.isYou,
                            listOf(it.sub.takeIf { s -> s != "—" }, it.monthEarnings.ifBlank { null }).filterNotNull().joinToString(" · ").ifBlank { null },
                            it.gap)
                    } + state.rows.map { Entry(it.rank, it.initial, it.name, it.points, it.isYou, null, null) }
                    all.firstOrNull { it.isYou }?.let { me ->
                        item { RankHero(me.rank, me.name, me.gap, me.points, RumaColors.Rider) }
                    }
                    item {
                        RumaList(all) { e -> RankRow(e.rank, e.initials, e.name, e.points, RumaColors.Rider, e.isYou, e.sub) }
                    }
                    if (state.prizes.isNotEmpty()) item { Premios(state) }
                }
            }
        }
    }
}

private data class Entry(
    val rank: Int, val initials: String, val name: String, val points: String,
    val isYou: Boolean, val sub: String?, val gap: String?,
)

/** The prizes fold behind one row, like the PWA's "Premios del ranking". */
@Composable
private fun Premios(state: RiderRankingUiState) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().rumaCard()) {
        RumaRow(
            "Premios del ranking",
            subtitle = state.prizeNote.ifBlank { null },
            leading = { GlassBadge(icon = Icons.Filled.EmojiEvents) },
            onClick = { open = !open },
        )
        if (open) state.prizes.forEach { p ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(RumaColors.Hair))
            RumaRow(p.value, leading = { Text("#${p.rank}", color = RumaColors.Text, style = RumaType.rowTitle) })
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1300)
@Composable
private fun RiderRankingPreview() {
    RumaTheme {
        RiderRankingScreen(
            state = RiderRankingUiState(
                zone = "Ranking · SJL",
                periodLabel = "1 Sep - 30 Sep",
                podium = listOf(PodiumUi(1, "María G.", "Racha 12 días", "4,850", "+120 pts esta semana", Gold, crown = true)),
                rows = listOf(RankRowUi(4, "F", "Luis F.", "870")),
            ),
        )
    }
}
