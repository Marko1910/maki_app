package com.example.maki.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight persistence for the things that should survive app restarts:
 * the onboarding flag, the signed-in session (so the user is not asked to log in
 * every launch), and a cached profile JSON for instant first paint.
 *
 * Initialised once from [Context] in MainActivity.
 */
object MakiPrefs {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences("maki", Context.MODE_PRIVATE)
        }
    }

    private val ready get() = ::prefs.isInitialized

    // ---- Onboarding ----
    var onboardingSeen: Boolean
        get() = ready && prefs.getBoolean(KEY_ONBOARDING, false)
        set(v) { if (ready) prefs.edit().putBoolean(KEY_ONBOARDING, v).apply() }

    // ---- First-run camera coaching (shown once, then never again) ----
    var cameraCoachSeen: Boolean
        get() = ready && prefs.getBoolean(KEY_CAMERA_COACH, false)
        set(v) { if (ready) prefs.edit().putBoolean(KEY_CAMERA_COACH, v).apply() }

    /**
     * Where the user said they live, captured during onboarding — before the account
     * exists, so it can't go to the database yet. Consumed by the sign-up screen.
     */
    var pendingAddress: String?
        get() = if (ready) prefs.getString(KEY_ADDR, null) else null
        set(v) { if (ready) prefs.edit().putString(KEY_ADDR, v).apply() }

    // Stored as text: a float would round the fix to roughly a metre for nothing.
    var pendingLat: Double?
        get() = if (ready) prefs.getString(KEY_LAT, null)?.toDoubleOrNull() else null
        set(v) { if (ready) prefs.edit().putString(KEY_LAT, v?.toString()).apply() }

    var pendingLng: Double?
        get() = if (ready) prefs.getString(KEY_LNG, null)?.toDoubleOrNull() else null
        set(v) { if (ready) prefs.edit().putString(KEY_LNG, v?.toString()).apply() }

    // ---- Active role ("generador" | "eco_rider" | "operador") ----
    var role: String
        get() = (if (ready) prefs.getString(KEY_ROLE, null) else null) ?: "generador"
        set(v) { if (ready) prefs.edit().putString(KEY_ROLE, v).apply() }

    // ---- Session ----
    fun saveSession(s: Session) {
        if (!ready) return
        prefs.edit()
            .putString(KEY_ACCESS, s.accessToken)
            .putString(KEY_REFRESH, s.refreshToken)
            .putString(KEY_UID, s.userId)
            .putString(KEY_EMAIL, s.email)
            .apply()
    }

    fun loadSession(): Session? {
        if (!ready) return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val uid = prefs.getString(KEY_UID, null) ?: return null
        return Session(accessToken = access, refreshToken = refresh, userId = uid, email = prefs.getString(KEY_EMAIL, null))
    }

    fun clearSession() {
        if (!ready) return
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_UID).remove(KEY_EMAIL).remove(KEY_PROFILE).apply()
    }

    // ---- Cached profile (raw JSON of GeneratorProfileDto) ----
    var cachedProfileJson: String?
        get() = if (ready) prefs.getString(KEY_PROFILE, null) else null
        set(v) { if (ready) prefs.edit().putString(KEY_PROFILE, v).apply() }

    private const val KEY_ONBOARDING = "onboarding_seen"
    private const val KEY_CAMERA_COACH = "camera_coach_seen"
    private const val KEY_ADDR = "pending_address"
    private const val KEY_LAT = "pending_lat"
    private const val KEY_LNG = "pending_lng"
    private const val KEY_ROLE = "active_role"
    private const val KEY_ACCESS = "session_access"
    private const val KEY_REFRESH = "session_refresh"
    private const val KEY_UID = "session_uid"
    private const val KEY_EMAIL = "session_email"
    private const val KEY_PROFILE = "cached_profile"
}
