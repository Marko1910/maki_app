package com.example.maki.ui.screens.rider

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Cloud
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
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Person
import com.example.maki.navigation.RiderRoutes
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MetricRow
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.MakiLoading
import com.example.maki.ui.components.MetricTile
import com.example.maki.ui.components.NavRow
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.makiHero
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiGradient
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

// Sample state mirroring the STAGING seed data for the rider Carlos.
data class RiderProfileUiState(
    val name: String = "Carlos Mendoza",
    val roleZone: String = "Eco-Rider · SJL Norte",
    val vehicle: String = "Moto",
    val rating: String = "4.8",
    val riderId: String = "RUMA-R-0042",
    val collections: String = "340",
    val recycled: String = "1,240 kg",
    val co2: String = "312 kg",
)

private val SampleRiderProfile = RiderProfileUiState()

/**
 * The rider's own page. Unlike every other screen here the protagonist is not a figure
 * but the person: the identity block carries the gradient, and the three totals sit
 * below it as supporting tiles. Putting "1,240 kg" in the hero would have made this a
 * stats screen, and the rider already has one of those.
 */
@Composable
fun RiderProfileScreen(
    state: RiderProfileUiState? = null,
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onInfo: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    error: String? = null,
    onRetry: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().makiScreen(MakiColors.Rider).statusBarsPadding()) {
        DetailHeader(title = "Mi perfil", onBack = onBack)
        if (state == null) {
            Column(
                Modifier.fillMaxSize()
                    .padding(horizontal = MakiSpace.gutter),
                verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
            ) {
                if (error != null) {
                    MakiError(error, onRetry, accent = MakiColors.Rider)
                } else {
                    MakiLoading("Cargando tu perfil…", accent = MakiColors.Rider)
                }
                // Cerrar sesion sigue a la vista: si el perfil no carga, salir de
                // la cuenta es justo lo que el usuario necesita poder hacer.
                CerrarSesion(onLogout)
            }
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(
                    start = MakiSpace.gutter, end = MakiSpace.gutter,
                    top = MakiSpace.xs, bottom = MakiSpace.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
        ) {
            Identidad(state)

            SectionLabel("TU TRABAJO HASTA HOY")
            MetricRow {
                MetricTile(
                    Icons.Filled.LocalShipping, state.collections, "recojos",
                    MakiColors.Rider, Modifier.weight(1f),
                )
                MetricTile(
                    Icons.Filled.Recycling, state.recycled, "reciclado",
                    MakiColors.Success, Modifier.weight(1f),
                )
                MetricTile(
                    Icons.Outlined.Cloud, state.co2, "CO₂ evitado",
                    MakiColors.Gen, Modifier.weight(1f),
                )
            }

            SectionLabel("TU CUENTA")
            NavRow(
                Icons.Filled.AccountBalanceWallet, "Mis ganancias",
                "Lo cobrado y lo que falta por liquidar",
                MakiColors.Money,
            ) { onNavigate(RiderRoutes.EARNINGS) }
            NavRow(
                Icons.Filled.BarChart, "Mis estadísticas",
                "Kilos, CO₂ evitado y tu ritmo por semana",
                MakiColors.Rider,
            ) { onNavigate(RiderRoutes.STATS) }
            NavRow(
                Icons.Filled.EmojiEvents, "Ranking",
                "Cómo vas frente al resto de Eco-Riders",
                MakiColors.Money,
            ) { onNavigate(RiderRoutes.RANKING) }
            NavRow(
                Icons.AutoMirrored.Filled.Assignment, "Historial de recojos",
                "Cada parada que cerraste",
                MakiColors.Rider,
            ) { onNavigate(RiderRoutes.HISTORY) }
            NavRow(
                Icons.Outlined.Person, "Editar perfil",
                "Tu nombre y teléfono",
                MakiColors.Rider,
            ) { onNavigate(RiderRoutes.EDIT_PROFILE) }
            NavRow(
                Icons.Outlined.Settings, "Configuración",
                "Avisos, meta mensual y tu sesión",
                MakiColors.Text2,
            ) { onInfo("Configuración estará disponible pronto.") }

            CerrarSesion(onLogout)
        }
    }
}

@Composable
private fun Identidad(state: RiderProfileUiState) {
    Column(
        Modifier.makiHero(MakiGradient.rider, glow = MakiGradient.glowOf(MakiColors.Rider))
            .fillMaxWidth().padding(MakiSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MakiSpace.xs),
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(MakiColors.WhiteTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.DirectionsBike, null,
                tint = MakiColors.OnDark, modifier = Modifier.size(36.dp),
            )
        }
        Text(state.name, color = MakiColors.OnDark, style = MakiType.metricLarge)
        Text(state.roleZone, color = MakiColors.OnRiderSoft, style = MakiType.bodySmall)
        Row(
            Modifier.padding(top = MakiSpace.sm),
            horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PastillaClara(Icons.AutoMirrored.Filled.DirectionsBike, state.vehicle)
            PastillaClara(Icons.Filled.Star, state.rating)
            PastillaClara(null, state.riderId)
        }
    }
}

/** A pill that reads on the gradient: the role tints are built for white backgrounds. */
@Composable
private fun PastillaClara(icon: ImageVector?, label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(MakiRadius.chip)).background(MakiColors.WhiteTint)
            .padding(horizontal = MakiSpace.sm, vertical = MakiSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MakiColors.OnDark, modifier = Modifier.size(13.dp))
        }
        Text(label, color = MakiColors.OnDark, style = MakiType.chip)
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
        Text(
            "Cerrar sesión", color = MakiColors.Error, style = MakiType.button,
            textAlign = TextAlign.Center,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1000)
@Composable
private fun RiderProfilePreview() {
    MAKITheme { RiderProfileScreen(state = SampleRiderProfile) }
}
