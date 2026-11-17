package pe.ruma.app.ui.screens.rider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.HeroRule
import pe.ruma.app.ui.components.RumaBar
import pe.ruma.app.ui.components.RumaBars
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaSkeleton
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.ProgressTrack
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

data class EarningDayUi(
    val day: String,
    val recs: String,
    val amount: String,
    /** Share of the best day in the week, 0..1 — the bar's height. */
    val fraction: Float = 0f,
    val isToday: Boolean = false,
    val inactive: Boolean = false,
)

data class RiderEarningsUiState(
    val monthEarned: String,
    val goalRemaining: String,
    val goalText: String,
    val progress: Float,
    val today: String,
    val todaySub: String,
    val days: List<EarningDayUi>,
    val weekTotal: String = "",
    val available: String,
    val withdrawNote: String,
    /** Below the S/50 minimum the CTA is visibly disabled instead of failing on tap. */
    val canWithdraw: Boolean = false,
)


/**
 * Real earnings: month total against the rider's own `monthly_goal`, today's take,
 * and the last 7 days from `rider_earnings`. Days without work are shown too, so a
 * quiet week reads as a quiet week rather than missing data.
 */
@Composable
fun RiderEarningsScreen(
    state: RiderEarningsUiState? = null,
    error: String? = null,
    onBack: () -> Unit = {},
    onWithdraw: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().rumaScreen(RumaColors.Rider).statusBarsPadding()) {
        DetailHeader(title = "Mis Ganancias", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            when {
                error != null -> item { RumaError(error, onRetry, accent = RumaColors.Rider) }
                state == null -> item { RumaSkeleton(listOf(96.dp, 72.dp, 72.dp), label = "Cargando tus ganancias…") }
                else -> {
                    item { SaldoHero(state) }
                    item { SemanaChart(state) }
                    item { Retiro(state, onWithdraw) }
                    // Only the days that produced something: seven empty rows are noise.
                    state.days.filterNot { it.inactive }.takeIf { it.isNotEmpty() }?.let { active ->
                        item { SectionLabel("Movimientos") }
                        item {
                            RumaList(active.reversed()) { d ->
                                RumaRow(
                                    d.day, subtitle = d.recs,
                                    trailing = { Text("+${d.amount}", color = RumaColors.Money, style = RumaType.rowTitle) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The month against the rider's own goal, with today as its last line. */
@Composable
private fun SaldoHero(state: RiderEarningsUiState) {
    Column(
        Modifier.rumaHero(RumaGradient.rider, glow = RumaGradient.glowOf(RumaColors.Rider))
            .fillMaxWidth().padding(RumaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        Text("GANADO ESTE MES", color = RumaColors.OnRiderSoft, style = RumaType.overline)
        Text(state.monthEarned, color = RumaColors.OnDark, style = RumaType.metricHero)
        if (state.today.isNotBlank()) {
            Text(
                listOf(state.today, state.todaySub).filter { it.isNotBlank() }.joinToString(" · "),
                color = RumaColors.OnRiderSoft, style = RumaType.body,
            )
        }
        HeroRule()
        ProgressTrack(state.progress)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(state.goalRemaining, color = RumaColors.OnRiderSoft, style = RumaType.bodySmall, modifier = Modifier.weight(1f))
            Text(state.goalText, color = RumaColors.OnDark, style = RumaType.chip)
        }
    }
}

/**
 * The week at a glance (PWA `Bars`): a day without work is a stub, not a red
 * "sin actividad" — nothing failed, the rider simply did not ride.
 */
@Composable
private fun SemanaChart(state: RiderEarningsUiState) {
    SoftCard(gap = RumaSpace.md) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ÚLTIMOS 7 DÍAS", color = RumaColors.Text2, style = RumaType.overline, modifier = Modifier.weight(1f))
            Text(state.weekTotal, color = RumaColors.Text, style = RumaType.rowTitle)
        }
        RumaBars(
            state.days.map { RumaBar(it.day, it.fraction, it.amount.removePrefix("S/"), it.isToday) },
            RumaColors.Rider, unit = " soles",
        )
    }
}

@Composable
private fun Retiro(state: RiderEarningsUiState, onWithdraw: () -> Unit) {
    SoftCard(gap = RumaSpace.sm) {
        Text("DISPONIBLE PARA RETIRO", color = RumaColors.Text2, style = RumaType.overline)
        Text(state.available, color = RumaColors.Text, style = RumaType.screenTitle)
        PrimaryButton(
            "Retirar a Yape",
            Modifier.fillMaxWidth().semantics {
                contentDescription = if (state.canWithdraw) "Retirar ${state.available} a Yape"
                else "Retiro no disponible. ${state.withdrawNote}"
            },
            icon = Icons.Filled.Smartphone,
            enabled = state.canWithdraw,
            color = RumaColors.Rider,
            onClick = onWithdraw,
        )
        Text(state.withdrawNote, color = RumaColors.Text2, style = RumaType.bodySmall)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun RiderEarningsPreview() {
    RumaTheme {
        RiderEarningsScreen(
            state = RiderEarningsUiState(
                monthEarned = "S/340.00", goalRemaining = "Faltan S/110.00 para tu meta",
                goalText = "Meta S/450", progress = 0.75f, today = "S/8.50 hoy",
                todaySub = "5 recojos completados",
                days = listOf(
                    EarningDayUi("Sáb", "—", "S/0.00", 0f, false, true),
                    EarningDayUi("Dom", "2 recojos", "S/5.20", 0.6f),
                    EarningDayUi("Lun", "—", "S/0.00", 0f, false, true),
                    EarningDayUi("Mar", "3 recojos", "S/7.90", 0.9f),
                    EarningDayUi("Mié", "1 recojo", "S/2.60", 0.3f),
                    EarningDayUi("Ayer", "—", "S/0.00", 0f, false, true),
                    EarningDayUi("Hoy", "5 recojos", "S/8.50", 1f, isToday = true),
                ),
                weekTotal = "S/24.20",
                available = "S/340.00", withdrawNote = "Mínimo S/50.00 · 1-2 días hábiles",
                canWithdraw = true,
            ),
        )
    }
}
