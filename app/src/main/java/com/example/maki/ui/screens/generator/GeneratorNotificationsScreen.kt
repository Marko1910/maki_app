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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.BackButton
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

private val SampleNotifs = listOf(
    NotifUi("streak_reminder", "¡Racha de 21 días!", "No pierdas tu racha, reporta hoy tus residuos.", "Hace 2h", unread = true, highlighted = true),
    NotifUi("ranking_up", "Subiste al #2 de tu cuadra", "¡Súper! Sigue así para alcanzar el 1er lugar.", "Ayer", unread = false, highlighted = false),
    NotifUi("challenge", "Nuevo reto: Latas al Poder", "Disponible por 12 días. ¡Participa y gana 250 pts!", "Ayer", unread = false, highlighted = false),
    NotifUi("rider_arriving", "Carlos (Eco-Rider) llegando", "Tu recojo llega en ~15 min. Ten listos tus residuos.", "Hoy 9:45am", unread = false, highlighted = false),
)

@Composable
fun GeneratorNotificationsScreen(
    items: List<NotifUi>? = null,
    onBack: () -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onCardClick: (Long) -> Unit = {},
) {
    val list = items ?: SampleNotifs
    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Text("Notificaciones", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            if (list.any { it.unread }) {
                Text("Marcar leídas", color = MakiColors.Gen, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onMarkAllRead))
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            list.forEach { n ->
                val v = notifVisual(n.kind)
                NotifCard(v.icon, v.tint, v.bg, n.title, n.body, n.time, unread = n.unread, highlighted = n.highlighted,
                    onClick = { onCardClick(n.id) })
            }
        }
    }
}

private data class NotifVisual(val icon: ImageVector, val tint: Color, val bg: Color)

private fun notifVisual(kind: String): NotifVisual = when (kind) {
    "streak_reminder" -> NotifVisual(Icons.Filled.LocalFireDepartment, MakiColors.Streak, MakiColors.StreakTint)
    "ranking_up", "leaderboard" -> NotifVisual(Icons.Filled.EmojiEvents, MakiColors.Money, MakiColors.MoneyTint)
    "points_earned" -> NotifVisual(Icons.Filled.EmojiEvents, MakiColors.Money, MakiColors.MoneyTint)
    "rider_arriving", "rider_assigned", "pickup_completed" ->
        NotifVisual(Icons.AutoMirrored.Filled.DirectionsBike, MakiColors.Rider, MakiColors.RiderTint)
    else -> NotifVisual(Icons.Filled.Inventory2, MakiColors.Gen, MakiColors.GenTint)
}

@Composable
private fun NotifCard(icon: ImageVector, iconTint: Color, iconBg: Color, title: String, body: String, time: String, unread: Boolean = false, highlighted: Boolean = false, onClick: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().makiShadow(14.dp, 4.dp).clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) Color(0x0D0F6E56) else MakiColors.Surface)
            .then(if (highlighted) Modifier.border(1.dp, MakiColors.GenTintStrong, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        IconBadge(icon, iconTint, iconBg, boxSize = 42.dp, iconSize = 21.dp, radius = 21.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (unread) Box(Modifier.size(9.dp).clip(CircleShape).background(MakiColors.Gen))
            }
            Text(body, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
            Text(time, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NotificationsPreview() {
    MAKITheme { GeneratorNotificationsScreen() }
}
