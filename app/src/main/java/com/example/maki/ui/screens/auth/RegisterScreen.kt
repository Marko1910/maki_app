package com.example.maki.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.MakiInputField
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

/**
 * Account creation for the role picked on the previous screen. The address is
 * prefilled with what onboarding detected, and stays editable — a GPS fix lands
 * on the block, not on the door.
 */
@Composable
fun RegisterScreen(
    vm: AuthViewModel,
    onSuccess: () -> Unit = {},
    onBack: () -> Unit = {},
    onLogin: () -> Unit = {},
) {
    val rider = vm.role == "eco_rider"
    val accent = if (rider) MakiColors.Rider else MakiColors.Gen

    Column(
        Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Filled.ChevronLeft, "Atrás", tint = MakiColors.Text,
            modifier = Modifier.size(26.dp).clickable(onClick = onBack),
        )

        Box(
            Modifier.makiShadow(22.dp, 6.dp, accent.copy(alpha = 0.25f)).size(70.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.78f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (rider) Icons.AutoMirrored.Filled.DirectionsBike else Icons.Filled.Home,
                null, tint = Color.White, modifier = Modifier.size(36.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (rider) "Crea tu cuenta de Eco-Rider" else "Crea tu cuenta",
                color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp,
            )
            Text(
                if (rider) "Recolecta reciclaje y gana por cada ruta."
                else "Recicla desde casa y gana Eco-Puntos por cada residuo.",
                color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 15.sp,
            )
        }

        MakiInputField(
            "NOMBRE", Icons.Filled.Person, vm.fullName, vm::onFullName,
            placeholder = if (rider) "Carlos Ramos" else "Fam. Nureña", accent = accent,
        )
        MakiInputField(
            "CORREO", Icons.Filled.MailOutline, vm.email, vm::onEmail,
            placeholder = "tucorreo@email.com", keyboardType = KeyboardType.Email, accent = accent,
        )
        MakiInputField(
            "CONTRASEÑA", Icons.Filled.Lock, vm.password, vm::onPassword,
            placeholder = "Mínimo 6 caracteres", keyboardType = KeyboardType.Password,
            visible = vm.passwordVisible, onToggleVisible = vm::togglePasswordVisible, accent = accent,
        )
        if (!rider) {
            MakiInputField(
                "DIRECCIÓN DE RECOJO", Icons.Filled.Place, vm.address, vm::onAddress,
                placeholder = "Jr. Las Flores 123, SJL", imeAction = ImeAction.Done, accent = accent,
            )
            Text(
                "Es donde el Eco-Rider recogerá tu reciclaje. Puedes cambiarla después.",
                color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp,
            )
        }

        vm.error?.let {
            Text(it, color = MakiColors.Error, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        vm.notice?.let {
            Text(it, color = MakiColors.Success, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }

        PrimaryButton(
            if (vm.loading) "Creando cuenta…" else "Crear cuenta",
            Modifier.fillMaxWidth(),
            enabled = !vm.loading,
            color = accent,
            onClick = { vm.signUp(onSuccess) },
        )

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("¿Ya tienes cuenta?", color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    "Iniciar sesión", color = accent, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onLogin),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun RegisterPreview() {
    MAKITheme { RegisterScreen(vm = AuthViewModel()) }
}
