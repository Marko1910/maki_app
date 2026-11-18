package pe.ruma.app.ui.screens.generator

import pe.ruma.app.ui.components.rumaCard
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.ruma.app.navigation.GenRoutes
import pe.ruma.app.ui.components.MetricRow
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.components.MetricTile
import pe.ruma.app.ui.components.NavRow
import pe.ruma.app.ui.components.RumiAvatar
import pe.ruma.app.ui.components.SecondaryButton
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.components.rumaShadow
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaElevation
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

/**
 * The household's own page, built on the same shape as the rider's: the person in the
 * gradient, the totals as tiles below. Two roles whose profile reads differently is how
 * an app starts to feel like two apps.
 */
@Composable
fun GeneratorProfileScreen(
    state: GeneratorUiState = GeneratorUiState(),
    onNavigate: (String) -> Unit = {},
    onInfo: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    goal: Int = 1,
    onGoalChange: (Int) -> Unit = {},
    /** The primary address; blank hides the line rather than showing someone else's. */
    address: String = "",
) {
    Column(
        Modifier.fillMaxSize().rumaScreen().statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = RumaSpace.gutter, end = RumaSpace.gutter,
                top = RumaSpace.sm, bottom = RumaSpace.xl,
            ),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mi perfil", color = RumaColors.Text, style = RumaType.screenTitle)
            Box(
                Modifier.rumaShadow(RumaSpace.xl, RumaElevation.card).size(44.dp)
                    .clip(CircleShape).background(RumaColors.Surface)
                    .clickable(
                        onClickLabel = "Editar perfil", role = Role.Button,
                        onClick = onEditProfile,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Settings, null,
                    tint = RumaColors.Text, modifier = Modifier.size(20.dp),
                )
            }
        }

        Identidad(state, address)

        SectionLabel("TU CUENTA EN NÚMEROS")
        MetricRow {
            MetricTile(
                Icons.Filled.LocalFireDepartment, "${state.streakDays} días", "Racha",
                RumaColors.Streak, Modifier.weight(1f),
            )
            MetricTile(
                Icons.Filled.MonetizationOn, "%,d".format(state.points), "Eco-Puntos",
                RumaColors.Money, Modifier.weight(1f),
            )
            MetricTile(
                Icons.Filled.AccountBalanceWallet, state.cashEarned, "Ganados",
                RumaColors.Gen, Modifier.weight(1f),
            )
        }

        SectionLabel("TU IMPACTO AMBIENTAL")
        SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.md, padding = RumaSpace.lg) {
            Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                Impacto(Modifier.weight(1f), state.co2, RumaColors.Success, "CO₂ evitado")
                Impacto(Modifier.weight(1f), state.recycled, RumaColors.Gen, "Reciclado")
                Impacto(Modifier.weight(1f), state.water, RumaColors.Rider, "Agua")
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(RumaRadius.badge))
                    .background(RumaColors.SuccessTint)
                    .padding(horizontal = RumaSpace.md, vertical = RumaSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Park, null,
                    tint = RumaColors.Success, modifier = Modifier.size(17.dp),
                )
                Text(
                    "Equivalente a plantar 2 árboles",
                    color = RumaColors.Success, style = RumaType.label,
                )
            }
        }

        SectionLabel("LOGROS")
        Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
            Logro(Modifier.weight(1f), Icons.Filled.VerifiedUser, RumaColors.Gen, "Defensor")
            Logro(Modifier.weight(1f), Icons.Filled.Scale, RumaColors.Success, "10 kg")
            Logro(Modifier.weight(1f), Icons.Filled.LocalShipping, RumaColors.Rider, "1er recojo")
            Logro(Modifier.weight(1f), Icons.Filled.Lock, RumaColors.Text3, "Bloqueado", locked = true)
        }
        SecondaryButton(
            "Ver insignia 3D", Modifier.fillMaxWidth(), icon = Icons.Filled.ViewInAr,
            onClick = { onInfo("La insignia 3D estará disponible pronto.") },
        )

        WeeklyGoalButton(goal, onGoalChange)

        // Account actions live here by design: the home screen has no hamburger.
        SectionLabel("CUENTA")
        NavRow(
            Icons.Filled.AccountBalanceWallet, "Mi billetera",
            "Puntos, canjes y descuento de arbitrios", RumaColors.Money,
        ) { onNavigate(GenRoutes.WALLET) }
        NavRow(
            Icons.Filled.History, "Mi historial",
            "Tus escaneos y recojos, uno por uno", RumaColors.Gen,
        ) { onNavigate(GenRoutes.HISTORY) }
        NavRow(
            Icons.Filled.CardGiftcard, "Invita y gana",
            "Puntos para ti y tu vecino cuando pesen su primer reciclaje", RumaColors.Gen,
        ) { onNavigate(GenRoutes.INVITE) }
        NavRow(
            Icons.Filled.WorkspacePremium, "Ruma Plus",
            "Tu plan, la prueba gratis y lo que incluye", RumaColors.Money,
        ) { onNavigate(GenRoutes.PLANS) }
        NavRow(
            Icons.Filled.EmojiEvents, "Rankings",
            "Cómo va tu barrio frente a los demás", RumaColors.Money,
        ) { onNavigate(GenRoutes.RANKINGS) }
        NavRow(
            Icons.Filled.Notifications, "Notificaciones",
            "Avisos de tus recojos y de tus puntos", RumaColors.Rider,
        ) { onNavigate(GenRoutes.NOTIFICATIONS) }
        NavRow(
            Icons.AutoMirrored.Filled.Chat, "Pregúntale a Rumi",
            "Qué va en cada tacho y cuánto vale tu material", RumaColors.Gen,
            leading = { RumiAvatar() },
        ) { onNavigate(GenRoutes.CHAT) }

        CerrarSesion(onLogout)
    }
}

