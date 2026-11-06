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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.HeroCard
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MetricTile
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.SoftCard
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

/**
 * One material at the door. [materialId] blank = nothing to count (a pickup with
 * no declared items): the row is informational only.
 */
data class ConfirmMaterialUi(
    val name: String,
    val detail: String,
    val points: String,
    val materialId: String = "",
    val declared: Int = 0,
    val confirmed: Int = 0,
)

data class RiderConfirmUiState(
    val stopName: String = "Fam. Nureña",
    val address: String = "Jr. Las Flores 123",
    val streak: String = "Hoy 3:00 - 6:00 pm",
    val materials: List<ConfirmMaterialUi> = listOf(
        ConfirmMaterialUi("Plástico PET", "La familia declaró 8", "+80 pts", "pet", 8, 8),
        ConfirmMaterialUi("Latas de aluminio", "La familia declaró 3", "+18 pts", "alu", 3, 2),
    ),
    val totalWeight: String = "—",
    val totalPoints: String = "98 pts",
    val earnings: String = "S/2.77",
)

/**
 * The rider counts what is really in the bag. The family's number is the
 * starting point, not the answer; the counted one is what gets recorded.
 */
@Composable
fun RiderConfirmScreen(
    state: RiderConfirmUiState = RiderConfirmUiState(),
    busy: Boolean = false,
    onBack: () -> Unit = {},
    onAdjust: (materialId: String, delta: Int) -> Unit = { _, _ -> },
    onConfirm: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().makiScreen(MakiColors.Rider).statusBarsPadding()) {
        DetailHeader("Confirmar Recolección", onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = MakiSpace.gutter, vertical = MakiSpace.stack),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
        ) {
            item {
                HeroCard(
                    accent = MakiColors.Rider,
                    overline = "CUENTA LO QUE RECIBES",
                    metric = if (state.materials.any { it.materialId.isNotBlank() })
                        "${state.materials.sumOf { it.confirmed }} unidades" else "—",
                    support = state.stopName,
                ) {
                    Text(state.address, color = MakiColors.OnRiderSoft, style = MakiType.bodySmall)
                    Text(state.streak, color = MakiColors.OnRiderSoft, style = MakiType.label)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.stack)) {
                    MetricTile(Icons.Filled.Star, state.totalPoints,
                        "Puntos al generador (se confirman en acopio)", MakiColors.Rider, Modifier.weight(1f))
                    MetricTile(Icons.Filled.AccountBalanceWallet, state.earnings,
                        "Ganarás", MakiColors.Rider, Modifier.weight(1f))
                }
            }
            if (state.totalWeight != "—") {
                item {
                    MetricTile(Icons.Filled.Inventory2, state.totalWeight, "Peso total",
                        MakiColors.Rider, Modifier.fillMaxWidth())
                }
            }
            item { SectionLabel("CUENTA LO QUE RECIBES") }
            item {
                Text(
                    "Lo que cuentes aquí es lo que se registra: los puntos del vecino y tu pago salen de este número.",
                    color = MakiColors.Text2, style = MakiType.bodySmall,
                )
            }
            if (state.materials.isEmpty()) {
                item {
                    MakiEmpty(Icons.Filled.Inventory2, "No hay materiales declarados",
                        accent = MakiColors.Rider)
                }
            }
            itemsIndexed(state.materials, key = { index, material -> "$index-${material.materialId}" }) { _, material ->
                MaterialRow(material, onAdjust)
            }
            item {
                PrimaryButton(
                    if (busy) "Confirmando…" else "Confirmar recolección",
                    Modifier.fillMaxWidth(), color = MakiColors.Rider, enabled = !busy, onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun MaterialRow(material: ConfirmMaterialUi, onAdjust: (String, Int) -> Unit) {
    SoftCard(gap = MakiSpace.md) {
        Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.md), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Inventory2, MakiColors.Rider, MakiColors.RiderTint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs)) {
                Text(material.name, color = MakiColors.Text, style = MakiType.rowTitle)
                Text(material.detail, color = MakiColors.Text2, style = MakiType.bodySmall)
                val diff = material.confirmed - material.declared
                if (material.materialId.isNotBlank() && diff != 0) {
                    Text(if (diff < 0) "Faltan ${-diff}" else "Sobran $diff",
                        color = MakiColors.Streak, style = MakiType.label)
                }
            }
        }
        if (material.materialId.isBlank()) {
            Text(material.points, color = MakiColors.Gen, style = MakiType.metricMedium)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MakiSpace.md, Alignment.End),
                verticalAlignment = Alignment.CenterVertically) {
                StepButton(Icons.Filled.Remove, "Quitar uno de ${material.name}", filled = false) {
                    onAdjust(material.materialId, -1)
                }
                Text("${material.confirmed}", color = MakiColors.Text, style = MakiType.metricMedium,
                    textAlign = TextAlign.Center, modifier = Modifier.width(MakiSpace.lg * 3))
                StepButton(Icons.Filled.Add, "Agregar uno de ${material.name}", filled = true) {
                    onAdjust(material.materialId, +1)
                }
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(MakiSpace.lg * 3).clip(CircleShape)
            .background(if (filled) MakiColors.Rider else MakiColors.Bg)
            .then(if (!filled) Modifier.border(MakiSpace.xs / 4, MakiColors.Border, CircleShape) else Modifier)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (filled) MakiColors.OnDark else MakiColors.Text, modifier = Modifier.size(MakiSpace.xl)) }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun RiderConfirmPreview() {
    MAKITheme { RiderConfirmScreen(state = SampleConfirm) }
}

private val SampleConfirm = RiderConfirmUiState()
