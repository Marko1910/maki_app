package pe.ruma.app.ui.screens.rider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.ChoiceCard
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.GlassChip
import pe.ruma.app.ui.components.RumaBottomAction
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaSkeleton
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

/** A completed stop still in the vehicle, waiting to be handed over. */
data class DeliveryStopUi(
    val id: String,
    val code: String,
    val name: String,
    val address: String,
    val materials: String,
    val earnings: String,
    val selected: Boolean,
)

data class DeliveryCenterUi(
    val id: String,
    val name: String,
    val address: String,
    val isOpen: Boolean,
    val selected: Boolean,
)

/**
 * A load already handed over. [status] stays raw (`delivery_status`) because the
 * colour has to follow the money: only `liquidada` means the rider actually got paid.
 */
data class DeliveryRecordUi(
    val id: String,
    val status: String,
    val code: String,
    val statusLabel: String,
    val centerName: String,
    val detail: String,
    val amount: String,
)

data class RiderDeliveryUiState(
    val stops: List<DeliveryStopUi> = emptyList(),
    val centers: List<DeliveryCenterUi> = emptyList(),
    val recent: List<DeliveryRecordUi> = emptyList(),
    val selectedLabel: String = "",
    val selectedEarnings: String = "",
    /** False while nothing is ticked or no acopio is chosen, so the CTA never fails on tap. */
    val canSend: Boolean = false,
    val hint: String = "",
)

/**
 * Closes the chain the rider's work hangs on: a completed stop only pays once the
 * acopio weighs it, and nothing weighed anything until the rider opened a delivery.
 * Choosing which stops travel matters — a rider can make two trips in one shift, and
 * the fees of the stops left behind must not ride on the first receipt.
 */
