package pe.ruma.app.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.RumaBottomAction
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaInputField
import pe.ruma.app.ui.components.RumaSkeleton
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.initialsOf
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

data class EditProfileUiState(
    val displayName: String = "",
    val phone: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
)

/**
 * Your own name and phone, for either role.
 *
 * The name is not decoration: the rider sees it at the door and the household
 * sees the rider's on the map, so it is the one field this screen refuses to
 * leave empty. Laid out like the PWA's: the initials on glass, the three fields in
 * one card, and "Guardar cambios" pinned where the thumb is.
 */
@Composable
fun EditProfileScreen(
    state: EditProfileUiState? = null,
    busy: Boolean = false,
    error: String? = null,
    accent: Color = RumaColors.Gen,
    onBack: () -> Unit = {},
    onName: (String) -> Unit = {},
    onPhone: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().rumaScreen(accent).statusBarsPadding()) {
        DetailHeader(title = "Editar perfil", onBack = onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = RumaSpace.gutter, end = RumaSpace.gutter,
                top = RumaSpace.xs, bottom = RumaSpace.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            when {
                error != null -> item { RumaError(error, onRetry, accent = accent) }
                state == null -> item { RumaSkeleton(listOf(96.dp, 72.dp, 72.dp, 52.dp), label = "Cargando tu perfil…") }
                else -> {
                    item { Retrato(state, accent) }
                    item { SectionLabel("TUS DATOS") }
                    item {
                        SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.lg) {
                            RumaInputField(
                                label = "NOMBRE", icon = Icons.Outlined.Person,
                                value = state.displayName, onValueChange = onName,
                                placeholder = "Ana Quispe",
                                imeAction = ImeAction.Next, accent = accent,
                                enabled = !busy, helper = "Cómo quieres que te llamen",
                            )
                            RumaInputField(
                                label = "TELÉFONO", icon = Icons.Outlined.Phone,
                                value = state.phone, onValueChange = onPhone,
                                placeholder = "+51 987 650 010",
                                keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done,
                                accent = accent, enabled = !busy, helper = "Para coordinar el recojo",
                            )
                            // El correo identifica la cuenta: cambiarlo es cambiar de
                            // sesion, no editar un campo.
                            RumaInputField(
                                label = "CORREO", icon = Icons.Outlined.MailOutline,
                                value = state.email.ifBlank { "—" }, onValueChange = {},
                                placeholder = "", enabled = false, accent = accent,
                                helper = "El correo no se puede cambiar desde aquí.",
                            )
                        }
                    }
                }
            }
        }
        if (state != null && error == null) {
            RumaBottomAction {
                PrimaryButton(
                    label = if (busy) "Guardando…" else "Guardar cambios",
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Check,
                    enabled = !busy && state.displayName.trim().length >= 2,
                    color = accent,
                    onClick = onSave,
                )
            }
        }
    }
}

/** The initials on glass; the photo arrives once the `avatars` bucket exists. */
@Composable
private fun Retrato(state: EditProfileUiState, accent: Color) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(RumaColors.SurfaceGlass)
                .border(1.dp, RumaColors.Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initialsOf(state.displayName),
                color = accent, style = RumaType.screenTitle,
            )
        }
        // Saying so is better than a button that uploads nothing.
        Text(
            "Tu inicial por ahora. La foto llega pronto.",
            color = RumaColors.Text2, style = RumaType.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun EditProfilePreview() {
    RumaTheme {
        EditProfileScreen(
            state = EditProfileUiState(
                displayName = "Marco Nureña",
                phone = "+51 987 650 010",
                email = "generador@ruma.test",
            ),
        )
    }
}
