package pe.ruma.app.ui.screens.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaLoading
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.RankHero
import pe.ruma.app.ui.components.RankRow
import pe.ruma.app.ui.components.rumaCard
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

private val SampleRankings = RankingsUi(
    boardTitle = "Jr. Las Flores",
    periodLabel = "Semanal · 12 Oct - 18 Oct",
    entries = listOf(
        RankEntryUi(1, "Fam. Quispe", "Q", "1,240", "Racha 15 días", null, isYou = false),
        RankEntryUi(2, "Fam. Nureña", "N", "1,180", "Racha 21 días", "A 60 pts del 1° lugar", isYou = true),
        RankEntryUi(3, "Fam. Torres", "T", "990", null, null, isYou = false),
        RankEntryUi(4, "Fam. López", "L", "870", null, null, isYou = false),
    ),
    rewards = listOf(
        RewardUi(1, "x1.5 puntos la próxima semana"),
        RewardUi(2, "x1.3 puntos"),
        RewardUi(3, "x1.1 puntos"),
    ),
)

/**
 * The block's leaderboard, as the PWA draws it: your place in the one hero, everybody
 * in one grouped list, and the prizes folded behind a row.
 */
@Composable
fun GeneratorRankingsScreen(state: RankingsUi? = null, onBack: () -> Unit = {}, onScan: () -> Unit = {}) {
    val s = state ?: SampleRankings.takeIf { LocalInspectionMode.current }

    Column(Modifier.fillMaxSize().rumaScreen().statusBarsPadding()) {
        DetailHeader(
            s?.boardTitle?.ifBlank { null } ?: "Ranking de tu cuadra", onBack,
            // Which board this is: one period per scope is open at a time.
            subtitle = s?.periodLabel?.ifBlank { null },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            when {
                s == null -> pe.ruma.app.ui.components.RumaSkeleton(listOf(140.dp, 64.dp, 64.dp, 64.dp, 64.dp), Modifier.padding(horizontal = RumaSpace.gutter), "Cargando el ranking…")
                s.entries.isEmpty() -> RumaEmpty(
                    Icons.Filled.EmojiEvents, "Todavía no hay ranking abierto",
                    "Cuando arranque el periodo verás tu posición.", accent = RumaColors.Gen,
                    actionLabel = "Registrar residuos", onAction = onScan,
                )
                else -> {
                    s.entries.firstOrNull { it.isYou }?.let { me ->
                        RankHero(me.rank, me.name, me.extra, me.points, RumaColors.Gen)
                    }
                    RumaList(s.entries) { e ->
                        RankRow(e.rank, e.initial, e.name, e.points, RumaColors.Gen, isYou = e.isYou, sub = e.streak)
                    }
                    if (s.rewards.isNotEmpty()) Rewards(s.rewards)
                    // The ranking ends on what moves it: the family's next scan.
                    Text("Tu posición se mueve con cada recojo.", Modifier.fillMaxWidth(), style = RumaType.bodySmall, color = RumaColors.Text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    pe.ruma.app.ui.components.SecondaryButton("Registrar residuos", Modifier.fillMaxWidth(), onClick = onScan)
                }
            }
        }
    }
}

@Composable
private fun Rewards(rewards: List<RewardUi>) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().rumaCard()) {
        RumaRow(
            "Premios del ranking",
            leading = { GlassBadge(icon = Icons.Filled.EmojiEvents) },
            onClick = { open = !open },
        )
        if (open) rewards.forEach { r ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(RumaColors.Hair))
            RumaRow(
                r.text,
                leading = { Text("#${r.rank}", color = RumaColors.Text, style = RumaType.rowTitle) },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun RankingsPreview() {
    RumaTheme { GeneratorRankingsScreen() }
}
