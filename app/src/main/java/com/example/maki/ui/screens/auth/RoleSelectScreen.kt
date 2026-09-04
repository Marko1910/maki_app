package com.example.maki.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

@Composable
fun RoleSelectScreen(
    onGenerator: () -> Unit = {},
    onRider: () -> Unit = {},
    onLogin: () -> Unit = {},
    onInfo: (String) -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Bienvenido a Maki", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text("Elige tu rol para crear tu cuenta", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        }
        Box(Modifier.size(4.dp))
        RoleCard(Icons.Filled.Home, MakiColors.Gen, "Generador", "Reciclo en casa y gano puntos", onClick = onGenerator)
        RoleCard(Icons.AutoMirrored.Filled.DirectionsBike, MakiColors.Rider, "Eco-Rider", "Recolecto y gano por ruta",
            onClick = onRider)
        RoleCard(Icons.Filled.Apartment, MakiColors.Admin, "Centro de Acopio", "Gestiono la operación",
            onClick = { onInfo("El panel de Centro de Acopio estará disponible pronto.") })
        Box(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Ya tengo cuenta", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("Iniciar sesión", color = MakiColors.Gen, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onLogin))
            }
        }
    }
}

@Composable
private fun RoleCard(icon: ImageVector, accent: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().makiShadow(20.dp, 4.dp, accent.copy(alpha = 0.12f)).clip(RoundedCornerShape(20.dp))
            .background(MakiColors.Surface).border(1.5.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.8f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = accent, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(subtitle, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = accent, modifier = Modifier.size(22.dp))
    }
}
