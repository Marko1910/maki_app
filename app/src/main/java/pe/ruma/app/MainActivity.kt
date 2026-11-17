package pe.ruma.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import pe.ruma.app.data.EcoTipsWorker
import pe.ruma.app.data.RumaPush
import pe.ruma.app.data.NotificationHelper
import pe.ruma.app.navigation.AppRoot
import pe.ruma.app.ui.theme.RumaTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // RumaPrefs is initialised in RumaApp: a worker can start the process with no Activity.
        NotificationHelper.ensureChannel(this)
        // The channel must exist before the first push: with the app closed the system draws it there.
        NotificationHelper.ensurePickupChannel(this)
        // The notification permission is not asked here, cold, any more: each Root asks when the
        // person has a reason to say yes (navigation/NotificationAsk.kt).
        // Opened from a pickup notice: the role's Root takes it to that pickup. Not on a recreation
        // (rotation), which would replay the same intent.
        if (savedInstanceState == null) RumaPush.handleIntent(intent)
        scheduleEcoTips()
        enableEdgeToEdge()
        setContent {
            RumaTheme {
                // Splash → onboarding/login → role apps.
                AppRoot()
            }
        }
    }

    /** The app was already open (singleTop) when a pickup notice was tapped. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        RumaPush.handleIntent(intent)
    }

    private fun scheduleEcoTips() {
        val wm = WorkManager.getInstance(this)
        // One-shot ~12s after launch so the eco-tip/price alert is visible during a demo.
        wm.enqueue(OneTimeWorkRequestBuilder<EcoTipsWorker>().setInitialDelay(12, TimeUnit.SECONDS).build())
        // Recurring daily nudge.
        wm.enqueueUniquePeriodicWork(
            "ruma_eco_tips",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<EcoTipsWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
