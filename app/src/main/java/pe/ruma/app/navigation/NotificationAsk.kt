package pe.ruma.app.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Asks for the notification permission at the moment it is worth something to the person: the
 * family right after "Confirmar recojo" (a rider is now coming), the rider when the shift starts
 * (pickups will be assigned). It used to be asked cold on the first launch, before the app had
 * shown any reason to say yes.
 *
 * Returns the function to call at that moment. It does nothing below Android 13 (no runtime
 * permission) or once granted; Android itself stops showing the dialog after two refusals, so
 * calling it on every pickup never nags.
 */
@Composable
fun rememberNotificationAsk(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
