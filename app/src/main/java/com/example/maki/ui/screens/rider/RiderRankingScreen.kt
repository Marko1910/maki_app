package com.example.maki.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MakiLoading

internal val Gold = Color(0xFFE0A41B)
internal val Silver = Color(0xFF9CA3AF)
internal val Bronze = Color(0xFFC58B5B)
private val TabsBg = Color(0x0D185FA5)
private val PodiumYouBg = Color(0x12185FA5)
private val PremiosBg = Color(0x0FBA7517)

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
    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()) {
        DetailHeader(
            title = state?.zone ?: "Ranking de riders",
            onBack = onBack,
            leadingIcon = Icons.Filled.EmojiEvents,
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                error != null -> item { MakiError(error, onRetry) }
                state == null -> item { MakiLoading("Cargando el ranking…") }
                state.podium.isEmpty() && state.rows.isEmpty() -> item {
                    MakiEmpty(
                        icon = Icons.Filled.EmojiEvents,
                        title = "Todavía no hay ranking abierto",
                        hint = "Cuando arranque el periodo verás tu posición entre los Eco-Riders de tu zona.",
                    )
                }
                else -> {
                    if (state.periodLabel.isNotBlank()) {
                        item {
                            Text(
                                state.periodLabel,
                                color = MakiColors.Text2, fontFamily = MakiFont,
                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            )
                        }
                    }
                    items(state.podium, key = { "p-${it.rank}" }) { PodiumCard(it) }
                    if (state.rows.isNotEmpty()) {
                        item {
                            Column(
                                Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp))
                                    .background(MakiColors.Surface).fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                            ) {
                                state.rows.forEachIndexed { i, r ->
                                    RankRow(r)
                                    if (i < state.rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(MakiColors.Border))
                                }
                            }
                        }
                    }
                    if (state.prizes.isNotEmpty()) item { PremiosCard(state) }
                }
            }
        }
    }
}

@Composable
private fun PodiumCard(p: PodiumUi) {
    if (p.isYou) {
        Row(
            Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(PodiumYouBg).fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(MakiColors.Rider))
            PodiumContent(p, Modifier.weight(1f).padding(14.dp))
        }
    } else {
        Box(Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface).border(1.5.dp, p.medalColor, RoundedCornerShape(16.dp)).fillMaxWidth()) {
            PodiumContent(p, Modifier.padding(14.dp))
        }
    }
}

@Composable
private fun PodiumContent(p: PodiumUi, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "Puesto ${p.rank}, ${p.name}" + (if (p.isYou) ", tú" else "") +
                ", ${p.points} puntos. ${p.sub}"
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(p.medalColor),
                contentAlignment = Alignment.Center,
            ) { Text("${p.rank}", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, color = if (p.isYou) MakiColors.Rider else MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    if (p.crown) Icon(Icons.Filled.WorkspacePremium, null, tint = MakiColors.Money, modifier = Modifier.size(16.dp))
                    if (p.isYou) Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(MakiColors.Rider).padding(horizontal = 7.dp, vertical = 2.dp),
                    ) { Text("Tú", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
                Text(p.sub, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
            Text(p.points, color = if (p.isYou) MakiColors.Rider else MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountBalanceWallet, null, tint = MakiColors.Money, modifier = Modifier.size(13.dp))
                Text(p.monthEarnings, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            p.gap?.let {
                Text(it, color = MakiColors.Rider, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RankRow(r: RankRowUi) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${r.rank}", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(22.dp))
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(MakiColors.RiderTint),
            contentAlignment = Alignment.Center,
        ) { Text(r.initial, color = MakiColors.Rider, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
        Text(
            if (r.isYou) "${r.name} · Tú" else r.name,
            color = if (r.isYou) MakiColors.Rider else MakiColors.Text,
            fontFamily = MakiFont,
            fontWeight = if (r.isYou) FontWeight.ExtraBold else FontWeight.Bold,
            fontSize = 14.sp, modifier = Modifier.weight(1f),
        )
        Text(r.points, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun PremiosCard(state: RiderRankingUiState) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(PremiosBg).fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CardGiftcard, null, tint = MakiColors.Money, modifier = Modifier.size(18.dp))
            Text("Premios del ranking", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, null, tint = MakiColors.Text2, modifier = Modifier.size(18.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.prizes.forEach { PrizeCard(Modifier.weight(1f), it) }
        }
        Text(state.prizeNote, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun PrizeCard(modifier: Modifier, p: PrizeUi) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(MakiColors.Surface).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(p.medalColor),
            contentAlignment = Alignment.Center,
        ) { Text("${p.rank}", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
        Text(p.value, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1300)
@Composable
private fun RiderRankingPreview() {
    MAKITheme {
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
