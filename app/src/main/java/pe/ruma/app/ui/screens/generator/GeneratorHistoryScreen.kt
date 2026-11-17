package pe.ruma.app.ui.screens.generator

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.FilterPill
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.GlassChip
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.RumaSkeleton
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

private val SampleHistory = HistoryUi(
    listOf(
        HistorySectionUi("HOY", listOf(
            HistoryEntryUi(false, "PET", "Recojo · 8 PET + 2 latas", "Hoy 3:00 - 6:00 pm · Carlos", "En camino", "assigned", null, null, pickupId = "p1"),
            HistoryEntryUi(false, "PET", "8 PET + 2 latas detectados", "+92 pts · S/0.34", "Recojo pedido", null, null, null),
        )),
        HistorySectionUi("AYER", listOf(
            HistoryEntryUi(false, "CAR", "Recojo · 3 cartones", "Carlos", "Recojo completado", "done", "+45 pts por confirmar en el acopio", null, pickupId = "p2"),
        )),
        HistorySectionUi("CANJES", listOf(
            HistoryEntryUi(true, "redeem", "Canje Yape S/5.00", "10 Jun", null, null, null, "−5,000 pts"),
        )),
    ),
)

/** 0 = Todo, 1 = Recojos, 2 = Detecciones, 3 = Canjes. */
private val Filters = listOf("Todo", "Recojos", "Detecciones", "Canjes")

/**
 * Everything the family did, newest first — scans, pickups (tap one to follow it or
 * see its detail) and redemptions — one grouped list per day, as in the PWA.
 */
@Composable
fun GeneratorHistoryScreen(
    state: HistoryUi? = null,
    onBack: () -> Unit = {},
    onOpenPickup: (String) -> Unit = {},
    onScan: () -> Unit = {},
    onRequestPickup: () -> Unit = {},
    onWallet: () -> Unit = {},
) {
    val s = state ?: SampleHistory.takeIf { LocalInspectionMode.current }
    var sel by rememberSaveable { mutableIntStateOf(0) }
    val sections = s?.sections.orEmpty()
        .map { it.label to it.entries.filter { e -> e.matchesFilter(sel) } }
        .filter { it.second.isNotEmpty() }

    Column(Modifier.fillMaxSize().rumaScreen().statusBarsPadding()) {
        DetailHeader("Mi Historial", onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                    Filters.forEachIndexed { i, f -> FilterPill(f, i == sel) { sel = i } }
                }
            }
            when {
                s == null -> item { RumaSkeleton(listOf(20.dp, 72.dp, 72.dp, 72.dp), label = "Cargando tu historial…") }
                // An empty list offers the one step that fills it.
                sections.isEmpty() -> item {
                    when (sel) {
                        1 -> RumaEmpty(
                            Icons.Filled.LocalShipping, "Aún no tienes recojos",
                            "Pide uno y aquí verás su estado y sus puntos, paso a paso.",
                            accent = RumaColors.Gen, actionLabel = "Pedir recojo", onAction = onRequestPickup,
                        )
                        3 -> RumaEmpty(
                            Icons.Filled.AccountBalanceWallet, "Aún no tienes canjes",
                            "Cuando tengas saldo disponible podrás canjearlo desde tu wallet.",
                            accent = RumaColors.Gen, actionLabel = "Ver mi wallet", onAction = onWallet,
                        )
                        else -> RumaEmpty(
                            Icons.Filled.History, "Nada por aquí todavía",
                            "Toma una foto de tus residuos: es el primer paso para ganar puntos.",
                            accent = RumaColors.Gen, actionLabel = "Registrar residuos", onAction = onScan,
                        )
                    }
                }
                else -> sections.forEach { (label, entries) ->
                    item(key = "day-$label") {
                        Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                            SectionLabel(label)
                            // Every pickup opens: in flight the live view, closed its detail.
                            RumaList(entries) { e -> HistoryRow(e, e.pickupId?.let { id -> { onOpenPickup(id) } }) }
                        }
                    }
                }
            }
        }
    }
}

private fun HistoryEntryUi.matchesFilter(sel: Int): Boolean = when (sel) {
    1 -> pickupId != null
    2 -> !isRedeem && pickupId == null
    3 -> isRedeem
    else -> true
}

@Composable
private fun HistoryRow(e: HistoryEntryUi, onClick: (() -> Unit)?) {
    val icon = when {
        e.isRedeem -> Icons.Filled.AccountBalanceWallet
        e.pickupId != null -> Icons.Filled.LocalShipping
        else -> Icons.Outlined.PhotoCamera
    }
    // A pickup reads "who and when", a scan its state, a redemption its date; the
    // figure that moves points always sits on the right in the money colour.
    val subtitle = if (e.pickupId != null || e.isRedeem) e.meta else e.statusText
    val money = when {
        e.isRedeem -> e.amount
        e.pickupId != null -> e.note
        else -> e.meta
    }
    RumaRow(
        e.title,
        subtitle = subtitle,
        leading = { GlassBadge(icon = icon) },
        trailing = {
            Column(Modifier.fillMaxWidth(0.42f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(RumaSpace.xs)) {
                if (e.pickupId != null && e.statusText != null) {
                    if (e.statusKind == "assigned") StatusChip(e.statusText, RumaColors.Gen) else GlassChip(e.statusText)
                }
                if (money != null) Text(money, color = RumaColors.Money, style = RumaType.chip, textAlign = TextAlign.End)
            }
        },
        onClick = onClick,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HistoryPreview() {
    RumaTheme { GeneratorHistoryScreen() }
}