@Composable
private fun Identidad(state: GeneratorUiState, address: String) {
    Column(
        Modifier.rumaHero(RumaGradient.gen, glow = RumaGradient.glowOf(RumaColors.Gen))
            .fillMaxWidth().padding(RumaSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RumaSpace.xs),
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(RumaColors.WhiteTint),
            contentAlignment = Alignment.Center,
        ) {
            Text(state.initials, color = RumaColors.OnDark, style = RumaType.metricLarge)
        }
        Text(state.displayName, color = RumaColors.OnDark, style = RumaType.metricLarge)
        if (address.isNotBlank()) Row(
            horizontalArrangement = Arrangement.spacedBy(RumaSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Place, null,
                tint = RumaColors.OnGenSoft, modifier = Modifier.size(14.dp),
            )
            Text(address, color = RumaColors.OnGenSoft, style = RumaType.bodySmall)
        }
    }
}

@Composable
private fun Impacto(modifier: Modifier, value: String, valueColor: Color, label: String) {
    Column(
        modifier.clip(RoundedCornerShape(RumaRadius.badge)).background(RumaColors.SurfaceSoft)
            .padding(vertical = RumaSpace.md, horizontal = RumaSpace.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RumaSpace.xs / 2),
    ) {
        Text(value, color = valueColor, style = RumaType.metricMedium)
        Text(
            label, color = RumaColors.Text2, style = RumaType.caption,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Logro(
    modifier: Modifier,
    icon: ImageVector,
    color: Color,
    label: String,
    locked: Boolean = false,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        Box(
            Modifier
                .then(
                    if (locked) Modifier
                    else Modifier.rumaShadow(30.dp, RumaElevation.hero, RumaGradient.glowOf(color)),
                )
                .size(58.dp).clip(CircleShape)
                .background(if (locked) RumaColors.Border else color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, null,
                tint = if (locked) RumaColors.Text3 else RumaColors.OnDark,
                modifier = Modifier.size(25.dp),
            )
        }
        Text(
            label,
            color = if (locked) RumaColors.Text2 else RumaColors.Text,
            style = RumaType.caption, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CerrarSesion(onLogout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = RumaSpace.sm)
            .clip(RoundedCornerShape(RumaRadius.badge))
            .background(RumaColors.ErrorTint)
            .clickable(onClickLabel = "Cerrar sesión", role = Role.Button, onClick = onLogout)
            .heightIn(min = 48.dp)
            .padding(vertical = RumaSpace.md),
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Logout, null,
            tint = RumaColors.Error, modifier = Modifier.size(18.dp),
        )
        Text("Cerrar sesión", color = RumaColors.Error, style = RumaType.button)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1200)
@Composable
private fun ProfilePreview() {
    RumaTheme { GeneratorProfileScreen() }
}

/** The weekly goal, moved here from the home: a button that opens the question and the seven days. */
@Composable
private fun WeeklyGoalButton(goal: Int, onGoalChange: (Int) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().rumaCard(), verticalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
        NavRow(Icons.Filled.EmojiEvents, "Meta semanal", "Participar $goal ${if (goal == 1) "día" else "días"} por semana", framed = false, onClick = { editing = !editing })
        if (editing) Column(Modifier.padding(start = RumaSpace.lg, end = RumaSpace.lg, bottom = RumaSpace.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                        Text("¿Cuántos días por semana quieres registrar residuos?", style = RumaType.bodySmall, color = RumaColors.Text2)
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                            (1..7).forEach { days ->
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                        .background(if (days == goal) RumaColors.Gen else RumaColors.SurfaceGlass)
                                        .selectable(selected = days == goal, role = Role.RadioButton, onClick = { onGoalChange(days); editing = false })
                                        .semantics { contentDescription = "$days ${if (days == 1) "día" else "días"}" },
                                    contentAlignment = Alignment.Center,
                                ) { Text("$days", style = RumaType.button, color = if (days == goal) RumaColors.OnDark else RumaColors.Text) }
                            }
                        }
                    }
        }
    }
}
