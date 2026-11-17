package pe.ruma.app.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import pe.ruma.app.navigation.RiderRoutes
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.HeroChip
import pe.ruma.app.ui.components.HeroRule
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaSkeleton
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.MetricCard
import pe.ruma.app.ui.components.MetricRow
import pe.ruma.app.ui.components.ScreenHeader
import pe.ruma.app.ui.components.SecondaryButton
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.initialsOf
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

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
    // A tab, like the PWA's: no back button; the system back still works.
    Column(Modifier.fillMaxSize().rumaScreen(RumaColors.Rider).statusBarsPadding()) {
        ScreenHeader("Mi perfil")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            if (state == null) {
                if (error != null) RumaError(error, onRetry, accent = RumaColors.Rider)
                else RumaSkeleton(listOf(96.dp, 72.dp, 72.dp), label = "Cargando tu perfil…")
                // Cerrar sesion sigue a la vista: si el perfil no carga, salir de
                // la cuenta es justo lo que el usuario necesita poder hacer.
                CerrarSesion(onLogout)
                return@Column
            }
            Identidad(state)

            SectionLabel("Tu trabajo hasta hoy")
            MetricRow {
                MetricCard(state.collections, "Recojos", Modifier.weight(1f))
                MetricCard(state.recycled, "Reciclado", Modifier.weight(1f))
                MetricCard(state.co2, "CO₂ evitado", Modifier.weight(1f))
            }

            SectionLabel("Tu cuenta")
            RumaList(AccountRows) { r ->
                RumaRow(
                    r.title, subtitle = r.subtitle,
                    leading = { GlassBadge(icon = r.icon) },
                    onClick = { onNavigate(r.route) },
                )
            }

            CerrarSesion(onLogout)
        }
    }
}

private data class AccountRow(val icon: ImageVector, val title: String, val subtitle: String, val route: String)

private val AccountRows = listOf(
    AccountRow(Icons.Filled.AccountBalanceWallet, "Mis ganancias", "Lo cobrado y lo que falta por liquidar", RiderRoutes.EARNINGS),
    AccountRow(Icons.Filled.BarChart, "Mis estadísticas", "Kilos, CO₂ evitado y tu ritmo por semana", RiderRoutes.STATS),
    AccountRow(Icons.Filled.EmojiEvents, "Ranking", "Cómo vas frente al resto de Eco-Riders", RiderRoutes.RANKING),
    AccountRow(Icons.AutoMirrored.Filled.Assignment, "Historial de recojos", "Cada parada que cerraste", RiderRoutes.HISTORY),
    AccountRow(Icons.Outlined.Edit, "Editar perfil", "Tu nombre y teléfono", RiderRoutes.EDIT_PROFILE),
)

/** The person, not a figure: initials, name and role; the vehicle, code and rating as chips. */
@Composable
private fun Identidad(state: RiderProfileUiState) {
    Column(
        Modifier.rumaHero(RumaGradient.rider, glow = RumaGradient.glowOf(RumaColors.Rider))
            .fillMaxWidth().padding(RumaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(RumaColors.WhiteTint)
                    .border(1.dp, RumaColors.OnGradientTrack, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(initialsOf(state.name), color = RumaColors.OnDark, style = RumaType.sectionHeading) }
            Column(Modifier.weight(1f)) {
                Text(state.name, color = RumaColors.OnDark, style = RumaType.sectionHeading)
                Text(state.roleZone, color = RumaColors.OnRiderSoft, style = RumaType.bodySmall)
            }
        }
        HeroRule()
        // Scrolls rather than wraps: FlowRow crashes on device with the current BOM.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
            listOf(state.vehicle, state.riderId, "★ ${state.rating}")
                .filter { it.isNotBlank() && it != "★ " && it != "★ —" }
                .forEach { HeroChip(it) }
        }
    }
}

@Composable
private fun CerrarSesion(onLogout: () -> Unit) {
    SecondaryButton(
        "Cerrar sesión", Modifier.fillMaxWidth(),
        icon = Icons.AutoMirrored.Filled.Logout, color = RumaColors.Error, onClick = onLogout,
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1000)
@Composable
private fun RiderProfilePreview() {
    RumaTheme { RiderProfileScreen(state = SampleRiderProfile) }
}
