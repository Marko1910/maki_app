package com.example.maki.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.BackButton
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MakiInputField
import com.example.maki.ui.components.MakiLoading
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SoftCard
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

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
        Modifier.fillMaxSize().makiScreen().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = MakiSpace.gutter, end = MakiSpace.gutter, top = MakiSpace.sm, bottom = MakiSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            IconBadge(
                if (rider) Icons.AutoMirrored.Filled.DirectionsBike else Icons.Filled.Home,
                accent, accent.copy(alpha = 0.12f),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(MakiSpace.sm)) {
            Text(
                if (rider) "Crea tu cuenta de Eco-Rider" else "Crea tu cuenta",
                color = MakiColors.Text, style = MakiType.screenTitle,
            )
            Text(
                if (rider) "Recolecta reciclaje y gana por cada ruta."
                else "Recicla desde casa y gana Eco-Puntos por cada residuo.",
                color = MakiColors.Text2, style = MakiType.body,
            )
        }

        SoftCard(gap = MakiSpace.lg) {
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
                    color = MakiColors.Text2, style = MakiType.label,
                )
            }

            vm.error?.let {
                MakiError(it, onRetry = { vm.signUp(onSuccess) })
            }
            vm.notice?.let {
                Text(it, color = MakiColors.Success, style = MakiType.bodySmall)
            }

            if (vm.loading) MakiLoading("Creando cuenta…", accent = accent)

            PrimaryButton(
                if (vm.loading) "Creando cuenta…" else "Crear cuenta",
                Modifier.fillMaxWidth(),
                enabled = !vm.loading,
                color = accent,
                onClick = { vm.signUp(onSuccess) },
            )

        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("¿Ya tienes cuenta?", color = MakiColors.Text2, style = MakiType.body)
            Text("Iniciar sesión", color = accent, style = MakiType.button,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClickLabel = "Iniciar sesión", role = Role.Button, onClick = onLogin)
                    .heightIn(min = MakiSpace.xl + MakiSpace.xxl).padding(MakiSpace.md))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun RegisterPreview() {
    MAKITheme { RegisterScreen(vm = androidx.compose.runtime.remember { AuthViewModel() }) }
}