@Composable
fun RiderDeliveryScreen(
    state: RiderDeliveryUiState? = null,
    busy: Boolean = false,
    error: String? = null,
    onBack: () -> Unit = {},
    onToggleStop: (String) -> Unit = {},
    onPickCenter: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().rumaScreen(RumaColors.Rider).statusBarsPadding()) {
        DetailHeader(title = "Entregar en acopio", onBack = onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            when {
                error != null -> item { RumaError(error, onRetry, accent = RumaColors.Rider) }
                state == null -> item { RumaSkeleton(listOf(96.dp, 72.dp, 72.dp), label = "Cargando tu carga…") }
                else -> {
                    item { SectionLabel("Recojos que llevas") }
                    if (state.stops.isEmpty()) {
                        item {
                            RumaEmpty(
                                Icons.Filled.Inventory2,
                                "No llevas recojos sin entregar",
                                "Cuando cierres un recojo aparecerá aquí para llevarlo al acopio.",
                                accent = RumaColors.Rider,
                            )
                        }
                    } else {
                        item { RumaList(state.stops) { stop -> StopRow(stop, onClick = { onToggleStop(stop.id) }) } }
                        item { SectionLabel("¿A qué acopio vas?") }
                        if (state.centers.isEmpty()) {
                            item { Text("No hay acopios disponibles", color = RumaColors.Text2, style = RumaType.body) }
                        }
                        items(state.centers, key = { it.id }) { center ->
                            ChoiceCard(
                                center.name, selected = center.selected, subtitle = center.address,
                                accent = RumaColors.Rider,
                                trailing = {
                                    if (center.isOpen) StatusChip("Abierto", RumaColors.Rider)
                                    else GlassChip("Cerrado", color = RumaColors.Text2)
                                },
                            ) { onPickCenter(center.id) }
                        }
                    }
                    if (state.recent.isNotEmpty()) {
                        item { SectionLabel("Entregas anteriores") }
                        item { RumaList(state.recent) { RecordRow(it) } }
                    }
                }
            }
        }
        // Pinned, like Confirmar: at the end of the list it could sit under the system bar.
        if (error == null && state != null && state.stops.isNotEmpty()) {
            RumaBottomAction {
                if (state.selectedLabel.isNotBlank()) {
                    Text(
                        listOf(state.selectedLabel, state.selectedEarnings).filter { it.isNotBlank() }.joinToString(" · "),
                        color = RumaColors.Text, style = RumaType.bodySmall,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
                PrimaryButton(
                    label = if (busy) "Abriendo la entrega…" else "Salir hacia el acopio",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    enabled = state.canSend && !busy,
                    color = RumaColors.Rider,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSend,
                )
                if (state.hint.isNotBlank()) {
                    Text(
                        state.hint,
                        color = RumaColors.Text2, style = RumaType.bodySmall,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** A closed stop in the vehicle, ticked to travel on this delivery (PWA checkbox row). */
@Composable
private fun StopRow(stop: DeliveryStopUi, onClick: () -> Unit) {
    RumaRow(
        stop.name,
        modifier = Modifier.toggleable(value = stop.selected, role = Role.Checkbox, onValueChange = { onClick() }),
        subtitle = stop.address,
        leading = {
            if (stop.selected) GlassBadge(icon = Icons.Filled.Check, fill = RumaColors.Rider, content = RumaColors.OnDark, ring = RumaColors.Rider)
            else GlassBadge()
        },
        extra = {
            if (stop.materials.isNotBlank()) Text(stop.materials, color = RumaColors.Text, style = RumaType.bodySmall)
            Text(stop.code, color = RumaColors.Text3, style = RumaType.caption)
        },
        trailing = { Text(stop.earnings, color = RumaColors.Money, style = RumaType.rowTitle) },
    )
}

@Composable
private fun RecordRow(record: DeliveryRecordUi) {
    // Only `liquidada` has actually paid; anything earlier is still a promise.
    val tone = when (record.status) {
        "liquidada" -> RumaColors.Success
        "rechazada" -> RumaColors.Error
        "pesada" -> RumaColors.Money
        else -> RumaColors.Rider
    }
    RumaRow(
        record.code.ifBlank { "Entrega" },
        subtitle = listOf(record.centerName, record.detail).filter { it.isNotBlank() }.joinToString(" · "),
        extra = { StatusChip(record.statusLabel, tone) },
        trailing = if (record.amount != "—") {
            { Text(record.amount, color = RumaColors.Money, style = RumaType.rowTitle) }
        } else null,
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1200)
@Composable
private fun RiderDeliveryPreview() {
    RumaTheme {
        RiderDeliveryScreen(state = SampleDelivery)
    }
}

private val SampleDelivery = RiderDeliveryUiState(
    stops = listOf(
        DeliveryStopUi(
            "1", "PU-979FD1A9", "Familia Quispe", "Av. Las Flores 231",
            "4 PET · 2 ALU", "S/2.64", selected = true,
        ),
        DeliveryStopUi(
            "2", "PU-9E770CC2", "Familia Rojas", "Jr. Cusco 118",
            "6 PET · 1 CAR", "S/2.70", selected = true,
        ),
        DeliveryStopUi(
            "3", "PU-8D7E0875", "Familia Lima", "Calle Union 44",
            "3 VID", "S/2.63", selected = false,
        ),
    ),
    centers = listOf(
        DeliveryCenterUi("c1", "Centro de Acopio SJL Norte", "Av. Canto Grande 1200", isOpen = true, selected = true),
        DeliveryCenterUi("c2", "Acopio Zarate", "Jr. Tacna 880", isOpen = false, selected = false),
    ),
    recent = listOf(
        DeliveryRecordUi(
            "d1", "liquidada", "ENT-000006", "Liquidada · te pagaron",
            "Centro de Acopio SJL Norte", "2 recojos · 29.7 kg · 16 Oct", "S/6.12",
        ),
        DeliveryRecordUi(
            "d2", "pesada", "ENT-000008", "Pesada · esperando liquidacion",
            "Centro de Acopio SJL Norte", "1 recojo · 16 Oct", "—",
        ),
    ),
    selectedLabel = "2 recojos",
    selectedEarnings = "S/5.34 por cobrar cuando el acopio pese la carga",
    canSend = true,
    hint = "El acopio pesa la carga y recién ahí se pagan tus recojos y los puntos del vecino.",
)
