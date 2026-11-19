package pe.ruma.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight persistence for the things that should survive app restarts:
 * the onboarding flag, the signed-in session (so the user is not asked to log in
 * every launch), and a cached profile JSON for instant first paint.
 *
 * Initialised once from [Context] in RumaApp.onCreate — not in an Activity: WorkManager
 * starts the process with no screen, and there the reads have to work too.
 */
object RumaPrefs {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences("ruma", Context.MODE_PRIVATE)
        }
    }

    private val ready get() = ::prefs.isInitialized

    /** Local participation goal, isolated by account and retained across sign-out. */
    /** One day a week by default: a low bar keeps more streaks alive (docs/retencion-incentivos-research.md, rec. 1). */
    const val DEFAULT_WEEKLY_GOAL = 1

    fun readWeeklyGoal(userId: String?): Int =
        (if (ready) prefs.getInt("ruma.goal.${userId ?: "anon"}", DEFAULT_WEEKLY_GOAL) else DEFAULT_WEEKLY_GOAL).takeIf { it in 1..7 } ?: DEFAULT_WEEKLY_GOAL

    /** The home's "Tu siguiente paso" the family already acted on: hidden until a different step comes. */
    fun readUsedStep(userId: String?): String? = if (ready) prefs.getString("ruma.step.${userId ?: "anon"}", null) else null

    fun writeUsedStep(userId: String?, key: String) {
        if (ready) prefs.edit().putString("ruma.step.${userId ?: "anon"}", key).apply()
    }

    fun writeWeeklyGoal(userId: String, days: Int) {
        require(days in 1..7)
        if (ready) prefs.edit().putInt("ruma.goal.$userId", days).apply()
    }

    /**
     * Points that reached the balance since this phone last showed it: what the home
     * screen celebrates. 0 on the first read and after a redemption (the balance fell).
     */
    fun takeBalanceGain(userId: String?, balance: Int): Int {
        if (!ready || userId == null) return 0
        val key = "ruma.balance.$userId"
        val last = if (prefs.contains(key)) prefs.getInt(key, balance) else null
        prefs.edit().putInt(key, balance).apply()
        return if (last == null) 0 else (balance - last).coerceAtLeast(0)
    }

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

    /**
     * When the rider said they go out ("salgo 3:00 pm"), as epoch millis. The app
     * turns the shift on by itself at that hour; null = no planned departure.
     */
    var shiftStartAt: Long?
        get() = if (ready) prefs.getLong(KEY_SHIFT_AT, 0L).takeIf { it > 0 } else null
        set(v) { if (ready) prefs.edit().putLong(KEY_SHIFT_AT, v ?: 0L).apply() }

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

    // ---- Cached display name ----
    // El generador ya pintaba su nombre al instante desde el perfil cacheado;
    // el rider no tenia nada que cachear y saludaba con "Hola," a secas
    // durante los segundos que tarda la red.
    var cachedDisplayName: String?
        get() = if (ready) prefs.getString(KEY_DISPLAY_NAME, null) else null
        set(v) { if (ready) prefs.edit().putString(KEY_DISPLAY_NAME, v).apply() }

    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_ONBOARDING = "onboarding_seen"
    private const val KEY_CAMERA_COACH = "camera_coach_seen"
    private const val KEY_ADDR = "pending_address"
    private const val KEY_LAT = "pending_lat"
    private const val KEY_LNG = "pending_lng"
    private const val KEY_ROLE = "active_role"
    private const val KEY_SHIFT_AT = "rider_shift_at"
    private const val KEY_ACCESS = "session_access"
    private const val KEY_REFRESH = "session_refresh"
    private const val KEY_UID = "session_uid"
    private const val KEY_EMAIL = "session_email"
    private const val KEY_PROFILE = "cached_profile"
}
