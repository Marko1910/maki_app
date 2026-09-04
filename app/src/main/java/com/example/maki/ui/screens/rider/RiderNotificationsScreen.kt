package com.example.maki.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MakiLoading

data class RiderNotifUi(
    val id: Long,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBg: Color,
    val title: String,
    val body: String,
    val time: String,
    val action: String? = null,
    val unread: Boolean = false,
)

private val UnreadBg = Color(0x0D185FA5)

/**
 * The rider's notifications straight from `notifications`. Read state is now
 * server-backed (it used to be a local flag that forgot itself on every restart):
 * tapping a card marks that row read, and "Marcar leídas" patches them all.
 */
@Composable
fun RiderNotificationsScreen(
    items: List<RiderNotifUi>? = null,
    error: String? = null,
    onBack: () -> Unit = {},
    onRead: (Long) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val hasUnread = items?.any { it.unread } == true

    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()) {
        DetailHeader(
            title = "Notificaciones",
            onBack = onBack,
            trailing = {
                if (hasUnread) {
                    Text(
                        "Marcar leídas",
                        color = MakiColors.Rider, fontFamily = MakiFont,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onMarkAllRead)
                            // 44dp target: the label alone is ~18dp tall.
                            .padding(horizontal = 10.dp, vertical = 13.dp)
                            .semantics { contentDescription = "Marcar todas las notificaciones como leídas" },
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                error != null -> item { MakiError(error, onRetry) }
                items == null -> item { MakiLoading("Cargando notificaciones…") }
                items.isEmpty() -> item {
                    MakiEmpty(
                        icon = Icons.Filled.NotificationsNone,
                        title = "Sin notificaciones",
                        hint = "Aquí te avisaremos de nuevas asignaciones, ganancias y cambios en tu ranking.",
                    )
                }
                else -> items(items, key = { it.id }) { n ->
                    NotifCard(n, n.unread, onClick = { if (n.unread) onRead(n.id) })
                }
            }
        }
    }
}

@Composable
private fun NotifCard(n: RiderNotifUi, unread: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.makiShadow(14.dp, 4.dp, Color(0x0A1A1A1A)).clip(RoundedCornerShape(14.dp))
            .background(if (unread) UnreadBg else MakiColors.Surface).fillMaxWidth().height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = (if (unread) "No leída. " else "") + n.title + ". " + n.body + ". " + n.time
            },
    ) {
        if (unread) Box(Modifier.width(4.dp).fillMaxHeight().background(MakiColors.Rider))
        Row(Modifier.weight(1f).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(n.iconBg),
                contentAlignment = Alignment.Center,
            ) { Icon(n.icon, null, tint = n.iconTint, modifier = Modifier.size(21.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.title, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (unread) Box(Modifier.size(9.dp).clip(CircleShape).background(MakiColors.Rider))
                }
                Text(n.body, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.time, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    n.action?.let {
                        Text(it, color = MakiColors.Rider, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun RiderNotificationsPreview() {
    MAKITheme {
        RiderNotificationsScreen(
            items = listOf(
                RiderNotifUi(
                    1L, Icons.AutoMirrored.Filled.DirectionsBike, MakiColors.Rider, MakiColors.RiderTint,
                    "Nueva asignación de recojo", "Fam. Torres · Jr. Las Flores 98", "Hace 5 min", unread = true,
                ),
            ),
        )
    }
}
