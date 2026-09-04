package com.example.maki.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.navigation.RiderRoutes
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

// Neutral defaults: every field is filled by RiderViewModel from the database.
// A placeholder that looks like real data is worse than an em dash.
data class RiderDashboardUiState(
    val riderName: String = "",
    val zone: String = "",
    val unreadCount: Int = 0,
    val pickupsTotal: String = "0",
    val completed: String = "0/0",
    val distance: String = "—",
    val earnedToday: String = "S/0.00",
    val nextStopName: String = "Sin recojos pendientes",
    val nextStopAddress: String = "Te avisaremos cuando haya solicitudes",
    val nextStopDistance: String = "",
    val nextStopChips: List<String> = emptyList(),
    val monthEarnings: String = "S/0.00",
    val monthGrowth: String = "",
    val goalProgress: Float = 0f,
    val goalPctText: String = "",
    val goalText: String = "",
    val rankingTitle: String = "",
    val rankingSubtitle: String = "",
    val weather: String = "",
    val weatherHint: String = "",
)

private val CtaSubtitle = Color(0xFFD2E4F5)
private val ClimaBg = Color(0x121A8CDB)

@Composable
fun RiderDashboardScreen(
    state: RiderDashboardUiState = RiderDashboardUiState(),
    onNavigate: (String) -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(state, onBell = { onNavigate(RiderRoutes.NOTIFICATIONS) })
        EstadoCard(state)
        CtaIniciarRuta(onClick = { onNavigate(RiderRoutes.ROUTE) })
        SiguienteParada(state, onClick = { onNavigate(RiderRoutes.NAVIGATION) })
        GananciasWidget(state, onClick = { onNavigate(RiderRoutes.EARNINGS) })
        RankingWidget(state, onClick = { onNavigate(RiderRoutes.RANKING) })
        ClimaWidget(state)
    }
}

@Composable
private fun Header(state: RiderDashboardUiState, onBell: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("Hola, ${state.riderName}", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, tint = MakiColors.Rider, modifier = Modifier.size(13.dp))
                Text(state.zone, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        Box(contentAlignment = Alignment.TopEnd) {
            CircleButton(Icons.Outlined.Notifications, onBell)
            if (state.unreadCount > 0) {
                Box(
                    Modifier.padding(top = 6.dp, end = 4.dp).size(17.dp).clip(CircleShape)
                        .background(MakiColors.Error).border(2.dp, MakiColors.Surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${state.unreadCount}", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp) }
            }
        }
    }
}

@Composable
private fun CircleButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.makiShadow(21.dp, 4.dp).size(42.dp).clip(CircleShape).background(MakiColors.Surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MakiColors.Text, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun EstadoCard(state: RiderDashboardUiState) {
    Column(
        Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface).fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCell(Modifier.weight(1f), Icons.AutoMirrored.Filled.Assignment, state.pickupsTotal, "recojos")
            StatCell(Modifier.weight(1f), Icons.Filled.CheckCircle, state.completed, "completados")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCell(Modifier.weight(1f), Icons.Filled.Route, state.distance, "recorridos")
            StatCell(Modifier.weight(1f), Icons.Filled.AccountBalanceWallet, state.earnedToday, "hoy")
        }
    }
}

@Composable
private fun StatCell(modifier: Modifier, icon: ImageVector, value: String, label: String) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, MakiColors.Rider, MakiColors.RiderTint, boxSize = 38.dp, iconSize = 19.dp, radius = 12.dp)
        Column {
            Text(value, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(label, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CtaIniciarRuta(onClick: () -> Unit) {
    Row(
        Modifier.makiShadow(18.dp, 8.dp, Color(0x59185FA5)).clip(RoundedCornerShape(18.dp)).background(MakiColors.Rider)
            .fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)).background(MakiColors.WhiteTint),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.RocketLaunch, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("INICIAR RUTA", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text("Tu ruta optimizada te espera", color = CtaSubtitle, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SiguienteParada(state: RiderDashboardUiState, onClick: () -> Unit) {
    Row(
        Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface).fillMaxWidth()
            .height(IntrinsicSize.Min).clickable(onClick = onClick),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(MakiColors.Rider))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = MakiColors.Rider, modifier = Modifier.size(15.dp))
                Text("SIGUIENTE PARADA", color = MakiColors.Rider, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(state.nextStopDistance, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text(state.nextStopName, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(state.nextStopAddress, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.nextStopChips.forEach { MaterialChip(it) }
            }
        }
    }
}

@Composable
private fun MaterialChip(label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x0D185FA5)).padding(start = 9.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Inventory2, null, tint = MakiColors.Rider, modifier = Modifier.size(13.dp))
        Text(label, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun GananciasWidget(state: RiderDashboardUiState, onClick: () -> Unit) {
    Column(
        Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface).fillMaxWidth()
            .clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountBalanceWallet, null, tint = MakiColors.Money, modifier = Modifier.size(18.dp))
                Text("Ganancias del mes", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(MakiColors.SuccessTint).padding(start = 6.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = MakiColors.Success, modifier = Modifier.size(13.dp))
                Text(state.monthGrowth, color = MakiColors.Success, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
        Text(state.monthEarnings, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MakiColors.Border)) {
            Box(Modifier.fillMaxWidth(state.goalProgress).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MakiColors.Rider))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(state.goalPctText, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(state.goalText, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RankingWidget(state: RiderDashboardUiState, onClick: () -> Unit) {
    Row(
        Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface).fillMaxWidth()
            .clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Filled.EmojiEvents, MakiColors.Money, MakiColors.MoneyTint, boxSize = 44.dp, iconSize = 23.dp, radius = 14.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(state.rankingTitle, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(state.rankingSubtitle, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MakiColors.Rider, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ClimaWidget(state: RiderDashboardUiState) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(ClimaBg).fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.WbSunny, null, tint = MakiColors.Money, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(state.weather, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(state.weatherHint, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun RiderDashboardPreview() {
    MAKITheme { RiderDashboardScreen() }
}
