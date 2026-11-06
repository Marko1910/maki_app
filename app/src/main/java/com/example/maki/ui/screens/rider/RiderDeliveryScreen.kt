package com.example.maki.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.HeroCard
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MakiLoading
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.StatusChip
import com.example.maki.ui.components.makiCard
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

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
    Column(Modifier.fillMaxSize().makiScreen(MakiColors.Rider).statusBarsPadding()) {
        DetailHeader(title = "Entregar en acopio", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = MakiSpace.gutter, end = MakiSpace.gutter, top = MakiSpace.sm, bottom = MakiSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
        ) {
            when {
                error != null -> item { MakiError(error, onRetry) }
                state == null -> item { MakiLoading("Cargando tu carga…", accent = MakiColors.Rider) }
                else -> {
                    item { CargaHero(state) }
                    if (state.stops.isEmpty()) {
                        item {
                            MakiEmpty(
                                Icons.Filled.Inventory2,
                                "No llevas recojos sin entregar",
                                "Cuando cierres un recojo aparecerá aquí para llevarlo al acopio.",
                                accent = MakiColors.Rider,
                            )
                        }
                    } else {
                        item { SectionLabel("RECOJOS QUE LLEVAS") }
                        items(state.stops, key = { it.id }) { stop ->
                            StopRow(stop, onClick = { onToggleStop(stop.id) })
                        }
                        item { SectionLabel("¿A QUÉ ACOPIO VAS?") }
                        if (state.centers.isEmpty()) {
                            item {
                                MakiEmpty(Icons.Filled.Warehouse, "No hay acopios disponibles",
                                    accent = MakiColors.Rider)
                            }
                        }
                        items(state.centers, key = { it.id }) { center ->
                            CenterRow(center, onClick = { onPickCenter(center.id) })
                        }
                        item {
                            PrimaryButton(
                                label = if (busy) "Abriendo la entrega…" else "Salir hacia el acopio",
                                icon = Icons.Filled.LocalShipping,
                                enabled = state.canSend && !busy,
                                color = MakiColors.Rider,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onSend,
                            )
                        }
                        item {
                            Text(
                                state.hint,
                                color = MakiColors.Text2, style = MakiType.label,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (state.recent.isNotEmpty()) {
                        item { SectionLabel("ENTREGAS ANTERIORES") }
                        items(state.recent, key = { it.id }) { record -> RecordRow(record) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CargaHero(state: RiderDeliveryUiState) {
    HeroCard(
        accent = MakiColors.Rider,
        overline = "Seleccionado para esta entrega",
        metric = state.selectedLabel,
        support = state.selectedEarnings,
    )
}

@Composable
private fun StopRow(stop: DeliveryStopUi, onClick: () -> Unit) {
    Row(
        Modifier.makiCard()
            .then(
                if (stop.selected) Modifier.border(MakiSpace.xs / 2, MakiColors.Rider, RoundedCornerShape(MakiRadius.card))
                else Modifier,
            )
            .fillMaxWidth().heightIn(min = MakiSpace.lg * 3)
            .semantics { toggleableState = if (stop.selected) ToggleableState.On else ToggleableState.Off }
            .clickable(
                onClickLabel = if (stop.selected) "Quitar de la entrega" else "Incluir en la entrega",
                role = Role.Checkbox,
                onClick = onClick,
            )
            .padding(MakiSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(MakiSpace.xxl).clip(CircleShape)
                .background(if (stop.selected) MakiColors.Rider else MakiColors.Border),
            contentAlignment = Alignment.Center,
        ) {
            if (stop.selected) {
                Icon(Icons.Filled.Check, null, tint = MakiColors.OnDark, modifier = Modifier.size(MakiSpace.lg))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs / 2)) {
            Text(
                stop.name, color = MakiColors.Text, style = MakiType.cardTitle,
            )
            Text(
                stop.address, color = MakiColors.Text2, style = MakiType.label,
            )
            Text(
                stop.materials, color = MakiColors.Rider, style = MakiType.label,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(MakiSpace.xs / 2)) {
            Text(
                stop.earnings, color = MakiColors.Success, style = MakiType.rowTitle,
            )
            Text(
                stop.code, color = MakiColors.Text2, style = MakiType.caption,
            )
        }
    }
}

@Composable
private fun CenterRow(center: DeliveryCenterUi, onClick: () -> Unit) {
    Row(
        Modifier.makiCard()
            .then(
                if (center.selected) Modifier.border(MakiSpace.xs / 2, MakiColors.Admin, RoundedCornerShape(MakiRadius.card))
                else Modifier,
            )
            .fillMaxWidth().heightIn(min = MakiSpace.lg * 3)
            .semantics { selected = center.selected }
            .clickable(onClickLabel = "Elegir este acopio", role = Role.RadioButton, onClick = onClick)
            .padding(MakiSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(MakiSpace.xl * 2).clip(RoundedCornerShape(MakiRadius.badge))
                .background(if (center.selected) MakiColors.Admin else MakiColors.Border),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Warehouse, null,
                tint = if (center.selected) MakiColors.OnDark else MakiColors.Text2,
                modifier = Modifier.size(MakiSpace.xl),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs / 2)) {
            Text(
                center.name, color = MakiColors.Text, style = MakiType.cardTitle,
            )
            Text(
                center.address, color = MakiColors.Text2, style = MakiType.label,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(MakiSpace.xs)) {
            StatusChip(if (center.isOpen) "Abierto" else "Cerrado",
                if (center.isOpen) MakiColors.Success else MakiColors.Text2)
            if (center.selected) StatusChip("Seleccionado", MakiColors.Rider)
        }
    }
}

@Composable
private fun RecordRow(record: DeliveryRecordUi) {
    // Only `liquidada` has actually paid; anything earlier is still a promise.
    val tone = when (record.status) {
        "liquidada" -> MakiColors.Success
        "rechazada" -> MakiColors.Error
        "pesada" -> MakiColors.Money
        else -> MakiColors.Rider
    }
    Row(
        Modifier.makiCard()
            .fillMaxWidth().padding(MakiSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs)) {
            Text(
                record.centerName, color = MakiColors.Text, style = MakiType.cardTitle,
            )
            Text(
                record.detail, color = MakiColors.Text2, style = MakiType.label,
            )
            StatusChip(record.statusLabel, tone)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(MakiSpace.xs / 2)) {
            Text(
                record.amount, color = tone, style = MakiType.rowTitle,
            )
            Text(
                record.code, color = MakiColors.Text2, style = MakiType.caption,
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1200)
@Composable
private fun RiderDeliveryPreview() {
    MAKITheme {
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
