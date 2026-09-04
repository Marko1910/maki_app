package com.example.maki.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

@Composable
fun SplashScreen() {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(MakiColors.Gen, MakiColors.GenDark))),
        contentAlignment = Alignment.Center,
    ) {
        // Soft decorative circles, like the design.
        Box(Modifier.align(Alignment.TopStart).offset(x = (-90).dp, y = (-70).dp).size(300.dp).clip(CircleShape).background(Color(0x0DFFFFFF)))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = 40.dp, y = 60.dp).size(240.dp).clip(CircleShape).background(Color(0x0AFFFFFF)))

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Box(
                Modifier.makiShadow(34.dp, 10.dp, Color(0x33000000)).size(108.dp).clip(RoundedCornerShape(34.dp)).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Eco, null, tint = MakiColors.Gen, modifier = Modifier.size(58.dp)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Maki", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp)
                Text("Tu agente de reciclaje inteligente", color = Color(0xFFD6EFE6), fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            listOf(Color.White, Color(0x66FFFFFF), Color(0x66FFFFFF)).forEach { c ->
                Box(Modifier.size(7.dp).clip(CircleShape).background(c))
            }
        }
    }
}
