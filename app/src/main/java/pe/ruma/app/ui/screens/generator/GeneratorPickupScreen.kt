package pe.ruma.app.ui.screens.generator

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
import androidx.compose.ui.unit.dp
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
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import pe.ruma.app.data.RoutePlanner
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.text.input.KeyboardType
import pe.ruma.app.ui.components.RumaInputField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.HeroCard
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.IconBadge
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaLoading
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.rumaCard
import pe.ruma.app.ui.components.rumaOutlined
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

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
    /** The family's sack code, to write on the sack before the rider comes. */
    sackCode: String = "",
    /** Selections, time window, and the mobile number typed here (null when the account already has one). */
    onConfirm: (List<Pair<String, Int>>, String, String?) -> Unit = { _, _, _ -> },
    onChangeAddress: () -> Unit = {},
    onUseMyLocation: () -> Unit = {},
    /** The device fix the rider will come to; null while locating or without GPS. */
    here: RoutePlanner.LatLng? = null,
) {
    // Sample only for @Preview: on a device made-up PET/ALU counts could be sent as a real request.
    val f = form ?: SamplePickupForm.takeIf { androidx.compose.ui.platform.LocalInspectionMode.current } ?: run {
        Column(Modifier.fillMaxSize().rumaScreen().statusBarsPadding()) {
            DetailHeader("Programar Recojo", onBack)
            pe.ruma.app.ui.components.RumaSkeleton(
                listOf(120.dp, 20.dp, 72.dp, 72.dp, 160.dp),
                Modifier.padding(horizontal = RumaSpace.gutter), "Cargando tu recojo…",
            )
        }
        return
    }
    val quantities = remember(f) { f.items.map { it.initialQty }.toMutableStateList() }
    var slot by remember { mutableIntStateOf(0) }
    // Today's window closes for new requests an hour before it ends: a rider needs time to fit the stop in.
    val slots = remember {
        val hour = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Lima")).get(java.util.Calendar.HOUR_OF_DAY)
        listOf(
            Triple("Hoy", "3:00 - 6:00 pm", 18),
            Triple("Mañana", "9:00 - 12:00 m", 12),
            Triple("Mañana", "2:00 - 5:00 pm", 17),
        ).filter { (day, _, ends) -> day != "Hoy" || hour < ends - 1 }
    }

    var phone by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().rumaScreen().statusBarsPadding().imePadding()) {
        DetailHeader("Programar Recojo", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = RumaSpace.gutter, end = RumaSpace.gutter,
                top = RumaSpace.xs, bottom = RumaSpace.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            item {
                HeroCard(
                    accent = RumaColors.Gen,
                    overline = "ÍTEMS A RECOGER",
                    metric = quantities.sum().toString(),
                    support = "Programar Recojo",
                )
            }
            item { PointsRuleCard(quantities.sum()) }
            item { SectionLabel("ÍTEMS A RECOGER") }
            if (f.items.isEmpty()) {
                item {
                    RumaEmpty(Icons.Filled.Inventory2, "No hay ítems para recoger", accent = RumaColors.Gen)
                }
            }
            itemsIndexed(f.items) { i, item ->
                PickupItemRow(
                    pickupItemIcon(item.code), item.label, quantities[i],
                    onPlus = { quantities[i] = quantities[i] + 1 },
                    onMinus = { if (quantities[i] > 0) quantities[i] = quantities[i] - 1 },
                )
            }

            if (sackCode.isNotBlank()) {
                item {
                    SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.xs) {
                        Text("Escribe $sackCode en tu saco", color = RumaColors.Text, style = RumaType.rowTitle)
                        Text(
                            "El Eco-Rider recoge tu saco y el acopio lo pesa y verifica por ese código. Ahí se acreditan tus puntos.",
                            color = RumaColors.Text2, style = RumaType.bodySmall,
                        )
                    }
                }
            }
            item { SectionLabel("PUNTO DE RECOJO") }
            item {
                PickupPlaceCard(
                    here = here,
                    gpsLine = gpsLine,
                    locating = locating,
                    addressLine = f.addressLine,
                    onUseMyLocation = onUseMyLocation,
                    onChangeAddress = onChangeAddress,
                )
            }
            if (f.phone.isNullOrBlank()) {
                item { SectionLabel("TU CELULAR") }
                item {
                    RumaInputField(
                        "CELULAR", Icons.Filled.Phone, phone, { phone = it.filter { c -> c.isDigit() || c in "+ -" }.take(16) },
                        placeholder = "987 654 321", keyboardType = KeyboardType.Phone, enabled = !busy,
                        helper = "El Eco-Rider te llamará si no encuentra tu casa. Lo guardamos en tu perfil.",
                    )
                }
            }
            item { SectionLabel("VENTANA HORARIA") }
            itemsIndexed(slots) { i, (day, time, _) ->
                SlotRow(day, time, selected = slot == i) { slot = i }
            }

            item {
                Row(
                    Modifier.clip(RoundedCornerShape(RumaRadius.chip)).background(RumaColors.MoneyTint).fillMaxWidth().padding(horizontal = RumaSpace.lg, vertical = RumaSpace.md),
                    horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Info, null, tint = RumaColors.Money, modifier = Modifier.size(RumaSpace.lg))
                    Text("El acopio pesa y verifica tu saco: ahí se acreditan tus puntos y puedes cobrarlos.",
                        color = RumaColors.Money, style = RumaType.label)
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
                        onConfirm(selections, "$day $time", phone.takeIf { f.phone.isNullOrBlank() })
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
    here: RoutePlanner.LatLng?,
    gpsLine: String?,
    locating: Boolean,
    addressLine: String,
    onUseMyLocation: () -> Unit,
    onChangeAddress: () -> Unit,
) {
    Column(Modifier.rumaCard().fillMaxWidth()) {
    if (here != null) PinMap(here)
    Row(
        Modifier.fillMaxWidth().padding(RumaSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            if (gpsLine != null) Icons.Filled.MyLocation else Icons.Filled.Place,
            RumaColors.Gen, RumaColors.GenTint, boxSize = RumaSpace.xl * 2, iconSize = RumaSpace.xl, radius = RumaRadius.badge,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RumaSpace.xs)) {
            Text(gpsLine ?: addressLine, color = RumaColors.Text, style = RumaType.rowTitle)
            when {
                gpsLine != null -> Text(
                    "El Eco-Rider vendrá a este punto del mapa · referencia: $addressLine",
                    color = RumaColors.Success, style = RumaType.label,
                )
                locating -> RumaLoading("Obteniendo tu ubicación…", accent = RumaColors.Gen)
                else -> Text(
                    "Activa la ubicación para que el Eco-Rider llegue a tu puerta. Mientras tanto usaremos tu dirección.",
                    color = RumaColors.Text2, style = RumaType.label,
                )
            }
            Column {
                if (gpsLine == null && !locating) {
                    Text("Reintentar GPS", color = RumaColors.Gen, style = RumaType.label,
                        modifier = Modifier.clickable(onClickLabel = "Reintentar GPS", role = Role.Button, onClick = onUseMyLocation)
                            .heightIn(min = RumaSpace.xl * 2 + RumaSpace.sm).padding(vertical = RumaSpace.lg))
                }
                Text("Cambiar referencia", color = RumaColors.Gen, style = RumaType.bodySmall,
                    modifier = Modifier.clickable(onClickLabel = "Cambiar referencia", role = Role.Button, onClick = onChangeAddress)
                        .heightIn(min = RumaSpace.xl * 2 + RumaSpace.sm).padding(vertical = RumaSpace.lg))
            }
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
        Modifier.fillMaxWidth().rumaCard().padding(RumaSpace.lg),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(RumaSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, RumaColors.Gen, RumaColors.GenTint)
            Text("$qty $name", color = RumaColors.Text, style = RumaType.rowTitle, modifier = Modifier.weight(1f))
        }
        Row(
            Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(RumaSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(Icons.Filled.Remove, filled = false, label = "Quitar una unidad de $name", onClick = onMinus)
            Text("$qty", color = RumaColors.Text, style = RumaType.metricMedium,
                textAlign = TextAlign.Center, modifier = Modifier.width(RumaSpace.xxl))
            StepButton(Icons.Filled.Add, filled = true, label = "Añadir una unidad de $name", onClick = onPlus)
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, filled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(RumaSpace.xl * 2 + RumaSpace.sm).clip(CircleShape)
            .background(if (filled) RumaColors.Gen else RumaColors.Bg)
            .then(if (!filled) Modifier.rumaOutlined(RumaColors.Border, RumaRadius.pill) else Modifier)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (filled) RumaColors.OnDark else RumaColors.Text, modifier = Modifier.size(RumaSpace.lg)) }
}

@Composable
private fun SlotRow(day: String, time: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .rumaOutlined(if (selected) RumaColors.Gen else RumaColors.Border, RumaRadius.badge)
            .background(if (selected) RumaColors.GenTint else RumaColors.Surface)
            .semantics { contentDescription = "Elegir recojo: $day $time" }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = RumaSpace.xl * 2 + RumaSpace.sm)
            .padding(RumaSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            null, tint = if (selected) RumaColors.Gen else RumaColors.Text2,
            modifier = Modifier.size(RumaSpace.xl + RumaSpace.xs),
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(day, color = if (selected) RumaColors.Gen else RumaColors.Text, style = RumaType.rowTitle)
            Text(time, color = RumaColors.Text2, style = RumaType.body)
        }
        if (selected) Icon(Icons.Filled.CheckCircle, null, tint = RumaColors.Gen, modifier = Modifier.size(RumaSpace.xl))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun PickupPreview() {
    RumaTheme { GeneratorPickupScreen() }
}
