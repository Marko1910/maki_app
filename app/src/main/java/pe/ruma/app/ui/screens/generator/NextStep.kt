package pe.ruma.app.ui.screens.generator

import pe.ruma.app.navigation.GenRoutes
import java.util.Locale

/**
 * The home's "siguiente mejor acción": the one thing worth doing now, read from what the
 * family already has on screen. Twin of `pwa/src/lib/nextStep.ts`; keep both in step.
 *
 * Order matters: what is happening now (a rider in flight), then what was left half done
 * (a scan with no pickup), then money that can be collected, then progress. No urgency and
 * no guilt: every line says what the step gets you, never what you lose.
 */
data class NextStep(
    val kind: Kind,
    val title: String,
    val body: String,
    val action: String,
    val route: String,
) {
    enum class Kind { LIVE, PICKUP, CASH, START_WEEK, NEAR_GOAL, NEAR_CASH, REGISTER }
}

/** Points worth showing "ya casi cobras": from half of the minimum up. */
private const val NEAR_CASH_SHARE = 0.5

fun nextStep(
    /** Title/line of the pickup in flight, as the home already builds them; null with none. */
    liveTitle: String?,
    liveLine: String = "",
    /** A scan saved this session that has no pickup yet. */
    unpickedScan: Boolean = false,
    balance: Int = 0,
    /** min_points of the cheapest active cash option (Yape/Plin); null when unknown. */
    cashMin: Int? = null,
    /** Days with a scan this week, and days left to the goal; null when the week did not load. */
    weekDays: Int? = null,
    weekLeft: Int? = null,
): NextStep = when {
    liveTitle != null -> NextStep(NextStep.Kind.LIVE, liveTitle, liveLine, "Ver mi recojo", GenRoutes.TRACKING)
    unpickedScan -> NextStep(
        NextStep.Kind.PICKUP, "Pide tu recojo",
        "Tu escaneo quedó guardado. Elige la hora y un Eco-Rider pasa por tus residuos.",
        "Pedir recojo", GenRoutes.PICKUP,
    )
    cashMin != null && cashMin > 0 && balance >= cashMin -> NextStep(
        NextStep.Kind.CASH, "Ya puedes cobrar",
        "Tienes ${soles(balance)} disponibles para canjear por Yape o Plin.",
        "Ver mi wallet", GenRoutes.WALLET,
    )
    weekDays == 0 -> NextStep(
        NextStep.Kind.START_WEEK, "Registra tus residuos",
        "Una foto de lo que ya juntaste y tu semana arranca.",
        "Registrar residuos", GenRoutes.CAMERA,
    )
    weekLeft == 1 -> NextStep(
        NextStep.Kind.NEAR_GOAL, "Te falta 1 día para tu meta",
        "Registra lo que juntaste hoy y completas tu semana.",
        "Registrar residuos", GenRoutes.CAMERA,
    )
    cashMin != null && cashMin > 0 && balance >= cashMin * NEAR_CASH_SHARE -> NextStep(
        NextStep.Kind.NEAR_CASH, "Tu primer cobro está cerca",
        "Te faltan ${groupInt(cashMin - balance)} pts para cobrar ${soles(cashMin)}.",
        "Registrar residuos", GenRoutes.CAMERA,
    )
    else -> NextStep(
        NextStep.Kind.REGISTER, "Registra lo de hoy",
        if (weekLeft == 0) "Ya cumpliste tu meta de la semana. Si juntaste más, súmalo cuando quieras."
        else "Hoy puedes registrar los residuos que ya tienes.",
        "Registrar residuos", GenRoutes.CAMERA,
    )
}

private fun soles(points: Int) = "S/%.2f".format(Locale.US, points / POINTS_PER_SOL)
