package pe.ruma.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * "Salgo a las 3:00 pm": at that hour the shift turns itself on — `is_available`
 * goes true, so dispatch can hand this rider stops — and the phone says so.
 *
 * The rider should not have to remember to flip a switch to start earning; the
 * only thing they promised was an hour.
 */
class RiderShiftWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        RumaPrefs.shiftStartAt = null
        if (!RumaApi.isSignedIn) runCatching { RumaRepository.restoreSession() }

        // Whoever booked the shift owns it. Another rider signing in on the same phone
        // must not be put on duty by a plan that was never theirs, and should not even
        // hear about it.
        val owner = inputData.getString(KEY_RIDER)
        val current = RumaApi.session?.userId
        if (owner != null && current != null && owner != current) return Result.success()

        // Only the rider who booked it goes on duty, and the write carries their id so a
        // sign-in landing mid-request cannot redirect it. A shift booked by an older
        // build has no owner at all: then this is a reminder and nothing more. No session
        // still reports — that rider is expecting to go out and needs to open the app.
        val ok = owner != null && owner == current &&
            runCatching { RumaRepository.setRiderAvailability(true, owner) }.isSuccess
        NotificationHelper.show(
            applicationContext,
            if (ok) "Tu turno empezó 🛵" else "¿Sales a ruta?",
            if (ok) "Ya estás en turno: te pueden asignar recojos. Revisa el tablero."
            else "Abre Ruma y activa tu turno para recibir recojos.",
            id = NOTIFICATION_ID,
        )
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "rider_shift_start"
        private const val NOTIFICATION_ID = 2001
        private const val KEY_RIDER = "rider_id"

        /** Books the shift for [atMillis]; a later call replaces the previous plan. */
        fun schedule(context: Context, atMillis: Long, riderId: String? = RumaApi.session?.userId) {
            val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            RumaPrefs.shiftStartAt = atMillis
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RiderShiftWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    // Stamped with its owner: the work survives a sign-out, the claim doesn't.
                    .setInputData(workDataOf(KEY_RIDER to riderId))
                    .build(),
            )
        }

        fun cancel(context: Context) {
            RumaPrefs.shiftStartAt = null
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
