package pe.ruma.app.ui.screens.rider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.HeroRule
import pe.ruma.app.ui.components.RumaBottomAction
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

/**
 * One material at the door. [materialId] blank = nothing to weigh (a pickup with
 * no declared items): the row is informational only.
 */
data class ConfirmMaterialUi(
    val name: String,
    val detail: String,
    val points: String,
    val materialId: String = "",
    val declared: Int = 0,
    /** Catalogue code ("PET"), shown in the row's badge as on the PWA. */
    val code: String = "",
)

data class RiderConfirmUiState(
    /** The family's sack code: the rider checks the label before taking the sack. */
    val sackCode: String = "RU-7K3Q9A",
    val stopName: String = "Fam. Nureña",
    val address: String = "Jr. Las Flores 123",
    val streak: String = "Hoy 3:00 - 6:00 pm",
    val materials: List<ConfirmMaterialUi> = listOf(
        ConfirmMaterialUi("Plástico PET", "La familia declaró 40", "+400 pts", "pet", 40, "PET"),
        ConfirmMaterialUi("Latas de aluminio", "La familia declaró 12", "+120 pts", "alu", 12, "ALU"),
    ),
    val totalWeight: String = "—",
    val totalPoints: String = "98 pts",
    val earnings: String = "S/2.77",
)

/**
 * The rider only receives the labelled sack (2026-11-18, docs/debate-quien-mide.md): no scale and no
 * counting at the door. The acopio weighs every sack by its code and counts by hand only the ones whose
 * weight doesn't back what the family declared. Laid out like the PWA's: the sack code, what the family
 * declared, what the rider earns as the hero, and "Recibí el saco" pinned above the system bar.
 */
@Composable
fun RiderConfirmScreen(
    state: RiderConfirmUiState = RiderConfirmUiState(),
    busy: Boolean = false,
    onBack: () -> Unit = {},
    onConfirm: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().rumaScreen(RumaColors.Rider).statusBarsPadding()) {
        DetailHeader("Confirmar Recolección", onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            item {
                SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.xs) {
                    if (state.sackCode.isNotBlank()) {
                        Text("SACO ${state.sackCode}", color = RumaColors.Rider, style = RumaType.cardTitle)
                        Text("Revisa que el saco tenga este código antes de llevarlo.", color = RumaColors.Text2, style = RumaType.bodySmall)
                    }
                    Text(state.stopName, color = RumaColors.Text, style = RumaType.rowTitle)
                    Text(state.address, color = RumaColors.Text2, style = RumaType.bodySmall)
                    Text(state.streak, color = RumaColors.Text2, style = RumaType.bodySmall)
                }
            }
            item { SectionLabel("Lo que declaró la familia") }
            item {
                if (state.materials.isEmpty()) {
                    Text("No hay materiales declarados", color = RumaColors.Text2, style = RumaType.body)
                } else {
                    RumaList(state.materials) { MaterialRow(it) }
                }
            }
            item {
                Text(
                    "No necesitas pesar ni contar: lleva el saco al acopio. Allí lo pesan y verifican, y de ahí salen los puntos del vecino.",
                    color = RumaColors.Text2, style = RumaType.bodySmall,
                )
            }
            item { EarningsHero(state) }
        }
        RumaBottomAction {
            PrimaryButton(
                if (busy) "Confirmando…" else state.sackCode.takeIf { it.isNotBlank() }?.let { "Recibí el saco $it" } ?: "Recibí el saco",
                Modifier.fillMaxWidth(), icon = Icons.Filled.Check,
                color = RumaColors.Rider, enabled = !busy, onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun MaterialRow(material: ConfirmMaterialUi) {
    RumaRow(
        material.name,
        subtitle = material.detail,
        leading = { GlassBadge(text = material.code.take(3).ifBlank { material.name.take(3).uppercase() }) },
        trailing = if (material.materialId.isBlank()) {
            { Text(material.points, color = RumaColors.Money, style = RumaType.rowTitle) }
        } else null,
    )
}

/** What this stop pays the rider: the one figure the screen is about (PWA "Ganarás"). */
@Composable
private fun EarningsHero(state: RiderConfirmUiState) {
    Column(
        Modifier.fillMaxWidth().rumaHero(RumaGradient.rider, glow = RumaGradient.glowOf(RumaColors.Rider))
            .padding(RumaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        Text("GANANCIA ESTIMADA", color = RumaColors.OnRiderSoft, style = RumaType.overline)
        // "≈ S/0.35 · se paga al pesar": the amount is the hero, the condition reads under it.
        val (amount, note) = state.earnings.split(" · ", limit = 2).let { it[0] to it.getOrNull(1) }
        Text(amount, color = RumaColors.OnDark, style = RumaType.metricHero)
        note?.let { Text(it.replaceFirstChar(Char::uppercase), color = RumaColors.OnRiderSoft, style = RumaType.body) }
        if (state.totalWeight != "—") {
            HeroRule()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Peso total", color = RumaColors.OnRiderSoft, style = RumaType.body, modifier = Modifier.weight(1f))
                Text(state.totalWeight, color = RumaColors.OnDark, style = RumaType.rowTitle)
            }
        }
        Text(
            "S/ 0,35 por kg pesado en el acopio + S/ 6 por ruta de 12 recojos. Los puntos del vecino los verifica el acopio.",
            color = RumaColors.OnRiderSoft, style = RumaType.bodySmall,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun RiderConfirmPreview() {
    RumaTheme { RiderConfirmScreen(state = SampleConfirm) }
}

private val SampleConfirm = RiderConfirmUiState()
