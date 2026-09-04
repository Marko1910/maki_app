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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.navigation.GenRoutes
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

// Sample state mirroring the STAGING seed data for Fam. Nureña.
data class HomeUiState(
    val displayName: String = "Fam. Nureña",
    val initials: String = "FN",
    val greetingPhrase: String = "Sigue así, cada residuo cuenta.",
    val streakDay: Int = 21,
    val ecoPoints: Int = 4250,
    val moneyValue: String = "S/4.25",
    val recommendation: String = "Reciclas mucho PET, ¿pruebas con vidrio esta semana?",
    val riderEtaText: String = "A 3 cuadras · Llega en ~5 min",
    val blockRank: String = "#2",
    val challengePct: Int = 53,
    val co2Avoided: String = "12.4 kg",
    val nextPickup: String = "Mañana 9am",
    val hasUnreadNotifications: Boolean = true,
    val riderIncoming: Boolean = true,
)

@Composable
fun GeneratorHomeScreen(
    state: HomeUiState = HomeUiState(),
    onNavigate: (String) -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MakiColors.Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeHeader(state, onBell = { onNavigate(GenRoutes.NOTIFICATIONS) })
        EcoPointsCard(state, onClick = { onNavigate(GenRoutes.WALLET) })
        CameraCta(onClick = { onNavigate(GenRoutes.CAMERA) })
        CentersCta(onClick = { onNavigate(GenRoutes.CENTERS) })
        RecommendationCard(state.recommendation)
        if (state.riderIncoming) ProximityCard(state.riderEtaText, onClick = { onNavigate(GenRoutes.TRACKING) })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                modifier = Modifier.weight(1f), onClick = { onNavigate(GenRoutes.RANKINGS) },
                icon = Icons.Filled.EmojiEvents, iconTint = MakiColors.Money, iconBg = MakiColors.MoneyTint,
                trailing = Icons.AutoMirrored.Filled.TrendingUp, trailingTint = MakiColors.Success,
                value = state.blockRank, label = "Tu cuadra",
            )
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Inventory2, iconTint = MakiColors.Gen, iconBg = MakiColors.GenTint,
                value = "${state.challengePct}%", label = "Gran Aplastada", progress = state.challengePct / 100f,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Eco, iconTint = MakiColors.Success, iconBg = MakiColors.SuccessTint,
                value = state.co2Avoided, label = "CO₂ evitado",
            )
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.LocalShipping, iconTint = MakiColors.Rider, iconBg = MakiColors.RiderTint,
                value = state.nextPickup, label = "Próximo recojo",
            )
        }
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, onBell: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar removed: the profile is already reachable from the bottom bar.
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Hola, ${state.displayName}", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            Text(state.greetingPhrase, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
        Box(contentAlignment = Alignment.TopEnd) {
            CircleButton(Icons.Outlined.Notifications, onBell)
            if (state.hasUnreadNotifications) {
                Box(
                    Modifier
                        .padding(top = 8.dp, end = 6.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(MakiColors.Streak)
                        .border(2.dp, MakiColors.Surface, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun CircleButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .makiShadow(21.dp, 4.dp)
            .size(42.dp)
            .clip(CircleShape)
            .background(MakiColors.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = MakiColors.Text, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EcoPointsCard(state: HomeUiState, onClick: () -> Unit) {
    Column(
        Modifier
            .makiShadow(22.dp, 10.dp, Color(0x400F6E56))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(MakiColors.Gen, MakiColors.GenDark)))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MakiColors.WhiteTint)
                .padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.LocalFireDepartment, null, tint = Color(0xFFFFD9A8), modifier = Modifier.size(16.dp))
            Text("Día ${state.streakDay} · Racha activa", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("%,d".format(state.ecoPoints), color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp)
                Text("Eco-Puntos", color = MakiColors.OnDarkSoft, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MakiColors.WhiteTint)
                    .padding(start = 11.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.AccountBalanceWallet, null, tint = Color(0xFFFFE7B0), modifier = Modifier.size(16.dp))
                Text("= ${state.moneyValue}", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun CameraCta(onClick: () -> Unit) {
    Row(
        Modifier
            .makiShadow(20.dp, 8.dp, Color(0x590F6E56))
            .clip(RoundedCornerShape(20.dp))
            .background(MakiColors.Gen)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.WhiteTint),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("TOMAR FOTO", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text("Detectar residuos con IA", color = MakiColors.OnDarkSoft, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CentersCta(onClick: () -> Unit) {
    Row(
        Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface)
            .fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Filled.Place, MakiColors.Gen, MakiColors.GenTint, boxSize = 40.dp, iconSize = 21.dp, radius = 14.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Puntos de acopio cerca", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Mira dónde reciclar y los precios", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MakiColors.Text2, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RecommendationCard(text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x0F0F6E56))
            .border(1.dp, MakiColors.GenTintStrong, RoundedCornerShape(16.dp))
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(MakiColors.Gen),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Lightbulb, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Maki te recomienda", color = MakiColors.Gen, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Text(text, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun ProximityCard(eta: String, onClick: () -> Unit) {
    Row(
        Modifier
            .makiShadow(16.dp, 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MakiColors.Surface)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.AutoMirrored.Filled.DirectionsBike, MakiColors.Rider, MakiColors.RiderTint, boxSize = 40.dp, iconSize = 21.dp, radius = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Eco-Rider en camino", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(eta, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(MakiColors.SuccessTint).padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(MakiColors.Success))
            Text("En vivo", color = MakiColors.Success, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    trailing: ImageVector? = null,
    trailingTint: Color = MakiColors.Text2,
    progress: Float? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .makiShadow(16.dp, 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MakiColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, iconTint, iconBg, boxSize = 34.dp, iconSize = 19.dp, radius = 17.dp)
            if (trailing != null) Icon(trailing, null, tint = trailingTint, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(value, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text(label, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        }
        if (progress != null) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MakiColors.Border)) {
                Box(Modifier.fillMaxWidth(progress).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MakiColors.Gen))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun GeneratorHomePreview() {
    MAKITheme { GeneratorHomeScreen() }
}
