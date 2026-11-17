package pe.ruma.app.ui.screens.generator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaLoading
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.TextAction
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace

private val SampleNotifs = listOf(
    NotifUi("streak_reminder", "¡Racha de 21 días!", "No pierdas tu racha, reporta hoy tus residuos.", "Hace 2h", unread = true, highlighted = true),
    NotifUi("ranking_up", "Subiste al #2 de tu cuadra", "¡Súper! Sigue así para alcanzar el 1er lugar.", "Ayer", unread = false, highlighted = false),
    NotifUi("rider_arriving", "Carlos (Eco-Rider) llegando", "Tu recojo llega en ~15 min. Ten listos tus residuos.", "Hoy 9:45am", unread = false, highlighted = false),
)

/**
 * The family's notices as one grouped list (PWA `Notifications`): an unread row
 * carries the role tint, and "Marcar leídas" sits in the header while any is unread.
 */
@Composable
fun GeneratorNotificationsScreen(
    items: List<NotifUi>? = null,
    onBack: () -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onCardClick: (Long) -> Unit = {},
) {
    // Sample only for @Preview: on a device a made-up "gana 250 pts" is a promise nobody keeps.
    val list = items ?: SampleNotifs.takeIf { androidx.compose.ui.platform.LocalInspectionMode.current }
    Column(Modifier.fillMaxSize().rumaScreen().statusBarsPadding()) {
        DetailHeader(
            "Notificaciones", onBack,
            trailing = if (list?.any { it.unread } == true) {
                { TextAction("Marcar leídas", onClickLabel = "Marcar todas como leídas", onClick = onMarkAllRead) }
            } else null,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            when {
                list == null -> RumaLoading("Cargando notificaciones…")
                list.isEmpty() -> RumaEmpty(Icons.Filled.Notifications, "Sin notificaciones", "Aquí te avisaremos de tus recojos, puntos y ganancias.", accent = RumaColors.Gen)
                else -> RumaList(list) { n -> NotifRow(n) { onCardClick(n.id) } }
            }
        }
    }
}

private fun notifIcon(kind: String): ImageVector = when (kind) {
    "streak_reminder" -> Icons.Filled.LocalFireDepartment
    "ranking_up", "leaderboard", "points_earned" -> Icons.Filled.EmojiEvents
    "rider_arriving", "rider_assigned", "pickup_completed" -> Icons.AutoMirrored.Filled.DirectionsBike
    else -> Icons.Outlined.Notifications
}

@Composable
private fun NotifRow(n: NotifUi, onClick: () -> Unit) {
    RumaRow(
        n.title,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = (if (n.unread) "No leída. " else "") + n.title + ". " + n.body + ". " + n.time
        },
        leading = { GlassBadge(icon = notifIcon(n.kind)) },
        subtitle = n.body.takeIf { it.isNotBlank() },
        meta = n.time,
        highlight = if (n.unread || n.highlighted) RumaColors.Gen else null,
        onClick = onClick,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NotificationsPreview() {
    RumaTheme { GeneratorNotificationsScreen() }
}
