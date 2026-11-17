package pe.ruma.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import pe.ruma.app.ui.components.BackButton
import pe.ruma.app.ui.components.InlineLink
import pe.ruma.app.ui.components.RumaInputField
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

/**
 * Account creation for the role picked on the previous screen, laid out like the
 * PWA's: the title, one card with the fields, the button and the way back to sign in.
 * The address is prefilled with what onboarding detected, and stays editable — a GPS
 * fix lands on the block, not on the door.
 */
@Composable
fun RegisterScreen(
    vm: AuthViewModel,
    onSuccess: () -> Unit = {},
    onBack: () -> Unit = {},
    onLogin: () -> Unit = {},
) {
    val rider = vm.role == "eco_rider"
    val accent = if (rider) RumaColors.Rider else RumaColors.Gen

    Column(
        Modifier.fillMaxSize().rumaScreen(accent).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.sm, bottom = RumaSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
    ) {
        BackButton(onBack)

        Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.xs)) {
            Text(
                if (rider) "Crea tu cuenta de Eco-Rider" else "Crea tu cuenta",
                color = RumaColors.Text, style = RumaType.screenTitle,
            )
            Text(
                if (rider) "Recolecta reciclaje y gana por cada ruta."
                else "Recicla desde casa y gana Eco-Puntos por cada residuo.",
                color = RumaColors.Text2, style = RumaType.body,
            )
        }

        SoftCard(gap = RumaSpace.lg) {
            RumaInputField(
                "NOMBRE", Icons.Filled.Person, vm.fullName, vm::onFullName,
                placeholder = if (rider) "Carlos Ramos" else "Ana Quispe", accent = accent, enabled = !vm.loading,
            )
            RumaInputField(
                "CORREO", Icons.Filled.MailOutline, vm.email, vm::onEmail,
                placeholder = "tucorreo@ejemplo.com", keyboardType = KeyboardType.Email, accent = accent, enabled = !vm.loading,
            )
            RumaInputField(
                "CONTRASEÑA", Icons.Filled.Lock, vm.password, vm::onPassword,
                placeholder = "Mínimo 6 caracteres", keyboardType = KeyboardType.Password,
                visible = vm.passwordVisible, onToggleVisible = vm::togglePasswordVisible, accent = accent, enabled = !vm.loading,
            )
            if (!rider) {
                RumaInputField(
                    "DIRECCIÓN DE RECOJO", Icons.Filled.Place, vm.address, vm::onAddress,
                    placeholder = "Jr. Las Flores 123, SJL", accent = accent, enabled = !vm.loading,
                    helper = "Es donde el Eco-Rider recogerá tu reciclaje. Puedes cambiarla después.",
                )
                RumaInputField(
                    "¿TE INVITÓ ALGUIEN? CÓDIGO", Icons.Filled.CardGiftcard, vm.referralCode, vm::onReferralCode,
                    placeholder = "Opcional · 6 letras", imeAction = ImeAction.Done, accent = accent, enabled = !vm.loading,
                    helper = "Con el código de quien te invitó, ganas puntos de bienvenida cuando pesen tu primer reciclaje.",
                )
            }
        }

        vm.error?.let { Text(it, color = RumaColors.Error, style = RumaType.body) }
        vm.notice?.let { Text(it, color = RumaColors.Success, style = RumaType.body) }

        PrimaryButton(
            if (vm.loading) "Creando cuenta…" else "Crear cuenta",
            Modifier.fillMaxWidth(),
            enabled = !vm.loading,
            color = accent,
            onClick = { vm.signUp(onSuccess) },
        )

        InlineLink("¿Ya tienes cuenta?", "Iniciar sesión", accent, onLogin)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun RegisterPreview() {
    RumaTheme { RegisterScreen(vm = androidx.compose.runtime.remember { AuthViewModel() }) }
}
