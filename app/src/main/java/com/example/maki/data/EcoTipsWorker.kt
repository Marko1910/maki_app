package com.example.maki.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Background eco-nudge: posts a price alert when a recyclable's market price rose,
 * otherwise a motivational/comfort phrase. Reads the public price view anonymously,
 * so it works without a signed-in session. Scheduled daily + a one-shot demo run.
 *
 * ponytail: no dedup of repeat alerts yet — add a "last notified price" in MakiPrefs
 * if it gets spammy.
 */
class EcoTipsWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val (title, body) = buildMessage()
        NotificationHelper.show(applicationContext, title, body)
        return Result.success()
    }

    private suspend fun buildMessage(): Pair<String, String> {
        val riser = runCatching { MakiRepository.loadPrices() }.getOrNull()
            ?.filter { it.pct_change > 0 && it.price_per_kg > 0 }
            ?.maxByOrNull { it.pct_change }
        return if (riser != null) {
            "📈 Subió el precio del reciclable" to
                "El ${riser.name} subió ${"%.0f".format(riser.pct_change)}% (S/${"%.2f".format(riser.price_per_kg)}/kg). " +
                "¡Buen momento para reciclar antes de que baje!"
        } else {
            "MAKI ♻️" to EcoPhrases.random()
        }
    }
}

private val EcoPhrases = listOf(
    "Cada botella que reciclas es un respiro para el planeta 🌱",
    "Hoy es un gran día para sumar Eco-Puntos. ¡Tú puedes!",
    "Pequeños gestos, gran impacto: separa tus residuos hoy.",
    "Tu racha te espera 🔥 recicla algo hoy y mantén el ritmo.",
    "Reciclar también cuida tu bolsillo. Revisa los precios del día.",
)
