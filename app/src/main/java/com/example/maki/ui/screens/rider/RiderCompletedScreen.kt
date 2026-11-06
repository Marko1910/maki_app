package com.example.maki.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.HeroCard
import com.example.maki.ui.components.MetricTile
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SecondaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.StatusChip
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

data class RiderCompletedUiState(
    val stopName: String = "Fam. Nureña",
    val materialsSummary: String = "11 unidades",
    val pointsAwarded: String = "98 pts",
    val earnings: String = "S/2.77",
    val remaining: String = "Te queda 1 parada en tu ruta",
    /** False on the last stop: "Siguiente parada" would lead to an empty route. */
    val hasNext: Boolean = true,
)

@Composable
fun RiderCompletedScreen(
    state: RiderCompletedUiState = RiderCompletedUiState(),
    onNext: () -> Unit = {},
    onFinish: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().makiScreen(MakiColors.Rider).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MakiSpace.gutter, vertical = MakiSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
    ) {
        Text("¡Recojo Completado!", color = MakiColors.Text, style = MakiType.screenTitle)
        Text("Registraste la recolección de ${state.stopName}",
            color = MakiColors.Text2, style = MakiType.body, textAlign = TextAlign.Center)
        HeroCard(MakiColors.Rider, "Tus ganancias", state.earnings, "acreditado a tus ganancias")
        SectionLabel("Resumen del recojo", Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.stack)) {
            MetricTile(Icons.Filled.Inventory2, state.materialsSummary, "Materiales",
                MakiColors.Rider, Modifier.weight(1f))
            MetricTile(Icons.Filled.Star, state.pointsAwarded, "Puntos al generador",
                MakiColors.Rider, Modifier.weight(1f))
        }
        StatusChip(state.remaining, MakiColors.Rider)
        if (state.hasNext) {
            PrimaryButton("Siguiente parada", Modifier.fillMaxWidth(), color = MakiColors.Rider, onClick = onNext)
            SecondaryButton("Volver al inicio", Modifier.fillMaxWidth(), color = MakiColors.Rider, onClick = onFinish)
        } else {
            PrimaryButton("Volver al inicio", Modifier.fillMaxWidth(), color = MakiColors.Rider, onClick = onFinish)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun RiderCompletedPreview() {
    MAKITheme { RiderCompletedScreen(state = SampleCompleted) }
}

private val SampleCompleted = RiderCompletedUiState()
