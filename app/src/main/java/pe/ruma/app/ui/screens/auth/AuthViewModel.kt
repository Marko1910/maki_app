package pe.ruma.app.ui.screens.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pe.ruma.app.data.PeruPhone
import pe.ruma.app.data.RumaPrefs
import pe.ruma.app.data.RumaRepository
import pe.ruma.app.navigation.AppRoutes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Drives the splash routing decision, the login form and account creation. */
class AuthViewModel : ViewModel() {

    // Login form: starts empty, like a fresh install sees it.
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
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
    var address by mutableStateOf(RumaPrefs.pendingAddress.orEmpty())
        private set
    /** Optional "¿Te invitó alguien?" code; the database checks it, the app only sends it. */
    var referralCode by mutableStateOf("")
        private set
    /** Mobile number: the rider calls the family from the street, and the family calls the rider. */
    var phone by mutableStateOf("")
        private set

    /** Role chosen on the role-select screen; drives the post-login destination. */
    var role by mutableStateOf(RumaPrefs.role)
        private set

    fun onEmail(v: String) { email = v; clearFeedback() }
    fun onPassword(v: String) { password = v; clearFeedback() }
    fun onFullName(v: String) { fullName = v; clearFeedback() }
    fun onAddress(v: String) { address = v; clearFeedback() }
    fun onPhone(v: String) { phone = v.filter { it.isDigit() || it in "+ -" }.take(16); clearFeedback() }
    fun onReferralCode(v: String) { referralCode = v.uppercase().filter { it.isLetterOrDigit() }.take(6); clearFeedback() }
    fun togglePasswordVisible() { passwordVisible = !passwordVisible }

    private fun clearFeedback() { error = null; notice = null }

    /** Records the role chosen for sign-up, which starts from empty fields. */
    fun selectRole(r: String, forSignUp: Boolean = false) {
        role = r
        RumaPrefs.role = r
        clearFeedback()
        if (forSignUp) {
            email = ""
            password = ""
            fullName = ""
            address = RumaPrefs.pendingAddress.orEmpty()
        }
    }

    /** Top-level destination for the active role after a successful login. */
    fun homeRoute(): String = when (role) {
        "eco_rider" -> AppRoutes.RIDER
        else -> AppRoutes.GENERATOR
    }

    /**
     * Decides where to start: onboarding on first launch, the app if a stored
     * session can be refreshed, otherwise login. Keeps the splash visible briefly
     * so the brand registers without feeling slow.
     *
     * Suspends in the caller's scope instead of the ViewModel's: a rotation during the
     * splash recreated the NavController, and a ViewModel-scoped callback then navigated
     * the destroyed one ("State must be at least 'CREATED'") and crashed the app.
     */
    suspend fun decideStart(): String = coroutineScope {
            val splash = launch { delay(1100) }
            val next = when {
                !RumaPrefs.onboardingSeen -> AppRoutes.ONBOARDING
                runCatching { RumaRepository.restoreSession() }.getOrDefault(false) -> {
                    refreshRole()
                    homeRoute()
                }
                else -> AppRoutes.LOGIN
            }
            splash.join()
            next
    }

    /**
     * The account's real role, from the database. The destination used to come
     * from the role picked on the sign-up screen (a local pref, "generador" by
     * default), so a rider logging in landed in the family's app. Offline, the
     * last known role stands.
     */
    private suspend fun refreshRole() {
        runCatching { RumaRepository.loadMyRole() }.getOrNull()?.let {
            role = it
            RumaPrefs.role = it
        }
    }

    /** Remembers the onboarding answers (seen flag + the address the user confirmed). */
    fun finishOnboarding(address: String?, lat: Double?, lng: Double?) {
        RumaPrefs.onboardingSeen = true
        address?.takeIf { it.isNotBlank() }?.let {
            RumaPrefs.pendingAddress = it
            RumaPrefs.pendingLat = lat
            RumaPrefs.pendingLng = lng
            this.address = it
        }
    }

    fun signIn(onSuccess: () -> Unit) {
        if (loading) return
        loading = true
        clearFeedback()
        viewModelScope.launch {
            try {
                RumaRepository.signIn(email.trim(), password)
            } catch (e: Exception) {
                error = "Correo o contraseña incorrectos."
                loading = false
                return@launch
            }
            refreshRole()
            loading = false
            if (role == "operador") {
                // There is no acopio console in this app; an operator would land
                // in a family's screens showing nobody's data.
                RumaRepository.signOut()
                error = "Esta app es para vecinos y Eco-Riders. Los operadores usan la consola del centro de acopio."
                return@launch
            }
            onSuccess()
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
            PeruPhone.normalize(phone) == null -> { error = PeruPhone.HINT; return }
        }
        loading = true
        clearFeedback()
        viewModelScope.launch {
            try {
                val signedIn = RumaRepository.signUp(
                    email = email,
                    password = password,
                    role = role,
                    fullName = name,
                    phone = PeruPhone.normalize(phone),
                    addressLine = address.trim().takeIf { it.isNotBlank() },
                    latitude = RumaPrefs.pendingLat,
                    longitude = RumaPrefs.pendingLng,
                    referralCode = referralCode.takeIf { it.isNotBlank() },
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
