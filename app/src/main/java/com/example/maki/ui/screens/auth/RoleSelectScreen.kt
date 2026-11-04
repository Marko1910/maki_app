package com.example.maki.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.NavRow
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.makiHero
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiGradient
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

@Composable
fun RoleSelectScreen(
    onGenerator: () -> Unit = {},
    onRider: () -> Unit = {},
    onLogin: () -> Unit = {},
    onInfo: (String) -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().makiScreen().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MakiSpace.gutter, vertical = MakiSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .makiHero(MakiGradient.genDeep, glow = MakiGradient.glowOf(MakiColors.Gen))
                .padding(MakiSpace.xl),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.sm),
        ) {
            Text("Bienvenido a Ruma", color = MakiColors.OnDark, style = MakiType.screenTitle)
            Text("Elige tu rol para crear tu cuenta", color = MakiColors.OnGenSoft, style = MakiType.body)
        }
        SectionLabel("Tu rol", Modifier.padding(top = MakiSpace.md))
        NavRow(Icons.Filled.Home, "Eco-Hero", "Reciclo donde vivo y gano puntos",
            accent = MakiColors.Gen, onClick = onGenerator)
        NavRow(Icons.AutoMirrored.Filled.DirectionsBike, "Eco-Rider", "Recojo reciclaje y gano dinero",
            accent = MakiColors.Rider, onClick = onRider)
        NavRow(Icons.Filled.Apartment, "Centro de Acopio", "Gestiono la operación",
            accent = MakiColors.Admin,
            onClick = { onInfo("El panel de Centro de Acopio estará disponible pronto.") })
        Column(
            Modifier.fillMaxWidth().padding(top = MakiSpace.xl),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.xs),
        ) {
            Text("Ya tengo cuenta", color = MakiColors.Text2, style = MakiType.body,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("Iniciar sesión", color = MakiColors.Gen, style = MakiType.button,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClickLabel = "Iniciar sesión", role = Role.Button, onClick = onLogin)
                    .heightIn(min = MakiSpace.xl + MakiSpace.xxl).padding(MakiSpace.md))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RoleSelectPreview() {
    MAKITheme { RoleSelectScreen() }
}
