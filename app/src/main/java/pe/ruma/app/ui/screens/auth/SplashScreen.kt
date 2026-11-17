package pe.ruma.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import pe.ruma.app.ui.components.RumaLogo
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

@Composable
fun SplashScreen() {
    Column(
        Modifier.fillMaxSize().rumaScreen()
            .statusBarsPadding().navigationBarsPadding().padding(RumaSpace.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RumaSpace.xxl, Alignment.CenterVertically),
    ) {
        RumaLogo()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
        ) {
            Text("ruma", color = RumaColors.Text, style = RumaType.metricHero)
            Text(
                "Tu agente de reciclaje inteligente", color = RumaColors.Text2,
                style = RumaType.body, textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashPreview() {
    RumaTheme { SplashScreen() }
}
