package com.example.maki.ui.screens.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.maki.data.MakiApi
import com.example.maki.data.MakiRepository
import kotlinx.coroutines.launch

/**
 * Editing your own account, shared by both roles.
 *
 * A household and a rider edit the same three fields on the same `profiles` row,
 * so this follows the precedent `MarketViewModel` set: one ViewModel both Roots
 * instantiate, rather than the same screen copied into each flow.
 */
class AccountViewModel : ViewModel() {

    var state by mutableStateOf<EditProfileUiState?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** True once a save lands, so the Root can close the screen. */
    var saved by mutableStateOf(false)
        private set

    fun consumeMessage() { message = null }
    fun consumeSaved() { saved = false }

    fun load(force: Boolean = false) {
        // Un guardado anterior sin consumir cerraba el editor nada mas abrirlo.
        saved = false
        if (state != null && !force) return
        viewModelScope.launch {
            try {
                if (!MakiApi.isSignedIn) MakiRepository.signInAsTestGenerator()
                val me = MakiRepository.loadMyProfile()
                state = EditProfileUiState(
                    displayName = me?.display_name.orEmpty(),
                    phone = me?.phone.orEmpty(),
                    email = MakiApi.session?.email.orEmpty(),
                    avatarUrl = me?.avatar_url,
                )
                error = null
            } catch (e: Exception) {
                error = e.message ?: "No se pudo cargar tu perfil."
            }
        }
    }

    fun onName(value: String) {
        state = state?.copy(displayName = value)
    }

    fun onPhone(value: String) {
        // Solo lo que puede formar un telefono: el teclado numerico deja pasar
        // caracteres que el backend acepta y luego nadie puede marcar.
        state = state?.copy(phone = value.filter { it.isDigit() || it in "+ -" }.take(20))
    }

    /**
     * The name is what every other screen shows, so an empty one is refused here
     * rather than written and discovered later as a blank greeting.
     */
    fun save() {
        val s = state ?: return
        val nombre = s.displayName.trim()
        if (nombre.length < 2) {
            message = "Escribe tu nombre para guardarlo."
            return
        }
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                MakiRepository.updateMyProfile(nombre, s.phone.trim().ifBlank { null })
                state = s.copy(displayName = nombre)
                message = "Perfil actualizado."
                saved = true
            } catch (e: Exception) {
                message = e.message ?: "No se pudo guardar."
            } finally {
                busy = false
            }
        }
    }
}
