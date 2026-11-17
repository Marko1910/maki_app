package pe.ruma.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pe.ruma.app.R

/** Posts Ruma's eco-tip / price-alert notifications (lock screen + tray). */
object NotificationHelper {
    private const val CHANNEL_ID = "ruma_eco_tips"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Consejos y precios Ruma", NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Frases motivadoras y alertas de precio de reciclables" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** Pickup news (rider assigned, on the way, collected, points credited): high importance, its own switch. */
    const val PICKUP_CHANNEL_ID = "ruma_recojos"

    fun ensurePickupChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(PICKUP_CHANNEL_ID, "Tus recojos", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Cuando un Eco-Rider toma, recoge o entrega tu recojo, y cuando se acreditan tus puntos" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /**
     * One live notice per pickup: the same [tag] replaces the previous step instead of stacking.
     * Tapping it opens [pickupId] (RumaPush.handleIntent), or just the app when there is none.
     */
    fun showPickup(context: Context, title: String, body: String, tag: String?, pickupId: String? = null) {
        ensurePickupChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val launch = android.content.Intent(context, pe.ruma.app.MainActivity::class.java).apply {
            // MainActivity is singleTop: an open app gets onNewIntent instead of a second copy.
            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (pickupId != null) {
                putExtra(RumaPush.EXTRA_REFERENCE_TYPE, "pickup")
                putExtra(RumaPush.EXTRA_REFERENCE_ID, pickupId)
            }
        }
        // One PendingIntent per pickup: with a shared request code the newest extras would replace the others'.
        val open = android.app.PendingIntent.getActivity(
            context, (pickupId ?: tag).hashCode(), launch,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, PICKUP_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(tag, 2001, notification)
    }

    fun show(context: Context, title: String, body: String, id: Int = 1001) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return  // user hasn't granted notifications

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
