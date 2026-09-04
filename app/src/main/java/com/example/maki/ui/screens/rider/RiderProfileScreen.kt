package com.example.maki.ui.screens.rider

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Settings
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
import com.example.maki.navigation.RiderRoutes
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

// Sample state mirroring the STAGING seed data for the rider Carlos.
data class RiderProfileUiState(
    val name: String = "Carlos Mendoza",
    val roleZone: String = "Eco-Rider · SJL Norte",
    val vehicle: String = "Moto",
    val rating: String = "4.8",
    val riderId: String = "ID: MAKI-R-0042",
    val collections: String = "340",
    val recycled: String = "1,240 kg",
    val co2: String = "312 kg",
)

private val SampleRiderProfile = RiderProfileUiState()
private val BadgeTint = Color(0x0D185FA5)   // ~5% rider tint used on profile badges/chips
private val StarTint = Color(0x0FBA7517)
private val LogoutTint = Color(0x0DD32F2F)

@Composable
fun RiderProfileScreen(
    state: RiderProfileUiState = SampleRiderProfile,
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onInfo: (String) -> Unit = {},
    onLogout: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.makiShadow(20.dp, 4.dp).size(40.dp).clip(CircleShape).background(MakiColors.Surface).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = MakiColors.Text, modifier = Modifier.size(20.dp)) }
            Text("Mi Perfil", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }

        // Profile block
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.makiShadow(42.dp, 8.dp, Color(0x40185FA5)).size(84.dp).clip(CircleShape).background(MakiColors.Rider),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            Text(state.name, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Text(state.roleZone, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MetaChip(Icons.AutoMirrored.Filled.DirectionsBike, MakiColors.Rider, BadgeTint, state.vehicle)
                MetaChip(Icons.Filled.Star, MakiColors.Money, StarTint, state.rating)
                Text(state.riderId, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }

        // Stats
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), state.collections, "recolecciones")
            StatCard(Modifier.weight(1f), state.recycled, "reciclado")
            StatCard(Modifier.weight(1f), state.co2, "CO₂ evitado")
        }

        // Quick access menu
        Column(
            Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface).fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        ) {
            MenuRow(Icons.Filled.AccountBalanceWallet, MakiColors.Money, "Mis Ganancias") { onNavigate(RiderRoutes.EARNINGS) }
            RowDivider()
            MenuRow(Icons.Filled.BarChart, MakiColors.Rider, "Mis Estadísticas") { onNavigate(RiderRoutes.STATS) }
            RowDivider()
            MenuRow(Icons.Filled.EmojiEvents, MakiColors.Money, "Ranking") { onNavigate(RiderRoutes.RANKING) }
            RowDivider()
            MenuRow(Icons.AutoMirrored.Filled.Assignment, MakiColors.Rider, "Historial de recojos") { onNavigate(RiderRoutes.HISTORY) }
            RowDivider()
            MenuRow(Icons.Outlined.Settings, MakiColors.Text2, "Configuración") { onInfo("Configuración estará disponible pronto.") }
        }

        // Logout
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LogoutTint).clickable(onClick = onLogout).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MakiColors.Error, modifier = Modifier.size(18.dp))
            Text("Cerrar sesión", color = MakiColors.Error, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MetaChip(icon: ImageVector, tint: Color, bg: Color, label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(start = 9.dp, top = 4.dp, end = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Text(label, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String) {
    Column(
        modifier.makiShadow(14.dp, 4.dp).clip(RoundedCornerShape(14.dp)).background(MakiColors.Surface).padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, color = MakiColors.Rider, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(label, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(BadgeTint),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
        Text(label, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = MakiColors.Text2, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MakiColors.Border))
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun RiderProfilePreview() {
    MAKITheme { RiderProfileScreen() }
}
