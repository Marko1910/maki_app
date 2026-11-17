package pe.ruma.app.ui.screens.generator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pe.ruma.app.data.RumaRepository
import pe.ruma.app.data.MyPlanDto
import pe.ruma.app.data.PlanDto
import pe.ruma.app.data.PlanFeatureDto
import pe.ruma.app.data.PlanPaymentDto
import pe.ruma.app.data.PlanPendingDto
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.HeroCard
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaSkeleton
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.SecondaryButton
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType
import kotlinx.coroutines.launch

/** Planes: the app asks (request, trial, cancel) and shows what rpc/my_plan says; the database grants every benefit. */
class PlansViewModel : ViewModel() {
    var plans by mutableStateOf<List<PlanDto>?>(null)
        private set
    var mine by mutableStateOf<MyPlanDto?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    /** The database's own sentence after an action, shown under the hero. */
    var notice by mutableStateOf<String?>(null)
        private set

    fun load(force: Boolean = false) {
        if (busy || (mine != null && !force)) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                plans = RumaRepository.loadPlans()
                mine = RumaRepository.loadMyPlan()
            } catch (e: Exception) { error = plain(e, "No pudimos cargar los planes.") }
            finally { busy = false }
        }
    }

    fun request(plan: String, period: String) = act { RumaRepository.requestPlan(plan, period); null }
    fun startTrial(plan: String) = act { RumaRepository.startPlanTrial(plan); "Tu prueba gratis empezó." }
    fun cancel(subscriptionId: String) = act { RumaRepository.cancelMyPlan(subscriptionId) }

    private fun act(call: suspend () -> String?) {
        if (busy) return
        busy = true
        notice = null
        viewModelScope.launch {
            try {
                notice = call()
                mine = RumaRepository.loadMyPlan()
            } catch (e: Exception) { notice = plain(e, "No pudimos completar la acción.") }
            finally { busy = false }
        }
    }

    // RumaApi prefixes "Error (400): "; the database already speaks Spanish.
    private fun plain(e: Exception, fallback: String) = e.message?.substringAfter("): ")?.takeIf { it.isNotBlank() } ?: fallback
}

@Composable
fun GeneratorPlansScreen(vm: PlansViewModel, onBack: () -> Unit) {
    PlansContent(
        plans = vm.plans, mine = vm.mine, error = vm.error, busy = vm.busy, notice = vm.notice,
        onBack = onBack, onRetry = { vm.load(force = true) },
        onRequest = vm::request, onTrial = vm::startTrial, onCancel = vm::cancel,
    )
}

private fun soles(v: Double) = "S/ " + String.format(java.util.Locale("es", "PE"), "%.2f", v)

private fun until(ts: String?): String = ts?.let { RumaTime.dayMonth(it) }.orEmpty()

@Composable
private fun PlansContent(
    plans: List<PlanDto>?,
    mine: MyPlanDto?,
    error: String?,
    busy: Boolean,
    notice: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRequest: (String, String) -> Unit,
    onTrial: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().rumaScreen().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = RumaSpace.gutter, vertical = RumaSpace.lg),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
    ) {
        item { DetailHeader("Ruma Plus", onBack, subtitle = "Más comodidad, no más puntos") }
        if (mine == null || plans == null) {
            if (error != null) item { RumaError(error, onRetry) }
            else item { RumaSkeleton(listOf(140.dp, 220.dp), label = "Cargando planes…") }
        } else {
            val active = mine.active
            item {
                HeroCard(
                    RumaColors.Gen, "TU PLAN", active?.name ?: "Ruma",
                    when {
                        active == null -> "Todo lo esencial es gratis: recojo, puntos y cobro."
                        active.cancelled_at != null -> "No se renovará. Tus beneficios siguen hasta el ${until(active.ends_at)}."
                        else -> "Beneficios hasta el ${until(active.ends_at)}. No se renueva solo."
                    },
                )
            }
            notice?.let { item { Text(it, style = RumaType.bodySmall, color = RumaColors.Text2) } }
            active?.takeIf { it.cancelled_at == null }?.let { a ->
                item { SecondaryButton("No renovar mi plan", Modifier.fillMaxWidth(), enabled = !busy, onClick = { onCancel(a.id) }) }
            }
            mine.pending?.let { p -> item { PendingCard(p, mine.payment, busy, onCancel) } }
            items(plans.filter { it.price_month > 0 }, key = { it.code }) { plan ->
                PlanCard(plan, mine, busy, onRequest, onTrial)
            }
        }
    }
}

