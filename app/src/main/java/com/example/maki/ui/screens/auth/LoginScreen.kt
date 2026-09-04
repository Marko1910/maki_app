package com.example.maki.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.MakiInputField
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SecondaryButton
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onSuccess: () -> Unit = {},
    onRegister: () -> Unit = {},
    onInfo: (String) -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier.makiShadow(22.dp, 6.dp, Color(0x400F6E56)).size(76.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(MakiColors.Gen, MakiColors.GenDark))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Eco, null, tint = Color.White, modifier = Modifier.size(40.dp)) }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Bienvenido de vuelta", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text("Inicia sesión para seguir reciclando", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        }

        MakiInputField("CORREO", Icons.Filled.MailOutline, vm.email, vm::onEmail,
            placeholder = "tucorreo@email.com", keyboardType = KeyboardType.Email)
        MakiInputField("CONTRASEÑA", Icons.Filled.Lock, vm.password, vm::onPassword,
            placeholder = "••••••••", keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
            visible = vm.passwordVisible, onToggleVisible = vm::togglePasswordVisible)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("¿Olvidaste tu contraseña?", color = MakiColors.Gen, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clickable { onInfo("Recuperación de contraseña disponible pronto.") })
        }

        vm.error?.let {
            Text(it, color = MakiColors.Error, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }

        PrimaryButton(
            if (vm.loading) "Ingresando…" else "Iniciar sesión",
            Modifier.fillMaxWidth(),
            enabled = !vm.loading,
            onClick = { vm.signIn(onSuccess) },
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).height(1.dp).background(MakiColors.Border))
            Text("o", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Box(Modifier.weight(1f).height(1.dp).background(MakiColors.Border))
        }

        SecondaryButton("Continuar con Google", Modifier.fillMaxWidth(),
            onClick = { onInfo("Inicio con Google disponible pronto.") })

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("¿No tienes cuenta?", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("Regístrate", color = MakiColors.Gen, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onRegister))
            }
        }
    }
}
