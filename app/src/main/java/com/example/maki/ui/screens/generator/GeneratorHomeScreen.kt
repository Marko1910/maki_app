package com.example.maki.ui.screens.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.example.maki.navigation.GenRoutes
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.HeroCard
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.MetricTile
import com.example.maki.ui.components.NavRow
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.StatusChip
import com.example.maki.ui.components.makiCard
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

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
    val riderTitle: String = "Carlos va en camino",
    val challengeLabel: String = "Gran Aplastada",
    /** Collected but not yet weighed at the acopio — shown so earned points never "vanish". */
    val pendingPoints: Int = 98,
)

@Composable
fun GeneratorHomeScreen(
    state: HomeUiState = HomeUiState(),
    onNavigate: (String) -> Unit = {},
) {
    LazyColumn(
        Modifier.fillMaxSize().makiScreen().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = MakiSpace.gutter, vertical = MakiSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
    ) {
        item { HomeHeader(state, onBell = { onNavigate(GenRoutes.NOTIFICATIONS) }) }
        item {
            HeroCard(
                accent = MakiColors.Gen,
                overline = "ECO-PUNTOS",
                metric = "%,d".format(state.ecoPoints),
                support = if (state.pendingPoints > 0)
                    "Eco-Puntos · +${"%,d".format(state.pendingPoints)} por confirmar" else "Eco-Puntos",
                modifier = Modifier.clickable(
                    onClickLabel = "Ir a Mi Wallet", role = Role.Button,
                    onClick = { onNavigate(GenRoutes.WALLET) },
                ),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = MakiColors.OnMoneySoft, modifier = Modifier.size(MakiSpace.lg))
                    Text("Día ${state.streakDay} · Racha activa", color = MakiColors.OnDark, style = MakiType.label)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AccountBalanceWallet, null, tint = MakiColors.OnMoneySoft, modifier = Modifier.size(MakiSpace.lg))
                    Text("= ${state.moneyValue}", color = MakiColors.OnDark, style = MakiType.rowTitle)
                }
            }
        }
        item { SectionLabel("Tu impacto") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.stack)) {
                MetricTile(
                    Icons.Filled.EmojiEvents, state.blockRank, "Tu cuadra", MakiColors.Money,
                    Modifier.weight(1f), onClick = { onNavigate(GenRoutes.RANKINGS) },
                )
                MetricTile(
                    Icons.Filled.Inventory2, "${state.challengePct}%", state.challengeLabel,
                    MakiColors.Gen, Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.stack)) {
                MetricTile(Icons.Filled.Eco, state.co2Avoided, "CO₂ evitado", MakiColors.Success, Modifier.weight(1f))
                MetricTile(
                    Icons.Filled.LocalShipping, state.nextPickup, "Mis recojos", MakiColors.Rider,
                    Modifier.weight(1f), onClick = { onNavigate(GenRoutes.HISTORY) },
                )
            }
        }
        item { SectionLabel("Recicla") }
        item {
            NavRow(
                Icons.Outlined.PhotoCamera, "Escanear residuos", "La cámara los cuenta y los valora",
                onClick = { onNavigate(GenRoutes.CAMERA) },
            )
        }
        item {
            NavRow(
                Icons.AutoMirrored.Filled.DirectionsBike, "Pedir recojo", "Un Eco-Rider pasa por tus residuos",
                accent = MakiColors.Rider, onClick = { onNavigate(GenRoutes.PICKUP) },
            )
        }
        item {
            NavRow(
                Icons.Filled.Place, "Puntos de acopio cerca", "Mira dónde reciclar y los precios",
                onClick = { onNavigate(GenRoutes.CENTERS) },
            )
        }
        if (state.riderIncoming) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(MakiSpace.sm)) {
                    StatusChip("En vivo", MakiColors.Success)
                    NavRow(
                        Icons.AutoMirrored.Filled.DirectionsBike, state.riderTitle, state.riderEtaText,
                        accent = MakiColors.Rider, onClick = { onNavigate(GenRoutes.TRACKING) },
                    )
                }
            }
        }
        item {
            // La recomendacion es el principio de una conversacion, no un cartel:
            // tocarla abre el asistente, que es donde el chat vive ahora.
            Row(
                Modifier.fillMaxWidth().makiCard()
                    .clickable(
                        onClickLabel = "Preguntarle a Ruma",
                        role = Role.Button,
                        onClick = { onNavigate(GenRoutes.CHAT) },
                    )
                    .padding(MakiSpace.lg),
                horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Outlined.Lightbulb, MakiColors.Gen, MakiColors.GenTint)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs)) {
                    Text("Ruma te recomienda", color = MakiColors.Gen, style = MakiType.cardTitle)
                    Text(state.recommendation, color = MakiColors.Text, style = MakiType.body)
                    Text(
                        "Pregúntale lo que quieras ›",
                        color = MakiColors.Gen, style = MakiType.label,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, onBell: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs)) {
            Text((if (state.displayName.isBlank()) "Hola" else "Hola, ${state.displayName}"), color = MakiColors.Text, style = MakiType.screenTitle)
            Text(state.greetingPhrase, color = MakiColors.Text2, style = MakiType.bodySmall)
        }
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier.size(MakiSpace.xl * 2 + MakiSpace.sm).makiCard(MakiRadius.pill)
                    .clickable(onClickLabel = "Ver notificaciones", role = Role.Button, onClick = onBell),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Notifications,
                    if (state.hasUnreadNotifications) "Ver notificaciones sin leer" else "Ver notificaciones",
                    tint = MakiColors.Text, modifier = Modifier.size(MakiSpace.xl),
                )
            }
            if (state.hasUnreadNotifications) {
                Box(
                    Modifier.padding(top = MakiSpace.sm, end = MakiSpace.xs)
                        .size(MakiSpace.sm).clip(CircleShape).background(MakiColors.Streak),
                )
            }
        }
    }
}

private val SampleHome = HomeUiState()

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun GeneratorHomePreview() {
    MAKITheme { GeneratorHomeScreen(state = SampleHome) }
}