@Composable
private fun PendingCard(p: PlanPendingDto, pay: PlanPaymentDto?, busy: Boolean, onCancel: (String) -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.sm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
            StatusChip("PAGO EN REVISIÓN", RumaColors.Money)
            Text(soles(p.amount), style = RumaType.rowTitle, color = RumaColors.Text)
        }
        Text("Tu código: ${p.reference}", style = RumaType.rowTitle, color = RumaColors.Text)
        val number = pay?.yape_number
        Text(
            if (number == null) "El número de Yape se publicará pronto. Tu solicitud ya quedó guardada."
            else "Yapea ${soles(p.amount)} al $number${pay.yape_holder?.let { " ($it)" }.orEmpty()} y escribe tu código en el mensaje. " +
                pay.instructions.orEmpty(),
            style = RumaType.bodySmall, color = RumaColors.Text2,
        )
        SecondaryButton("Retirar solicitud", Modifier.fillMaxWidth(), enabled = !busy, onClick = { onCancel(p.id) })
    }
}

@Composable
private fun PlanCard(
    plan: PlanDto, mine: MyPlanDto, busy: Boolean,
    onRequest: (String, String) -> Unit, onTrial: (String) -> Unit,
) {
    SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.md) {
        Text(plan.name, style = RumaType.cardTitle, color = RumaColors.Text)
        plan.tagline?.let { Text(it, style = RumaType.bodySmall, color = RumaColors.Text2) }
        Text(
            soles(plan.price_month) + " al mes" + (plan.price_year?.let { " · ${soles(it)} al año" } ?: ""),
            style = RumaType.rowTitle, color = RumaColors.Text,
        )
        SectionLabel("Incluye")
        plan.features.forEach { f ->
            Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(f.title, style = RumaType.rowTitle, color = RumaColors.Text)
                    f.detail?.let { Text(it, style = RumaType.bodySmall, color = RumaColors.Text2) }
                }
                if (f.status == "pronto") StatusChip("PRONTO", RumaColors.Text2)
            }
        }
        // A pending request already shows its own card; asking again would only swap it.
        if (mine.pending == null) {
            val trial = mine.trial_available && plan.trial_days > 0
            Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                if (trial) PrimaryButton("Probar ${plan.trial_days} días gratis", Modifier.fillMaxWidth(), enabled = !busy, onClick = { onTrial(plan.code) })
                val monthly = "Pedir mensual · ${soles(plan.price_month)}"
                if (trial) SecondaryButton(monthly, Modifier.fillMaxWidth(), enabled = !busy, onClick = { onRequest(plan.code, "month") })
                else PrimaryButton(monthly, Modifier.fillMaxWidth(), enabled = !busy, onClick = { onRequest(plan.code, "month") })
                plan.price_year?.let {
                    SecondaryButton("Pedir anual · ${soles(it)}", Modifier.fillMaxWidth(), enabled = !busy, onClick = { onRequest(plan.code, "year") })
                }
            }
        }
    }
}

private val SamplePlan = PlanDto(
    "PLUS", "Ruma Plus", "Más comodidad, no más puntos", 4.9, 39.9, 14,
    listOf(PlanFeatureDto("Cobra desde S/ 1", "Sin esperar a juntar S/ 5"), PlanFeatureDto("Plan familiar", null, "pronto")),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 1000)
@Composable
private fun PlansPreview() {
    RumaTheme {
        PlansContent(listOf(SamplePlan), MyPlanDto(trial_available = true), null, false, null, {}, {}, { _, _ -> }, {}, {})
    }
}
