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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.maki.ui.components.makiCard
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.components.makiHero
import com.example.maki.ui.theme.MakiGradient
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiType
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MakiLoading

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
    Column(Modifier.fillMaxSize().makiScreen().statusBarsPadding()) {
        DetailHeader(title = "Mis Ganancias", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                error != null -> item { MakiError(error, onRetry) }
                state == null -> item { MakiLoading("Cargando tus ganancias…") }
                else -> {
                    item { SaldoHero(state) }
                    item { ResumenDia(state) }
                    item {
                        Text(
                            "ÚLTIMOS 7 DÍAS", color = MakiColors.Text2, style = MakiType.chip, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    item { SemanaChart(state) }
                    // Only the days that produced something: seven empty rows are noise.
                    state.days.filterNot { it.inactive }.takeIf { it.isNotEmpty() }?.let { active ->
                        item {
                            Column(
                                Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp))
                                    .background(MakiColors.Surface).fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                            ) {
                                active.reversed().forEachIndexed { i, d ->
                                    DayRow(d)
                                    if (i < active.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(MakiColors.Border))
                                }
                            }
                        }
                    }
                    item { Retiro(state, onWithdraw) }
                }
            }
        }
    }
}

@Composable
private fun SaldoHero(state: RiderEarningsUiState) {
    Column(
        Modifier.makiHero(MakiGradient.rider, glow = MakiGradient.glowOf(MakiColors.Rider))
            .fillMaxWidth().padding(MakiSpace.xl),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Ganado este mes", color = MakiColors.OnRiderSoft, style = MakiType.label)
        Text(state.monthEarned, color = MakiColors.OnDark, style = MakiType.metricHero)
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MakiColors.OnGradientTrack)) {
            Box(Modifier.fillMaxWidth(state.progress).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MakiColors.OnDark))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(state.goalRemaining, color = MakiColors.OnRiderSoft, style = MakiType.label)
            Text(state.goalText, color = MakiColors.OnDark, style = MakiType.chip)
        }
    }
}

@Composable
private fun ResumenDia(state: RiderEarningsUiState) {
    Row(
        Modifier.makiCard().fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MakiColors.SuccessTint),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.AccountBalanceWallet, null, tint = MakiColors.Success, modifier = Modifier.size(23.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(state.today, color = MakiColors.Text, style = MakiType.sectionHeading)
            Text(state.todaySub, color = MakiColors.Text2, style = MakiType.bodySmall)
        }
    }
}

/**
 * The week at a glance. A day without work is a short grey stub, not a red
 * "sin actividad" — nothing failed, the rider simply did not ride.
 */
@Composable
private fun SemanaChart(state: RiderEarningsUiState) {
    Column(
        Modifier.makiCard()
            .fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Esta semana", color = MakiColors.Text, style = MakiType.cardTitle)
            Text(state.weekTotal, color = MakiColors.Rider, style = MakiType.cardTitle)
        }
        Row(
            Modifier.fillMaxWidth().height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            state.days.forEach { d ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val height = (6 + (d.fraction * 70f)).dp
                    Box(
                        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    d.inactive -> MakiColors.Border
                                    d.isToday -> MakiColors.Rider
                                    else -> MakiColors.RiderTint
                                }
                            ),
                    )
                    Text(
                        d.day,
                        color = if (d.isToday) MakiColors.Rider else MakiColors.Text2,
                        style = if (d.isToday) MakiType.overline else MakiType.caption,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayRow(d: EarningDayUi) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(d.day, color = MakiColors.Text, style = MakiType.rowTitle, modifier = Modifier.width(52.dp))
        Text(d.recs, color = MakiColors.Text2, style = MakiType.bodySmall, modifier = Modifier.weight(1f))
        Text(d.amount, color = if (d.inactive) MakiColors.Text2 else MakiColors.Success, style = MakiType.cardTitle)
    }
}

@Composable
private fun Retiro(state: RiderEarningsUiState, onWithdraw: () -> Unit) {
    Column(
        Modifier.makiCard()
            .border(1.5.dp, MakiColors.Rider, RoundedCornerShape(16.dp)).fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Disponible para retiro", color = MakiColors.Text2, style = MakiType.label)
                Text(state.available, color = MakiColors.Text, style = MakiType.metricMedium)
            }
            Icon(Icons.Filled.Payments, null, tint = MakiColors.Success, modifier = Modifier.size(26.dp))
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (state.canWithdraw) MakiColors.Rider else MakiColors.Border)
                .clickable(enabled = state.canWithdraw, onClick = onWithdraw)
                .padding(vertical = 14.dp)
                .semantics {
                    contentDescription = if (state.canWithdraw) "Retirar ${state.available} a Yape"
                                         else "Retiro no disponible. ${state.withdrawNote}"
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Smartphone, null,
                tint = if (state.canWithdraw) MakiColors.OnDark else MakiColors.Text2,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Retirar a Yape",
                color = if (state.canWithdraw) MakiColors.OnDark else MakiColors.Text2,
                style = MakiType.cardTitle,
            )
        }
        Text(state.withdrawNote, color = MakiColors.Text2, style = MakiType.caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun RiderEarningsPreview() {
    MAKITheme {
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
