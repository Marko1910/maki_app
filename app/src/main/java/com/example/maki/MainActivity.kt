package com.example.maki

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.maki.data.EcoTipsWorker
import com.example.maki.data.MakiPrefs
import com.example.maki.data.NotificationHelper
import com.example.maki.navigation.AppRoot
import com.example.maki.ui.theme.MAKITheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MakiPrefs.init(this)
        NotificationHelper.ensureChannel(this)
        requestNotificationPermission()
        scheduleEcoTips()
        enableEdgeToEdge()
        setContent {
            MAKITheme {
                // Splash → onboarding/login → role apps.
                AppRoot()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun scheduleEcoTips() {
        val wm = WorkManager.getInstance(this)
        // One-shot ~12s after launch so the eco-tip/price alert is visible during a demo.
        wm.enqueue(OneTimeWorkRequestBuilder<EcoTipsWorker>().setInitialDelay(12, TimeUnit.SECONDS).build())
        // Recurring daily nudge.
        wm.enqueueUniquePeriodicWork(
            "maki_eco_tips",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<EcoTipsWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
