package pe.ruma.app.data

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Push with the app closed (Firebase Cloud Messaging). The database decides what is sent and when
 * (`notifications.push`, quiet hours, retries: supabase/sql/2026-11-12_push_reglas.sql) and the `send-push`
 * Edge Function delivers it. The app only registers its token for the signed-in account.
 *
 * Every session start goes through [RumaRepository] (sign-in, sign-up, restored session), which calls
 * [sync]; every sign-out goes through [RumaRepository.signOut], which calls [onSignOut]. No screen does either.
 *
 * Firebase starts only when the build had `app/google-services.json` (docs/push-setup.md); without it
 * no FirebaseApp exists and every call here is a no-op.
 */
object RumaPush {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var app: Context? = null

    /** Intent extras of a pickup notice, the same keys `send-push` puts in the FCM data. */
    const val EXTRA_REFERENCE_TYPE = "reference_type"
    const val EXTRA_REFERENCE_ID = "reference_id"

    /**
     * The pickup of the notice the user just tapped. The role's Root opens it and calls [consumeOpenPickup];
     * it waits here while the app is still on splash or login.
     */
    var openPickupId by mutableStateOf<String?>(null)
        private set

    fun consumeOpenPickup() { openPickupId = null }

    /** From Application.onCreate: [sync] can then run from the data layer, where no Activity exists. */
    fun init(context: Context) { app = context.applicationContext }

    private val firebaseReady: Boolean
        get() = app?.let { runCatching { FirebaseApp.getApps(it).isNotEmpty() }.getOrDefault(false) } ?: false

    /** Registers this phone for the signed-in account. Safe to call on every session start. */
    fun sync() {
        if (!firebaseReady || RumaApi.session == null) return
        runCatching { FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> register(token) } }
    }

    internal fun register(token: String) {
        if (RumaApi.session == null) return
        scope.launch { runCatching { RumaRepository.registerPushToken(token) } }
    }

    /**
     * Before the session is cleared: stop sending this account's notices to this phone. The access token is
     * captured now because the request lands after the session is gone, which is why it cannot use RumaApi.
     */
    fun onSignOut() {
        val access = RumaApi.session?.accessToken ?: return
        if (!firebaseReady) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                scope.launch {
                    runCatching {
                        val c = URL("${SupabaseConfig.URL}/rest/v1/rpc/unregister_push_token").openConnection() as HttpURLConnection
                        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 8000; c.readTimeout = 8000
                        c.setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
                        c.setRequestProperty("Authorization", "Bearer $access")
                        c.setRequestProperty("Content-Type", "application/json")
                        // FCM tokens are [A-Za-z0-9_:.-] (the RPC rejects anything else), so no escaping is needed.
                        c.outputStream.use { it.write("""{"p_token":"$token"}""".toByteArray()) }
                        c.responseCode; c.disconnect()
                    }
                }
            }
        }
    }

    /**
     * Reads a tapped notice out of the launch intent. Both ways in carry the same extras: the system tray
     * (app closed: FCM copies the message data into the launcher intent) and [NotificationHelper.showPickup].
     */
    fun handleIntent(intent: Intent?) {
        val extras = intent?.extras ?: return
        if (extras.getString(EXTRA_REFERENCE_TYPE) != "pickup") return
        val id = extras.getString(EXTRA_REFERENCE_ID)?.takeIf { it.isNotBlank() } ?: return
        openPickupId = id
        // An Activity recreated later (rotation, process restore) must not reopen the same pickup.
        intent.removeExtra(EXTRA_REFERENCE_ID)
    }
}

/** Receives FCM messages and token rotations. */
class RumaPushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = RumaPush.register(token)

    /** With the app open the system does not draw FCM notifications: draw it on the pickups channel. */
    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification ?: return
        val pickupId = message.data[RumaPush.EXTRA_REFERENCE_ID]?.takeIf {
            it.isNotBlank() && message.data[RumaPush.EXTRA_REFERENCE_TYPE] == "pickup"
        }
        NotificationHelper.showPickup(
            applicationContext,
            title = n.title ?: "Ruma",
            body = n.body.orEmpty(),
            tag = pickupId ?: message.messageId,
            pickupId = pickupId,
        )
    }
}
