package com.example.maki.ui.screens.generator

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
import com.example.maki.navigation.GenRoutes
import com.example.maki.ui.components.MetricRow
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.MetricTile
import com.example.maki.ui.components.NavRow
import com.example.maki.ui.components.SecondaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.SoftCard
import com.example.maki.ui.components.makiHero
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiElevation
import com.example.maki.ui.theme.MakiGradient
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

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
) {
    Column(
        Modifier.fillMaxSize().makiScreen().statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = MakiSpace.gutter, end = MakiSpace.gutter,
                top = MakiSpace.sm, bottom = MakiSpace.xl,
            ),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mi perfil", color = MakiColors.Text, style = MakiType.screenTitle)
            Box(
                Modifier.makiShadow(MakiSpace.xl, MakiElevation.card).size(44.dp)
                    .clip(CircleShape).background(MakiColors.Surface)
                    .clickable(
                        onClickLabel = "Editar perfil", role = Role.Button,
                        onClick = onEditProfile,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Settings, null,
                    tint = MakiColors.Text, modifier = Modifier.size(20.dp),
                )
            }
        }

        Identidad(state)

        SectionLabel("TU CUENTA EN NÚMEROS")
        MetricRow {
            MetricTile(
                Icons.Filled.LocalFireDepartment, "${state.streakDays} días", "Racha",
                MakiColors.Streak, Modifier.weight(1f),
            )
            MetricTile(
                Icons.Filled.MonetizationOn, "%,d".format(state.points), "Eco-Puntos",
                MakiColors.Money, Modifier.weight(1f),
            )
            MetricTile(
                Icons.Filled.AccountBalanceWallet, state.cashEarned, "Ganados",
                MakiColors.Gen, Modifier.weight(1f),
            )
        }

        SectionLabel("TU IMPACTO AMBIENTAL")
        SoftCard(Modifier.fillMaxWidth(), gap = MakiSpace.md, padding = MakiSpace.lg) {
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm)) {
                Impacto(Modifier.weight(1f), state.co2, MakiColors.Success, "CO₂ evitado")
                Impacto(Modifier.weight(1f), state.recycled, MakiColors.Gen, "Reciclado")
                Impacto(Modifier.weight(1f), state.water, MakiColors.Rider, "Agua")
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(MakiRadius.badge))
                    .background(MakiColors.SuccessTint)
                    .padding(horizontal = MakiSpace.md, vertical = MakiSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Park, null,
                    tint = MakiColors.Success, modifier = Modifier.size(17.dp),
                )
                Text(
                    "Equivalente a plantar 2 árboles",
                    color = MakiColors.Success, style = MakiType.label,
                )
            }
        }

        SectionLabel("LOGROS")
        Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm)) {
            Logro(Modifier.weight(1f), Icons.Filled.VerifiedUser, MakiColors.Gen, "Defensor")
            Logro(Modifier.weight(1f), Icons.Filled.Scale, MakiColors.Success, "10 kg")
            Logro(Modifier.weight(1f), Icons.Filled.LocalShipping, MakiColors.Rider, "1er recojo")
            Logro(Modifier.weight(1f), Icons.Filled.Lock, MakiColors.Text3, "Bloqueado", locked = true)
        }
        SecondaryButton(
            "Ver insignia 3D", Modifier.fillMaxWidth(), icon = Icons.Filled.ViewInAr,
            onClick = { onInfo("La insignia 3D estará disponible pronto.") },
        )

        // Account actions live here by design: the home screen has no hamburger.
        SectionLabel("CUENTA")
        NavRow(
            Icons.Filled.AccountBalanceWallet, "Mi billetera",
            "Puntos, canjes y descuento de arbitrios", MakiColors.Money,
        ) { onNavigate(GenRoutes.WALLET) }
        NavRow(
            Icons.Filled.History, "Mi historial",
            "Tus escaneos y recojos, uno por uno", MakiColors.Gen,
        ) { onNavigate(GenRoutes.HISTORY) }
        NavRow(
            Icons.Filled.EmojiEvents, "Rankings",
            "Cómo va tu barrio frente a los demás", MakiColors.Money,
        ) { onNavigate(GenRoutes.RANKINGS) }
        NavRow(
            Icons.Filled.Notifications, "Notificaciones",
            "Avisos de tus recojos y de tus puntos", MakiColors.Rider,
        ) { onNavigate(GenRoutes.NOTIFICATIONS) }
        NavRow(
            Icons.AutoMirrored.Filled.Chat, "Pregúntale a Ruma",
            "Qué va en cada tacho y cuánto vale tu material", MakiColors.Gen,
        ) { onNavigate(GenRoutes.CHAT) }

        CerrarSesion(onLogout)
    }
}

@Composable
private fun Identidad(state: GeneratorUiState) {
    Column(
        Modifier.makiHero(MakiGradient.gen, glow = MakiGradient.glowOf(MakiColors.Gen))
            .fillMaxWidth().padding(MakiSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MakiSpace.xs),
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(MakiColors.WhiteTint),
            contentAlignment = Alignment.Center,
        ) {
            Text(state.initials, color = MakiColors.OnDark, style = MakiType.metricLarge)
        }
        Text(state.displayName, color = MakiColors.OnDark, style = MakiType.metricLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(MakiSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Place, null,
                tint = MakiColors.OnGenSoft, modifier = Modifier.size(14.dp),
            )
            Text("Jr. Las Flores 123, SJL", color = MakiColors.OnGenSoft, style = MakiType.bodySmall)
        }
    }
}

@Composable
private fun Impacto(modifier: Modifier, value: String, valueColor: Color, label: String) {
    Column(
        modifier.clip(RoundedCornerShape(MakiRadius.badge)).background(MakiColors.SurfaceSoft)
            .padding(vertical = MakiSpace.md, horizontal = MakiSpace.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MakiSpace.xs / 2),
    ) {
        Text(value, color = valueColor, style = MakiType.metricMedium)
        Text(
            label, color = MakiColors.Text2, style = MakiType.caption,
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
        verticalArrangement = Arrangement.spacedBy(MakiSpace.sm),
    ) {
        Box(
            Modifier
                .then(
                    if (locked) Modifier
                    else Modifier.makiShadow(30.dp, MakiElevation.hero, MakiGradient.glowOf(color)),
                )
                .size(58.dp).clip(CircleShape)
                .background(if (locked) MakiColors.Border else color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, null,
                tint = if (locked) MakiColors.Text3 else MakiColors.OnDark,
                modifier = Modifier.size(25.dp),
            )
        }
        Text(
            label,
            color = if (locked) MakiColors.Text2 else MakiColors.Text,
            style = MakiType.caption, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CerrarSesion(onLogout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = MakiSpace.sm)
            .clip(RoundedCornerShape(MakiRadius.badge))
            .background(MakiColors.ErrorTint)
            .clickable(onClickLabel = "Cerrar sesión", role = Role.Button, onClick = onLogout)
            .heightIn(min = 48.dp)
            .padding(vertical = MakiSpace.md),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Logout, null,
            tint = MakiColors.Error, modifier = Modifier.size(18.dp),
        )
        Text("Cerrar sesión", color = MakiColors.Error, style = MakiType.button)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1200)
@Composable
private fun ProfilePreview() {
    MAKITheme { GeneratorProfileScreen() }
}
