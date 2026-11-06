package com.example.maki.ui.screens.auth

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
import com.example.maki.ui.components.RumaLogo
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.makiHero
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiGradient
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

@Composable
fun SplashScreen() {
    Column(
        Modifier.fillMaxSize().makiScreen()
            .statusBarsPadding().navigationBarsPadding().padding(MakiSpace.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MakiSpace.xxl, Alignment.CenterVertically),
    ) {
        RumaLogo()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MakiSpace.sm),
        ) {
            Text("ruma", color = MakiColors.Text, style = MakiType.metricHero)
            Text(
                "Tu agente de reciclaje inteligente", color = MakiColors.Text2,
                style = MakiType.body, textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashPreview() {
    MAKITheme { SplashScreen() }
}
