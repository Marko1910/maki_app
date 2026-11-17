package pe.ruma.app.ui.screens.generator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaLoading
import pe.ruma.app.ui.components.NavRow
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.components.rumaCard
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

/**
 * "Mis recojos": every pickup the family asked for, the newest as the big card on top and the rest as a list.
 * Each one opens its own screen: the live view while in flight, the detail once closed.
 */
@Composable
fun GeneratorPickupsScreen(
    pickups: List<OtherPickupUi>?,
    error: String? = null,
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
    onOpen: (String) -> Unit = {},
    onScan: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().rumaScreen().statusBarsPadding()) {
        DetailHeader("Mis recojos", onBack)
        when {
            pickups == null && error != null -> RumaError(error, onRetry)
            pickups == null -> pe.ruma.app.ui.components.RumaSkeleton(listOf(132.dp, 20.dp, 72.dp, 72.dp, 72.dp), Modifier.padding(horizontal = RumaSpace.gutter), "Cargando tus recojos…")
            pickups.isEmpty() -> RumaEmpty(
                icon = Icons.Filled.LocalShipping, title = "Aún no tienes recojos",
                hint = "Toma una foto de tus residuos y pide tu primer recojo.", accent = RumaColors.Gen,
                actionLabel = "Registrar residuos", onAction = onScan,
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xl),
                verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
            ) {
                // The big card is the pickup still in flight (newest first); with none in flight, the newest one.
                val latest = pickups.firstOrNull { it.live } ?: pickups.first()
                val rest = pickups.filter { it !== latest }
                item { LatestCard(latest) { onOpen(latest.id) } }
                if (rest.isNotEmpty()) {
                    item { SectionLabel("ANTERIORES") }
                    item {
                        Column(Modifier.fillMaxWidth().rumaCard()) {
                            rest.forEach { o ->
                                NavRow(Icons.Filled.LocalShipping, o.title, "${o.sub} · ${o.status}", accent = if (o.live) RumaColors.Money else RumaColors.Gen, framed = false, onClick = { onOpen(o.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The most recent pickup, as the one hero of the screen. */
@Composable
private fun LatestCard(o: OtherPickupUi, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .rumaHero(RumaGradient.forAccent(RumaColors.Gen), glow = RumaGradient.glowOf(RumaColors.Gen))
            .clickable(onClickLabel = "Ver tu último recojo", role = Role.Button, onClick = onClick)
            .padding(RumaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        Text(if (o.live) "TU RECOJO EN CURSO" else "TU ÚLTIMO RECOJO", style = RumaType.overline, color = RumaGradient.softOn(RumaColors.Gen))
        Text(o.title, style = RumaType.metricMedium, color = RumaColors.OnDark)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
            StatusChip(o.status, RumaColors.OnDark)
            Text(o.sub, style = RumaType.bodySmall, color = RumaGradient.softOn(RumaColors.Gen))
        }
        Text(if (o.live) "Toca para ver cómo va" else "Toca para ver el detalle", style = RumaType.label, color = RumaColors.OnDark)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PickupsPreview() {
    RumaTheme {
        GeneratorPickupsScreen(
            listOf(
                OtherPickupUi("1", "Recojo · 3 PET", "Mañana 9:00 - 12:00 m", "Buscando Eco-Rider", true),
                OtherPickupUi("2", "Recojo · 2 PET", "11 Nov", "Completado", false),
            ),
        )
    }
}
