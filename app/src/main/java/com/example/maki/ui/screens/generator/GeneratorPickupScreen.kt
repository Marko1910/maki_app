package com.example.maki.ui.screens.generator

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.HeroCard
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MakiLoading
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.makiCard
import com.example.maki.ui.components.makiOutlined
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

private val SamplePickupForm = PickupFormUi(
    items = listOf(
        PickupFormItemUi("PET", "botellas PET", 8),
        PickupFormItemUi("ALU", "latas de aluminio", 2),
    ),
    addressLine = "Jr. Las Flores 123, SJL",
)

@Composable
fun GeneratorPickupScreen(
    form: PickupFormUi? = null,
    busy: Boolean = false,
    /** Street of the device fix; null when there is no GPS position (the address applies). */
    gpsLine: String? = "Avenida Industrial 47, Cartavio",
    /** True while the phone is still getting a fix. */
    locating: Boolean = false,
    onBack: () -> Unit = {},
    onConfirm: (List<Pair<String, Int>>, String) -> Unit = { _, _ -> },
    onChangeAddress: () -> Unit = {},
    onUseMyLocation: () -> Unit = {},
) {
    val f = form ?: SamplePickupForm
    val quantities = remember(f) { f.items.map { it.initialQty }.toMutableStateList() }
    var slot by remember { mutableIntStateOf(0) }
    val slots = listOf(
        Triple("Hoy", "3:00 - 6:00 pm", true),
        Triple("Mañana", "9:00 - 12:00 m", false),
        Triple("Mañana", "2:00 - 5:00 pm", false),
    )

    Column(Modifier.fillMaxSize().makiScreen().statusBarsPadding()) {
        DetailHeader("Programar Recojo", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = MakiSpace.gutter, end = MakiSpace.gutter,
                top = MakiSpace.xs, bottom = MakiSpace.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
        ) {
            item {
                HeroCard(
                    accent = MakiColors.Gen,
                    overline = "ÍTEMS A RECOGER",
                    metric = quantities.sum().toString(),
                    support = "Programar Recojo",
                )
            }
            item { SectionLabel("ÍTEMS A RECOGER") }
            if (f.items.isEmpty()) {
                item {
                    MakiEmpty(Icons.Filled.Inventory2, "No hay ítems para recoger", accent = MakiColors.Gen)
                }
            }
            itemsIndexed(f.items) { i, item ->
                PickupItemRow(
                    pickupItemIcon(item.code), item.label, quantities[i],
                    onPlus = { quantities[i] = quantities[i] + 1 },
                    onMinus = { if (quantities[i] > 0) quantities[i] = quantities[i] - 1 },
                )
            }

            item { SectionLabel("PUNTO DE RECOJO") }
            item {
                PickupPlaceCard(
                    gpsLine = gpsLine,
                    locating = locating,
                    addressLine = f.addressLine,
                    onUseMyLocation = onUseMyLocation,
                    onChangeAddress = onChangeAddress,
                )
            }
            item { SectionLabel("VENTANA HORARIA") }
            itemsIndexed(slots) { i, (day, time, _) ->
                SlotRow(day, time, selected = slot == i) { slot = i }
            }

            item {
                Row(
                    Modifier.clip(RoundedCornerShape(MakiRadius.chip)).background(MakiColors.MoneyTint).fillMaxWidth().padding(horizontal = MakiSpace.lg, vertical = MakiSpace.md),
                    horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Info, null, tint = MakiColors.Money, modifier = Modifier.size(MakiSpace.lg))
                    Text("Los puntos se acreditarán cuando el Eco-Rider confirme la recolección.",
                        color = MakiColors.Money, style = MakiType.label)
                }
            }
            item {
                PrimaryButton(
                    if (busy) "PROCESANDO…" else "CONFIRMAR RECOJO",
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Check,
                    enabled = !busy,
                    onClick = {
                        val selections = f.items.mapIndexed { i, item -> item.materialId to quantities[i] }
                        val (day, time, _) = slots[slot]
                        onConfirm(selections, "$day $time")
                    },
                )
            }
        }
    }
}

/**
 * Where the rider will come: the phone's GPS position when there is one (the family
 * asks from home), the registered address otherwise — and it says which.
 */
