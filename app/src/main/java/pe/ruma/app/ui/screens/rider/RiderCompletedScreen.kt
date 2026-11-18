package pe.ruma.app.ui.screens.rider

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.HeroCard
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.SecondaryButton
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

data class RiderCompletedUiState(
    val stopName: String = "Fam. Nureña",
    val materialsSummary: String = "11 unidades",
    val pointsAwarded: String = "98 pts",
    val earnings: String = "S/2.77",
    val remaining: String = "Te queda 1 parada en tu ruta",
    /** False on the last stop: "Siguiente parada" would lead to an empty route. */
    val hasNext: Boolean = true,
)

/**
 * The moment a stop closes, as the PWA draws it: a check on glass, who it was, what
 * it paid as the one hero, the summary, and what is left of the route.
 */
@Composable
fun RiderCompletedScreen(
    state: RiderCompletedUiState = RiderCompletedUiState(),
    onNext: () -> Unit = {},
    onFinish: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().rumaScreen(RumaColors.Rider).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RumaSpace.gutter, vertical = RumaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).padding(top = RumaSpace.xl)
                .size(96.dp).clip(CircleShape).background(RumaColors.SurfaceGlass)
                .border(1.dp, RumaColors.Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Check, null, tint = RumaColors.Rider, modifier = Modifier.size(44.dp)) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("¡Recojo Completado!", color = RumaColors.Text, style = RumaType.screenTitle, textAlign = TextAlign.Center)
            Text(state.stopName, color = RumaColors.Text2, style = RumaType.body, textAlign = TextAlign.Center)
        }
        HeroCard(
            RumaColors.Rider, "GANANCIA ESTIMADA", state.earnings.substringBefore(" · "),
            support = state.earnings.substringAfter(" · ", "").replaceFirstChar(Char::uppercase).ifBlank { null },
        )
        SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.sm) {
            Text("Resumen del recojo", color = RumaColors.Text, style = RumaType.rowTitle)
            SummaryLine("Materiales", state.materialsSummary)
            SummaryLine("Puntos para la familia", state.pointsAwarded)
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Route, null, tint = RumaColors.Text, modifier = Modifier.size(22.dp))
                Text(state.remaining, color = RumaColors.Text, style = RumaType.body)
            }
        }
        if (state.hasNext) {
            PrimaryButton("Siguiente parada", Modifier.fillMaxWidth(), icon = Icons.Filled.Route, color = RumaColors.Rider, onClick = onNext)
            SecondaryButton("Volver al inicio", Modifier.fillMaxWidth(), color = RumaColors.Rider, onClick = onFinish)
        } else {
            PrimaryButton("Volver al inicio", Modifier.fillMaxWidth(), color = RumaColors.Rider, onClick = onFinish)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RumaSpace.md), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = RumaColors.Text2, style = RumaType.body, modifier = Modifier.weight(1f))
        Text(value, color = RumaColors.Text, style = RumaType.rowTitle, textAlign = TextAlign.End)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun RiderCompletedPreview() {
    RumaTheme { RiderCompletedScreen(state = SampleCompleted) }
}

private val SampleCompleted = RiderCompletedUiState()
