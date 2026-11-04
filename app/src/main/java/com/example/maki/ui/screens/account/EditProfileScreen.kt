package com.example.maki.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MakiInputField
import com.example.maki.ui.components.MakiLoading
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.SoftCard
import com.example.maki.ui.components.makiHero
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiGradient
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

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
 * leave empty.
 */
@Composable
fun EditProfileScreen(
    state: EditProfileUiState? = null,
    busy: Boolean = false,
    error: String? = null,
    accent: Color = MakiColors.Gen,
    onBack: () -> Unit = {},
    onName: (String) -> Unit = {},
    onPhone: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().makiScreen().statusBarsPadding()) {
        DetailHeader(title = "Editar perfil", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MakiSpace.gutter, end = MakiSpace.gutter,
                top = MakiSpace.xs, bottom = MakiSpace.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
        ) {
            when {
                error != null -> item { MakiError(error, onRetry, accent = accent) }
                state == null -> item { MakiLoading("Cargando tu perfil…", accent = accent) }
                else -> {
                    item { Retrato(state, accent) }

                    item { SectionLabel("TUS DATOS") }
                    item {
                        MakiInputField(
                            label = "NOMBRE", icon = Icons.Outlined.Person,
                            value = state.displayName, onValueChange = onName,
                            placeholder = "Cómo quieres que te llamen",
                            imeAction = ImeAction.Next, accent = accent,
                            enabled = !busy,
                        )
                    }
                    item {
                        MakiInputField(
                            label = "TELÉFONO", icon = Icons.Outlined.Phone,
                            value = state.phone, onValueChange = onPhone,
                            placeholder = "Para coordinar el recojo",
                            keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done,
                            accent = accent, enabled = !busy,
                        )
                    }
                    item {
                        SoftCard(Modifier.fillMaxWidth(), gap = MakiSpace.xs) {
                            Text("CORREO", color = MakiColors.Text2, style = MakiType.overline)
                            Text(
                                state.email.ifBlank { "—" },
                                color = MakiColors.Text, style = MakiType.body,
                            )
                            // El correo identifica la cuenta: cambiarlo es cambiar de
                            // sesion, no editar un campo.
                            Text(
                                "El correo no se puede cambiar desde aquí.",
                                color = MakiColors.Text2, style = MakiType.caption,
                            )
                        }
                    }

                    item {
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
    }
}

@Composable
private fun Retrato(state: EditProfileUiState, accent: Color) {
    Column(
        Modifier.makiHero(MakiGradient.forAccent(accent), glow = MakiGradient.glowOf(accent))
            .fillMaxWidth().padding(MakiSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MakiSpace.sm),
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(MakiColors.WhiteTint),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                state.displayName.trim().take(1).uppercase().ifBlank { "·" },
                color = MakiColors.OnDark, style = MakiType.metricLarge,
            )
        }
        Text(
            state.displayName.ifBlank { "Sin nombre" },
            color = MakiColors.OnDark, style = MakiType.metricLarge,
            textAlign = TextAlign.Center,
        )
        // La foto llega cuando exista el bucket `avatars`; hasta entonces el
        // retrato es la inicial, y decirlo es mejor que un boton que no sube nada.
        Text(
            "Tu inicial por ahora. La foto llega pronto.",
            color = MakiGradient.softOn(accent), style = MakiType.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun EditProfilePreview() {
    MAKITheme {
        EditProfileScreen(
            state = EditProfileUiState(
                displayName = "Marco Nureña",
                phone = "+51 987 650 010",
                email = "generador@maki.test",
            ),
        )
    }
}
