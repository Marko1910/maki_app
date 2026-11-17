package pe.ruma.app.ui.screens.rider

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
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.components.rumaShadow
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaType
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaSkeleton

import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.TextAction
import pe.ruma.app.ui.theme.RumaSpace

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
    /** About a pickup (assigned, cancelled…): tapping it opens the route. */
    val aboutPickup: Boolean = false,
)

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

    Column(Modifier.fillMaxSize().rumaScreen(RumaColors.Rider).statusBarsPadding()) {
        DetailHeader(
            title = "Notificaciones",
            onBack = onBack,
            trailing = if (hasUnread) {
                { TextAction("Marcar leídas", RumaColors.Rider, "Marcar todas las notificaciones como leídas", onMarkAllRead) }
            } else null,
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            when {
                error != null -> item { RumaError(error, onRetry) }
                items == null -> item { RumaSkeleton(listOf(96.dp, 72.dp, 72.dp), label = "Cargando notificaciones…") }
                items.isEmpty() -> item {
                    RumaEmpty(
                        icon = Icons.Filled.NotificationsNone,
                        title = "Sin notificaciones",
                        hint = "Aquí te avisaremos de nuevas asignaciones, ganancias y cambios en tu ranking.",
                    )
                }
                // Always forwarded: a read pickup notice still opens the route.
                else -> item { RumaList(items) { n -> NotifRow(n, onClick = { onRead(n.id) }) } }
            }
        }
    }
}

/** One notice as a row of the grouped list (PWA `Notifications`); unread wears the tint. */
@Composable
private fun NotifRow(n: RiderNotifUi, onClick: () -> Unit) {
    RumaRow(
        n.title,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = (if (n.unread) "No leída. " else "") + n.title + ". " + n.body + ". " + n.time
        },
        subtitle = n.body.takeIf { it.isNotBlank() },
        meta = n.time,
        leading = { GlassBadge(icon = n.icon) },
        highlight = if (n.unread) RumaColors.Rider else null,
        extra = n.action?.let { a -> { Text(a, color = RumaColors.Rider, style = RumaType.chip) } },
        onClick = onClick,
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun RiderNotificationsPreview() {
    RumaTheme {
        RiderNotificationsScreen(
            items = listOf(
                RiderNotifUi(
                    1L, Icons.AutoMirrored.Filled.DirectionsBike, RumaColors.Rider, RumaColors.RiderTint,
                    "Nueva asignación de recojo", "Fam. Torres · Jr. Las Flores 98", "Hace 5 min", unread = true,
                ),
            ),
        )
    }
}
