package com.example.maki.ui.screens.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

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
    onBack: () -> Unit = {},
    onConfirm: (List<Pair<String, Int>>, String) -> Unit = { _, _ -> },
    onChangeAddress: () -> Unit = {},
) {
    val f = form ?: SamplePickupForm
    val quantities = remember(f) { f.items.map { it.initialQty }.toMutableStateList() }
    var slot by remember { mutableIntStateOf(0) }
    val slots = listOf(
        Triple("Hoy", "3:00 - 6:00 pm", true),
        Triple("Mañana", "9:00 - 12:00 m", false),
        Triple("Mañana", "2:00 - 5:00 pm", false),
    )

    Column(Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding()) {
        DetailHeader("Programar Recojo", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SectionLabel("ITEMS A RECOGER")
            Column(
                Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface)
                    .fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                f.items.forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MakiColors.Border))
                    PickupItemRow(
                        pickupItemIcon(item.code), item.label, quantities[i],
                        onPlus = { quantities[i] = quantities[i] + 1 },
                        onMinus = { if (quantities[i] > 0) quantities[i] = quantities[i] - 1 },
                    )
                }
            }

            SectionLabel("DIRECCIÓN")
            Row(
                Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface)
                    .fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Filled.Place, MakiColors.Gen, MakiColors.GenTint, boxSize = 42.dp, iconSize = 21.dp, radius = 14.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(f.addressLine, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Cambiar dirección", color = MakiColors.Gen, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = onChangeAddress))
                }
            }

            SectionLabel("VENTANA HORARIA")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                slots.forEachIndexed { i, (day, time, _) ->
                    SlotRow(day, time, selected = slot == i) { slot = i }
                }
            }

            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x0FBA7517)).fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Info, null, tint = MakiColors.Money, modifier = Modifier.size(17.dp))
                Text("Los puntos se acreditarán cuando el Eco-Rider confirme la recolección.",
                    color = Color(0xFF7A4E0F), fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp)
            }

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

private fun pickupItemIcon(code: String): ImageVector = when (code) {
    "PET", "VID" -> Icons.Filled.LocalDrink
    else -> Icons.Filled.Inventory2
}

@Composable
private fun PickupItemRow(icon: ImageVector, name: String, qty: Int, onPlus: () -> Unit, onMinus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(MakiColors.Gen), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Icon(icon, null, tint = MakiColors.Gen, modifier = Modifier.size(20.dp))
        Text("$qty $name", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton(Icons.Filled.Remove, filled = false, onClick = onMinus)
            Text("$qty", color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                textAlign = TextAlign.Center, modifier = Modifier.width(20.dp))
            StepButton(Icons.Filled.Add, filled = true, onClick = onPlus)
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape)
            .background(if (filled) MakiColors.Gen else MakiColors.Bg)
            .then(if (!filled) Modifier.border(1.dp, MakiColors.Border, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (filled) Color.White else MakiColors.Text, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun SlotRow(day: String, time: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) MakiColors.GenTint else MakiColors.Surface)
            .border(if (selected) 1.8.dp else 1.dp, if (selected) MakiColors.Gen else MakiColors.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) MakiColors.Gen else Color(0xFFC7CFCB), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(MakiColors.Gen)) }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(day, color = if (selected) MakiColors.Gen else MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(time, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
        if (selected) Icon(Icons.Filled.CheckCircle, null, tint = MakiColors.Gen, modifier = Modifier.size(20.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun PickupPreview() {
    MAKITheme { GeneratorPickupScreen() }
}
