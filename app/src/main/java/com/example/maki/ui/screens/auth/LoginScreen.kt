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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.maki.ui.components.RumaLogo
import androidx.compose.ui.unit.dp
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MakiInputField
import com.example.maki.ui.components.MakiLoading
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SecondaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.SoftCard
import com.example.maki.ui.components.StatusChip
import com.example.maki.ui.components.makiOutlined
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onSuccess: () -> Unit = {},
    onRegister: () -> Unit = {},
    onInfo: (String) -> Unit = {},
) {
    val accent = if (vm.role == "eco_rider") MakiColors.Rider else MakiColors.Gen
    Column(
        Modifier.fillMaxSize().makiScreen(accent).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = MakiSpace.gutter, end = MakiSpace.gutter, top = MakiSpace.sm, bottom = MakiSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = MakiSpace.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MakiSpace.lg)) {
            RumaLogo(size = 96.dp)
            Text("Bienvenido de vuelta", color = MakiColors.Text, style = MakiType.screenTitle,
                textAlign = TextAlign.Center)
            Text("Elige cómo entras y sigue reciclando", color = MakiColors.Text2, style = MakiType.body,
                textAlign = TextAlign.Center)
        }

        // Entering is role-shaped: the two apps look nothing alike, and picking
        // here fills in that role's demo account so a tester only taps "Iniciar sesión".
        RolePicker(selected = vm.role, onSelect = vm::selectRole)

        SoftCard(gap = MakiSpace.lg) {
            MakiInputField("CORREO", Icons.Filled.MailOutline, vm.email, vm::onEmail,
                placeholder = "tucorreo@email.com", keyboardType = KeyboardType.Email, accent = accent)
            MakiInputField("CONTRASEÑA", Icons.Filled.Lock, vm.password, vm::onPassword,
                placeholder = "••••••••", keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                visible = vm.passwordVisible, onToggleVisible = vm::togglePasswordVisible, accent = accent)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("¿Olvidaste tu contraseña?", color = accent, style = MakiType.bodySmall,
                    modifier = Modifier.clickable(onClickLabel = "Recuperar contraseña", role = Role.Button) {
                        onInfo("Recuperación de contraseña disponible pronto.")
                    }.heightIn(min = MakiSpace.xl + MakiSpace.xxl).padding(vertical = MakiSpace.lg))
            }

            vm.error?.let {
                MakiError(it, onRetry = { vm.signIn(onSuccess) })
            }

            if (vm.loading) MakiLoading("Ingresando…", accent = accent)

        }

            PrimaryButton(
                if (vm.loading) "Ingresando…" else "Iniciar sesión",
                Modifier.fillMaxWidth(),
                enabled = !vm.loading,
                color = accent,
                onClick = { vm.signIn(onSuccess) },
            )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MakiSpace.md)) {
            Box(Modifier.weight(1f).height(MakiSpace.xs / 4).background(MakiColors.Border))
            Text("o", color = MakiColors.Text2, style = MakiType.bodySmall)
            Box(Modifier.weight(1f).height(MakiSpace.xs / 4).background(MakiColors.Border))
        }

        SecondaryButton("Continuar con Google", Modifier.fillMaxWidth(), color = accent,
            onClick = { onInfo("Inicio con Google disponible pronto.") })

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("¿No tienes cuenta?", color = MakiColors.Text2, style = MakiType.body)
            Text("Regístrate", color = accent, style = MakiType.button,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClickLabel = "Crear una cuenta", role = Role.Button, onClick = onRegister)
                    .heightIn(min = MakiSpace.xl + MakiSpace.xxl).padding(MakiSpace.md))
        }
    }
}

/**
 * Two cards, one tap: which app you are entering. The account's real role still
 * wins after signing in (the database decides), so a wrong pick only means the
 * form was prefilled with the other demo account.
 */
@Composable
private fun RolePicker(selected: String?, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MakiSpace.md)) {
        RoleOption(
            Modifier.weight(1f), Icons.Filled.Home, "Eco-Hero", "Reciclo y gano puntos",
            accent = MakiColors.Gen, tint = MakiColors.GenTint,
            on = selected != "eco_rider",
        ) { onSelect("generador") }
        RoleOption(
            Modifier.weight(1f), Icons.AutoMirrored.Filled.DirectionsBike, "Eco-Rider", "Recojo y gano dinero",
            accent = MakiColors.Rider, tint = MakiColors.RiderTint,
            on = selected == "eco_rider",
        ) { onSelect("eco_rider") }
    }
}

@Composable
private fun RoleOption(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    tint: Color,
    on: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier.makiOutlined(if (on) accent else MakiColors.Border)
            .background(if (on) tint else Color.Transparent)
            .clickable(onClickLabel = "Entrar como $title", role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 52.dp).padding(horizontal = MakiSpace.md, vertical = MakiSpace.sm)
            .semantics { contentDescription = "Entrar como $title. $subtitle"; this.selected = on },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm),
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(MakiSpace.xl))
        Text(title, color = if (on) accent else MakiColors.Text2, style = MakiType.button)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun LoginPreview() {
    MAKITheme { LoginScreen(vm = androidx.compose.runtime.remember { AuthViewModel() }) }
}
