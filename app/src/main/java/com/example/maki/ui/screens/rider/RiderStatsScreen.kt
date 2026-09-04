package com.example.maki.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MakiLoading

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

private val BarInactive = Color(0xFFD5DEEA)
private val LockedBg = Color(0xFFE7EBE9)
private val LockedIcon = Color(0xFFA6B0AB)
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
    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()) {
        DetailHeader(title = "Mis Estadísticas", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                error != null -> item { MakiError(error, onRetry) }
                state == null -> item { MakiLoading("Cargando tus estadísticas…") }
                else -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.tiles.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { StatTile(Modifier.weight(1f), it) }
                                }
                            }
                        }
                    }
                    item { ChartCard(state) }
                    item { Text("INSIGNIAS", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    item {
                        if (state.badges.isEmpty()) {
                            MakiEmpty(
                                icon = Icons.Filled.EmojiEvents,
                                title = "Aún sin insignias",
                                hint = "Completa recojos para desbloquear tus primeros logros.",
                            )
                        } else {
                            BadgesRow(state.badges)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(modifier: Modifier, t: StatTileUi) {
    Row(
        modifier.makiShadow(14.dp, 4.dp).clip(RoundedCornerShape(14.dp)).background(MakiColors.Surface).padding(14.dp)
            .clearAndSetSemantics { contentDescription = t.value + " " + t.label },
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(t.tint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) { Icon(t.icon, null, tint = t.tint, modifier = Modifier.size(20.dp)) }
        Column {
            Text(t.value, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(t.label, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ChartCard(state: RiderStatsUiState) {
    Column(
        Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface).fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(state.chartTitle, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (state.weeklyTotal.isNotBlank()) {
                Text(state.weeklyTotal, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            state.bars.forEach { bar ->
                Column(
                    Modifier.weight(1f)
                        // The chart is one node for a screen reader, not seven mute boxes.
                        .clearAndSetSemantics {
                            contentDescription = bar.label + ": " + bar.value.ifBlank { "0" } + " recojos"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        bar.value,
                        color = if (bar.active) MakiColors.Rider else MakiColors.Text2,
                        fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    )
                    Box(Modifier.fillMaxWidth().height(bar.heightDp.dp).clip(RoundedCornerShape(6.dp)).background(if (bar.active) MakiColors.Rider else BarInactive))
                    Text(bar.label, color = if (bar.active) MakiColors.Rider else MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun BadgesRow(badges: List<StatBadgeUi>) {
    // Badges come from the DB, so the count is unknown - wrap in rows of five
    // rather than shrinking every badge as the list grows.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      badges.chunked(5).forEach { rowBadges ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        rowBadges.forEach { b ->
            Column(
                Modifier.weight(1f)
                    .clearAndSetSemantics {
                        contentDescription = (if (b.locked) "Insignia bloqueada: " else "Insignia conseguida: ") + b.label
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .then(if (!b.locked) Modifier.makiShadow(27.dp, 4.dp, Color(0x40185FA5)) else Modifier)
                        .size(54.dp).clip(CircleShape).background(if (b.locked) LockedBg else MakiColors.Rider),
                    contentAlignment = Alignment.Center,
                ) { Icon(b.icon, null, tint = if (b.locked) LockedIcon else Color.White, modifier = Modifier.size(24.dp)) }
                Text(b.label, color = if (b.locked) MakiColors.Text2 else MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        // Keep the last, partially filled row aligned left instead of stretched.
        repeat(5 - rowBadges.size) { Box(Modifier.weight(1f)) }
        }
      }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1200)
@Composable
private fun RiderStatsPreview() {
    MAKITheme {
        RiderStatsScreen(
            state = RiderStatsUiState(
                tiles = listOf(
                    StatTileUi(Icons.Filled.Home, MakiColors.Rider, "340", "recolecciones"),
                    StatTileUi(Icons.Filled.Star, MakiColors.Money, "4.8", "de 52 reseñas"),
                ),
                bars = listOf(StatBarUi("L", 40, value = "2"), StatBarUi("M", 90, active = true, value = "5")),
                badges = listOf(StatBadgeUi(Icons.Filled.EmojiEvents, "1er recojo")),
                weeklyTotal = "7 recojos esta semana",
            ),
        )
    }
}
