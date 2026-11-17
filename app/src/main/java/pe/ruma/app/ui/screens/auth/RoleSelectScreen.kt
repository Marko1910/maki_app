package pe.ruma.app.ui.screens.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.ChoiceCard
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.InlineLink
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.RumaLogo
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

/**
 * Pick a role, then continue (PWA `RoleSelect`): choosing is a radio, going is the
 * button, so a wrong tap costs nothing.
 */
@Composable
fun RoleSelectScreen(
    onGenerator: () -> Unit = {},
    onRider: () -> Unit = {},
    onLogin: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onInfo: (String) -> Unit = {},
) {
    var rider by rememberSaveable { mutableStateOf(false) }
    // Choosing Eco-Rider washes the page into the rider's blue instead of snapping to it.
    val accent by animateColorAsState(if (rider) RumaColors.Rider else RumaColors.Gen, tween(450), label = "roleAccent")
    val wash by animateFloatAsState(if (rider) 1f else 0f, tween(450), label = "roleWash")
    Box(Modifier.fillMaxSize().rumaScreen(RumaColors.Gen)) {
    Box(Modifier.matchParentSize().graphicsLayer { alpha = wash }.rumaScreen(RumaColors.Rider))
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RumaSpace.gutter, vertical = RumaSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RumaSpace.xs),
        ) {
            RumaLogo(size = 96.dp)
            Text("Bienvenido a Ruma", color = RumaColors.Text, style = RumaType.screenTitle,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = RumaSpace.md))
            Text("Elige tu rol para crear tu cuenta", color = RumaColors.Text2, style = RumaType.body,
                textAlign = TextAlign.Center)
        }
        SectionLabel("Tu rol", Modifier.padding(top = RumaSpace.md))
        Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
            ChoiceCard(
                "Eco-Hero", selected = !rider, subtitle = "Reciclo donde vivo y gano puntos",
                accent = RumaColors.Gen, leading = { GlassBadge(icon = Icons.Filled.Home) },
            ) { rider = false }
            ChoiceCard(
                "Eco-Rider", selected = rider, subtitle = "Recojo reciclaje y gano dinero",
                accent = RumaColors.Rider, leading = { GlassBadge(icon = Icons.AutoMirrored.Filled.DirectionsBike) },
            ) { rider = true }
        }
        PrimaryButton(
            "Continuar", Modifier.fillMaxWidth().padding(top = RumaSpace.md), color = accent,
            onClick = { if (rider) onRider() else onGenerator() },
        )
        InlineLink("Ya tengo cuenta", "Iniciar sesión", accent, onLogin)
    }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RoleSelectPreview() {
    RumaTheme { RoleSelectScreen() }
}
