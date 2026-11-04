package com.example.maki.ui.screens.generator

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.maki.ui.components.makiCard
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiType

private data class RankEntry(
    val rankLabel: String,
    val medalColor: Color?,      // null => plain rank number (no medal)
    val avatarColor: Color,
    val initial: String,
    val name: String,
    val isYou: Boolean = false,
    val streak: String? = null,
    val extra: String? = null,
    val points: String,
    val highlighted: Boolean = false,
)

private val SampleRankings = RankingsUi(
    boardTitle = "Jr. Las Flores",
    entries = listOf(
        RankEntryUi(1, "Fam. Quispe", "Q", "1,240", "Racha 15 días", null, isYou = false),
        RankEntryUi(2, "Fam. Nureña", "N", "1,180", "Racha 21 días", "A 60 pts del 1° lugar", isYou = true),
        RankEntryUi(3, "Fam. Torres", "T", "990", null, null, isYou = false),
        RankEntryUi(4, "Fam. López", "L", "870 pts", null, null, isYou = false),
        RankEntryUi(5, "Fam. Rojas", "R", "650 pts", null, null, isYou = false),
    ),
    rewards = listOf(
        RewardUi(1, "x1.5 puntos la próxima semana"),
        RewardUi(2, "x1.3 puntos"),
        RewardUi(3, "x1.1 puntos"),
    ),
)

@Composable
fun GeneratorRankingsScreen(state: RankingsUi? = null, onBack: () -> Unit = {}) {
    val s = state ?: SampleRankings

    Column(Modifier.fillMaxSize().makiScreen().statusBarsPadding()) {
        DetailHeader(s.boardTitle, onBack, leadingIcon = Icons.Filled.EmojiEvents, leadingIconTint = MakiColors.Money)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Which board this is. It used to be four tabs — Semanal / Mensual /
            // Cuadra / Distrito — that only moved a highlight: the list never
            // changed, because the database opens one period per scope at a time.
            if (s.periodLabel.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MakiColors.GenTint)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = MakiColors.Gen, modifier = Modifier.size(16.dp))
                    Text(
                        s.periodLabel, color = MakiColors.Gen, style = MakiType.chip,
                    )
                }
            }
            s.entries.forEach { RankRow(it.toRankEntry()) }
            PrizesCard(s.rewards)
        }
    }
}

/** Derives the medal/avatar styling from rank + "you" flag. */
private fun RankEntryUi.toRankEntry(): RankEntry {
    val gray = MakiColors.Silver
    val medal = when (rank) {
        1 -> MakiColors.Gold
        2 -> if (isYou) MakiColors.Gen else gray
        3 -> MakiColors.Bronze
        else -> null
    }
    return RankEntry(
        rankLabel = rank.toString(),
        medalColor = medal,
        avatarColor = if (rank <= 3) MakiColors.Gen else gray,
        initial = initial,
        name = name,
        isYou = isYou,
        streak = streak,
        extra = extra,
        points = points,
        highlighted = isYou,
    )
}

@Composable
private fun RankRow(e: RankEntry) {
    Row(
        Modifier.fillMaxWidth()
            .makiShadow(16.dp, 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (e.highlighted) MakiColors.GenTint else MakiColors.Surface)
            .then(if (e.highlighted) Modifier.border(1.6.dp, MakiColors.Gen, RoundedCornerShape(16.dp)) else Modifier)
            .padding(start = 12.dp, end = 14.dp, top = 13.dp, bottom = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (e.medalColor != null) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(e.medalColor), contentAlignment = Alignment.Center) {
                Text(e.rankLabel, color = MakiColors.OnDark, style = MakiType.cardTitle)
            }
        } else {
            Text(e.rankLabel, color = MakiColors.Text2, style = MakiType.cardTitle,
                textAlign = TextAlign.Center, modifier = Modifier.width(30.dp))
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(e.avatarColor), contentAlignment = Alignment.Center) {
            Text(e.initial, color = MakiColors.OnDark, style = MakiType.cardTitle)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e.name, color = if (e.highlighted) MakiColors.Gen else MakiColors.Text, style = MakiType.cardTitle)
                if (e.isYou) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MakiColors.Gen).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("TÚ", color = MakiColors.OnDark, style = MakiType.overline)
                    }
                }
            }
            if (e.streak != null) Text(e.streak, color = MakiColors.Streak, style = MakiType.label)
            if (e.extra != null) Text(e.extra, color = MakiColors.Text2, style = MakiType.label)
        }
        Text(e.points, color = if (e.highlighted) MakiColors.Gen else MakiColors.Text, style = MakiType.cardTitle)
    }
}

@Composable
private fun PrizesCard(rewards: List<RewardUi>) {
    var expanded by remember { mutableStateOf(true) }
    Column(
        Modifier.fillMaxWidth().makiCard().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CardGiftcard, null, tint = MakiColors.Money, modifier = Modifier.size(19.dp))
            Text("Premios del ranking", color = MakiColors.Text, style = MakiType.cardTitle, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = MakiColors.Text2, modifier = Modifier.size(18.dp))
        }
        if (expanded) rewards.forEach { PrizeRow(medalColor(it.rank), it.rank.toString(), it.text) }
    }
}

private fun medalColor(rank: Int): Color = when (rank) {
    1 -> MakiColors.Gold
    2 -> MakiColors.Silver
    3 -> MakiColors.Bronze
    else -> MakiColors.Silver
}

@Composable
private fun PrizeRow(color: Color, rank: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(rank, color = MakiColors.OnDark, style = MakiType.chip)
        }
        Text(text, color = MakiColors.Text, style = MakiType.label)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun RankingsPreview() {
    MAKITheme { GeneratorRankingsScreen() }
}
