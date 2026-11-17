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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import pe.ruma.app.ui.components.InlineLink
import pe.ruma.app.ui.components.RumaInputField
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.RumaLogo
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

/**
 * Sign in, laid out like the PWA's: the logo on its glass plate, one card with the two
 * fields, the button and the way to sign up. Only email and password: the account's
 * role comes from the database after signing in.
 */
@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onSuccess: () -> Unit = {},
    onRegister: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onInfo: (String) -> Unit = {},
) {
    val accent = RumaColors.Gen
    Column(
        Modifier.fillMaxSize().rumaScreen(accent).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xxl, bottom = RumaSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
        ) {
            RumaLogo()
            Text("Bienvenido de vuelta", color = RumaColors.Text, style = RumaType.screenTitle,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = RumaSpace.sm))
            Text("Entra y sigue reciclando", color = RumaColors.Text2, style = RumaType.body,
                textAlign = TextAlign.Center)
        }

        SoftCard(gap = RumaSpace.lg) {
            RumaInputField("CORREO", Icons.Filled.MailOutline, vm.email, vm::onEmail,
                placeholder = "tucorreo@ejemplo.com", keyboardType = KeyboardType.Email, accent = accent,
                enabled = !vm.loading)
            RumaInputField("CONTRASEÑA", Icons.Filled.Lock, vm.password, vm::onPassword,
                placeholder = "Tu contraseña", keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                visible = vm.passwordVisible, onToggleVisible = vm::togglePasswordVisible, accent = accent,
                enabled = !vm.loading)
        }

        vm.error?.let { Text(it, color = RumaColors.Error, style = RumaType.body) }

        PrimaryButton(
            if (vm.loading) "Ingresando…" else "Iniciar sesión",
            Modifier.fillMaxWidth(),
            enabled = !vm.loading,
            color = accent,
            onClick = { vm.signIn(onSuccess) },
        )

        InlineLink("¿No tienes cuenta?", "Regístrate", accent, onRegister)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun LoginPreview() {
    RumaTheme { LoginScreen(vm = androidx.compose.runtime.remember { AuthViewModel() }) }
}
