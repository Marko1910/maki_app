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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont
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
    val tag: String? = null,
    val tagNegative: Boolean = false,
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
    val available: String,
    val withdrawNote: String,
    /** Below the S/50 minimum the CTA is visibly disabled instead of failing on tap. */
    val canWithdraw: Boolean = false,
)

private val HeroSubtitle = Color(0xFFD2E4F5)
private val HeroTrack = Color(0x2EFFFFFF)

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
    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()) {
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
                            "ÚLTIMOS 7 DÍAS", color = MakiColors.Text2, fontFamily = MakiFont,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    item {
                        Column(
                            Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp))
                                .background(MakiColors.Surface).fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        ) {
                            state.days.forEachIndexed { i, d ->
                                DayRow(d)
                                if (i < state.days.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(MakiColors.Border))
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
        Modifier.makiShadow(20.dp, 8.dp, Color(0x40185FA5)).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(MakiColors.Rider, Color(0xFF1A8CDB)))).fillMaxWidth().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Ganado este mes", color = HeroSubtitle, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(state.monthEarned, color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp)
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(HeroTrack)) {
            Box(Modifier.fillMaxWidth(state.progress).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(state.goalRemaining, color = HeroSubtitle, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(state.goalText, color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ResumenDia(state: RiderEarningsUiState) {
    Row(
        Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface).fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MakiColors.SuccessTint),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.AccountBalanceWallet, null, tint = MakiColors.Success, modifier = Modifier.size(23.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(state.today, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(state.todaySub, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DayRow(d: EarningDayUi) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(d.day, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(42.dp))
        Text(d.recs, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.weight(1f))
        d.tag?.let {
            Text(it, color = if (d.tagNegative) MakiColors.Error else MakiColors.Success, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(d.amount, color = if (d.inactive) MakiColors.Text2 else MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun Retiro(state: RiderEarningsUiState, onWithdraw: () -> Unit) {
    Column(
        Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface)
            .border(1.5.dp, MakiColors.Rider, RoundedCornerShape(16.dp)).fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Disponible para retiro", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(state.available, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
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
                tint = if (state.canWithdraw) Color.White else MakiColors.Text2,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Retirar a Yape",
                color = if (state.canWithdraw) Color.White else MakiColors.Text2,
                fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
            )
        }
        Text(state.withdrawNote, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                days = listOf(EarningDayUi("Hoy", "5 rec", "S/8.50")),
                available = "S/340.00", withdrawNote = "Mínimo S/50.00 · 1-2 días hábiles",
                canWithdraw = true,
            ),
        )
    }
}
