package com.example.maki.ui.screens.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.maki.data.MakiPrefs
import com.example.maki.data.MakiRepository
import com.example.maki.navigation.AppRoutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Drives the splash routing decision, the login form and account creation. */
class AuthViewModel : ViewModel() {

    // Login form (prefilled with the demo account for the selected role so it
    // works out of the box).
    var email by mutableStateOf("generador@maki.test")
        private set
    var password by mutableStateOf("Maki12345!")
        private set
    var passwordVisible by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Non-error feedback, e.g. "confirma tu correo". */
    var notice by mutableStateOf<String?>(null)
        private set

    // Sign-up-only fields. The address comes from what onboarding detected.
    var fullName by mutableStateOf("")
        private set
    var address by mutableStateOf(MakiPrefs.pendingAddress.orEmpty())
        private set

    /** Role chosen on the role-select screen; drives the post-login destination. */
    var role by mutableStateOf(MakiPrefs.role)
        private set

    fun onEmail(v: String) { email = v; clearFeedback() }
    fun onPassword(v: String) { password = v; clearFeedback() }
    fun onFullName(v: String) { fullName = v; clearFeedback() }
    fun onAddress(v: String) { address = v; clearFeedback() }
    fun togglePasswordVisible() { passwordVisible = !passwordVisible }

    private fun clearFeedback() { error = null; notice = null }

    /**
     * Records the chosen role. Sign-up starts from empty fields; the demo
     * credentials only prefill the *login* form, where they are a convenience.
     */
    fun selectRole(r: String, forSignUp: Boolean = false) {
        role = r
        MakiPrefs.role = r
        clearFeedback()
        if (forSignUp) {
            email = ""
            password = ""
            fullName = ""
            address = MakiPrefs.pendingAddress.orEmpty()
        } else {
            email = demoEmailFor(r)
            password = "Maki12345!"
        }
    }

    /** Top-level destination for the active role after a successful login. */
    fun homeRoute(): String = when (role) {
        "eco_rider" -> AppRoutes.RIDER
        else -> AppRoutes.GENERATOR
    }

    private fun demoEmailFor(r: String): String = when (r) {
        "eco_rider" -> "rider@maki.test"
        else -> "generador@maki.test"
    }

    /**
     * Decides where to start: onboarding on first launch, the app if a stored
     * session can be refreshed, otherwise login. Keeps the splash visible briefly
     * so the brand registers without feeling slow.
     */
    fun decideStart(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val splash = launch { delay(1100) }
            val next = when {
                !MakiPrefs.onboardingSeen -> AppRoutes.ONBOARDING
                runCatching { MakiRepository.restoreSession() }.getOrDefault(false) -> homeRoute()
                else -> AppRoutes.LOGIN
            }
            splash.join()
            onResult(next)
        }
    }

    /** Remembers the onboarding answers (seen flag + the address the user confirmed). */
    fun finishOnboarding(address: String?, lat: Double?, lng: Double?) {
        MakiPrefs.onboardingSeen = true
        address?.takeIf { it.isNotBlank() }?.let {
            MakiPrefs.pendingAddress = it
            MakiPrefs.pendingLat = lat
            MakiPrefs.pendingLng = lng
            this.address = it
        }
    }

    fun signIn(onSuccess: () -> Unit) {
        if (loading) return
        loading = true
        clearFeedback()
        viewModelScope.launch {
            try {
                MakiRepository.signIn(email.trim(), password)
                onSuccess()
            } catch (e: Exception) {
                error = "Correo o contraseña incorrectos."
            } finally {
                loading = false
            }
        }
    }

    /** Creates the account for the selected role and enters the app. */
    fun signUp(onSuccess: () -> Unit) {
        if (loading) return
        val name = fullName.trim()
        when {
            name.length < 2 -> { error = "Escribe tu nombre."; return }
            !email.contains("@") || !email.contains(".") -> { error = "Escribe un correo válido."; return }
            password.length < 6 -> { error = "La contraseña debe tener al menos 6 caracteres."; return }
        }
        loading = true
        clearFeedback()
        viewModelScope.launch {
            try {
                val signedIn = MakiRepository.signUp(
                    email = email,
                    password = password,
                    role = role,
                    fullName = name,
                    addressLine = address.trim().takeIf { it.isNotBlank() },
                    latitude = MakiPrefs.pendingLat,
                    longitude = MakiPrefs.pendingLng,
                )
                if (signedIn) {
                    onSuccess()
                } else {
                    // Email confirmation is on for this project: the account exists
                    // but has no session yet, so send them to log in after confirming.
                    notice = "Cuenta creada. Confirma tu correo y luego inicia sesión."
                }
            } catch (e: Exception) {
                error = e.message ?: "No se pudo crear la cuenta."
            } finally {
                loading = false
            }
        }
    }
}