@Composable
private fun PickupPlaceCard(
    gpsLine: String?,
    locating: Boolean,
    addressLine: String,
    onUseMyLocation: () -> Unit,
    onChangeAddress: () -> Unit,
) {
    Row(
        Modifier.makiCard()
            .fillMaxWidth().padding(MakiSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            if (gpsLine != null) Icons.Filled.MyLocation else Icons.Filled.Place,
            MakiColors.Gen, MakiColors.GenTint, boxSize = MakiSpace.xl * 2, iconSize = MakiSpace.xl, radius = MakiRadius.badge,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs)) {
            Text(gpsLine ?: addressLine, color = MakiColors.Text, style = MakiType.rowTitle)
            when {
                gpsLine != null -> Text(
                    "Ubicación GPS de tu celular · el Eco-Rider vendrá aquí",
                    color = MakiColors.Success, style = MakiType.label,
                )
                locating -> MakiLoading("Obteniendo tu ubicación…", accent = MakiColors.Gen)
                else -> Text(
                    "Sin GPS: usaremos tu dirección registrada",
                    color = MakiColors.Text2, style = MakiType.label,
                )
            }
            Column {
                if (gpsLine == null && !locating) {
                    Text("Usar mi ubicación", color = MakiColors.Gen, style = MakiType.label,
                        modifier = Modifier.clickable(onClickLabel = "Usar mi ubicación", role = Role.Button, onClick = onUseMyLocation)
                            .heightIn(min = MakiSpace.xl * 2 + MakiSpace.sm).padding(vertical = MakiSpace.lg))
                }
                Text("Cambiar dirección", color = MakiColors.Gen, style = MakiType.bodySmall,
                    modifier = Modifier.clickable(onClickLabel = "Cambiar dirección", role = Role.Button, onClick = onChangeAddress)
                        .heightIn(min = MakiSpace.xl * 2 + MakiSpace.sm).padding(vertical = MakiSpace.lg))
            }
        }
    }
}

private fun pickupItemIcon(code: String): ImageVector = when (code) {
    "PET", "VID" -> Icons.Filled.LocalDrink
    else -> Icons.Filled.Inventory2
}

@Composable
private fun PickupItemRow(icon: ImageVector, name: String, qty: Int, onPlus: () -> Unit, onMinus: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().makiCard().padding(MakiSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, MakiColors.Gen, MakiColors.GenTint)
            Text("$qty $name", color = MakiColors.Text, style = MakiType.rowTitle, modifier = Modifier.weight(1f))
        }
        Row(
            Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(Icons.Filled.Remove, filled = false, label = "Quitar una unidad de $name", onClick = onMinus)
            Text("$qty", color = MakiColors.Text, style = MakiType.metricMedium,
                textAlign = TextAlign.Center, modifier = Modifier.width(MakiSpace.xxl))
            StepButton(Icons.Filled.Add, filled = true, label = "Añadir una unidad de $name", onClick = onPlus)
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, filled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(MakiSpace.xl * 2 + MakiSpace.sm).clip(CircleShape)
            .background(if (filled) MakiColors.Gen else MakiColors.Bg)
            .then(if (!filled) Modifier.makiOutlined(MakiColors.Border, MakiRadius.pill) else Modifier)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (filled) MakiColors.OnDark else MakiColors.Text, modifier = Modifier.size(MakiSpace.lg)) }
}

@Composable
private fun SlotRow(day: String, time: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .makiOutlined(if (selected) MakiColors.Gen else MakiColors.Border, MakiRadius.badge)
            .background(if (selected) MakiColors.GenTint else MakiColors.Surface)
            .semantics { contentDescription = "Elegir recojo: $day $time" }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = MakiSpace.xl * 2 + MakiSpace.sm)
            .padding(MakiSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            null, tint = if (selected) MakiColors.Gen else MakiColors.Text2,
            modifier = Modifier.size(MakiSpace.xl + MakiSpace.xs),
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(day, color = if (selected) MakiColors.Gen else MakiColors.Text, style = MakiType.rowTitle)
            Text(time, color = MakiColors.Text2, style = MakiType.body)
        }
        if (selected) Icon(Icons.Filled.CheckCircle, null, tint = MakiColors.Gen, modifier = Modifier.size(MakiSpace.xl))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun PickupPreview() {
    MAKITheme { GeneratorPickupScreen() }
}
