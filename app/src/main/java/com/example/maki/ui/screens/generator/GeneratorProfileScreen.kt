package com.example.maki.ui.screens.generator

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.navigation.GenRoutes
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.SecondaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

@Composable
fun GeneratorProfileScreen(
    state: GeneratorUiState = GeneratorUiState(),
    onNavigate: (String) -> Unit = {},
    onInfo: (String) -> Unit = {},
    onLogout: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Mi Perfil", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Box(
                Modifier.makiShadow(20.dp, 4.dp).size(40.dp).clip(CircleShape).background(MakiColors.Surface)
                    .clickable { onInfo("Editar perfil estará disponible pronto.") },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Settings, "Editar perfil", tint = MakiColors.Text, modifier = Modifier.size(20.dp)) }
        }

        // Profile block
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.makiShadow(40.dp, 8.dp, Color(0x400F6E56)).size(80.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(MakiColors.Gen, MakiColors.GenDark))),
                contentAlignment = Alignment.Center,
            ) { Text(state.initials, color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp) }
            Text(state.displayName, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, tint = MakiColors.Text2, modifier = Modifier.size(14.dp))
                Text("Jr. Las Flores 123, SJL", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }

        // Stats grid
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBadgeCard(Modifier.weight(1f), Icons.Filled.LocalFireDepartment, MakiColors.Streak, MakiColors.StreakTint, "${state.streakDays} días", "Racha")
            StatBadgeCard(Modifier.weight(1f), Icons.Filled.MonetizationOn, MakiColors.Money, MakiColors.MoneyTint, "%,d".format(state.points), "Eco-Puntos")
            StatBadgeCard(Modifier.weight(1f), Icons.Filled.AccountBalanceWallet, MakiColors.Gen, MakiColors.GenTint, state.cashEarned, "Ganados")
        }

        // Environmental impact
        Column(
            Modifier.makiShadow(18.dp, 5.dp).clip(RoundedCornerShape(18.dp)).background(MakiColors.Surface).fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Grass, null, tint = MakiColors.Success, modifier = Modifier.size(20.dp))
                Text("Mi Impacto Ambiental", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ImpactMetric(Modifier.weight(1f), state.co2, MakiColors.Success, "CO₂ evitado")
                ImpactMetric(Modifier.weight(1f), state.recycled, MakiColors.Gen, "Reciclado")
                ImpactMetric(Modifier.weight(1f), state.water, MakiColors.Rider, "Agua")
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x121E8E4F)).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Park, null, tint = MakiColors.Success, modifier = Modifier.size(17.dp))
                Text("Equivalente a plantar 2 árboles", color = Color(0xFF15703E), fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        SectionLabel("LOGROS")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AchievementBadge(Modifier.weight(1f), Icons.Filled.VerifiedUser, MakiColors.Gen, "Defensor")
            AchievementBadge(Modifier.weight(1f), Icons.Filled.Scale, MakiColors.Success, "10 kg")
            AchievementBadge(Modifier.weight(1f), Icons.Filled.LocalShipping, MakiColors.Rider, "1er Recojo")
            AchievementBadge(Modifier.weight(1f), Icons.Filled.Lock, Color(0xFFA6B0AB), "Bloqueado", locked = true)
        }

        SecondaryButton("Ver insignia 3D", Modifier.fillMaxWidth(), icon = Icons.Filled.ViewInAr,
            onClick = { onInfo("La insignia 3D estará disponible pronto.") })

        // Account menu — centralised here for order (moved out of the home header).
        SectionLabel("CUENTA")
        Column(Modifier.makiShadow(18.dp, 5.dp).clip(RoundedCornerShape(18.dp)).background(MakiColors.Surface).fillMaxWidth()) {
            ProfileMenuRow(Icons.Filled.AccountBalanceWallet, MakiColors.Money, MakiColors.MoneyTint, "Mi Wallet") { onNavigate(GenRoutes.WALLET) }
            MenuDivider()
            ProfileMenuRow(Icons.Filled.History, MakiColors.Gen, MakiColors.GenTint, "Mi Historial") { onNavigate(GenRoutes.HISTORY) }
            MenuDivider()
            ProfileMenuRow(Icons.Filled.EmojiEvents, MakiColors.Money, MakiColors.MoneyTint, "Rankings") { onNavigate(GenRoutes.RANKINGS) }
            MenuDivider()
            ProfileMenuRow(Icons.Filled.Notifications, MakiColors.Rider, MakiColors.RiderTint, "Notificaciones") { onNavigate(GenRoutes.NOTIFICATIONS) }
            MenuDivider()
            ProfileMenuRow(Icons.AutoMirrored.Filled.Logout, MakiColors.Error, Color(0x14D32F2F), "Cerrar sesión", danger = true) { onLogout() }
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    tint: Color,
    bg: Color,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, bg, boxSize = 36.dp, iconSize = 19.dp, radius = 12.dp)
        Text(
            label, modifier = Modifier.weight(1f),
            color = if (danger) MakiColors.Error else MakiColors.Text,
            fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
        )
        if (!danger) Icon(Icons.Filled.ChevronRight, null, tint = MakiColors.Text2, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MenuDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(Color(0x0F000000)))
}

@Composable
private fun StatBadgeCard(modifier: Modifier, icon: ImageVector, tint: Color, bg: Color, value: String, label: String) {
    Column(
        modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        IconBadge(icon, tint, bg, boxSize = 34.dp, iconSize = 18.dp, radius = 17.dp)
        Text(value, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    }
}

@Composable
private fun ImpactMetric(modifier: Modifier, value: String, valueColor: Color, label: String) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(MakiColors.Bg).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = valueColor, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AchievementBadge(modifier: Modifier, icon: ImageVector, color: Color, label: String, locked: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            Modifier
                .then(if (!locked) Modifier.makiShadow(30.dp, 8.dp, Color(0x33000000)) else Modifier)
                .size(60.dp).clip(CircleShape)
                .background(if (locked) Color(0xFFE7EBE9) else color),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (locked) Color(0xFFA6B0AB) else Color.White, modifier = Modifier.size(26.dp)) }
        Text(label, color = if (locked) MakiColors.Text2 else MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun ProfilePreview() {
    MAKITheme { GeneratorProfileScreen() }
}
