package pe.ruma.app.ui.screens.market

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.GlassChip
import pe.ruma.app.ui.components.HeroChip
import pe.ruma.app.ui.components.HeroRule
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaLoading
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.ScreenHeader
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.TextAction
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

/** One material on the board: what it pays, how it moved, how badly it is wanted. */
data class MarketQuoteUi(
    val code: String,
    val name: String,
    val pricePerKg: String,
    val pointsPerKg: String,
    val yours: String,
    val changePct: String? = null,
    val rising: Boolean = true,
    /** "alta" | "media" | "baja" | "sin_demanda", straight from `v_material_demand`. */
    val demand: String = "media",
    val centers: Int = 0,
)

data class MarketNewsUi(
    val id: String,
    val kind: String,
    val title: String,
    val summary: String,
    val source: String,
    val url: String?,
    val date: String,
)

data class MarketUiState(
    val quotes: List<MarketQuoteUi> = emptyList(),
    val news: List<MarketNewsUi> = emptyList(),
    /** The material to bring in today — best paid with demand to absorb it. */
    val highlight: MarketQuoteUi? = null,
)

/**
 * Mercado Vivo: the price board plus what is happening around it.
 *
 * Recycling pays differently every week and nobody tells the neighbourhood —
 * people separate out of goodwill and find out the price at the door. This is
 * the same screen for both roles: a family decides what to keep, a rider decides
 * what is worth the trip.
 */
@Composable
fun MarketScreen(
    state: MarketUiState? = null,
    accent: Color = RumaColors.Gen,
    error: String? = null,
    onRetry: () -> Unit = {},
    onOpenNews: (String) -> Unit = {},
    /** The family's "¿qué junto esta semana?" under the hero; null hides it (the rider). */
    onAsk: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().rumaScreen(accent).statusBarsPadding()) {
        ScreenHeader("Mercado Vivo", subtitle = "Cuánto paga hoy cada material y qué están pidiendo los acopios")
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.xs, bottom = RumaSpace.xl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            when {
                error != null -> item { RumaError(error, onRetry, accent = accent) }
                state == null -> item { RumaLoading("Consultando el mercado…", accent = accent) }
                state.quotes.isEmpty() && state.news.isEmpty() -> item {
                    RumaEmpty(
                        icon = Icons.Filled.Storefront,
                        title = "El mercado está en calma",
                        hint = "Cuando haya precios y novedades de tu zona los verás aquí.",
                        accent = accent,
                    )
                }
                else -> {
                    state.highlight?.let { item { Highlight(it, accent, onAsk) } }
                    if (state.quotes.isNotEmpty()) {
                        item { SectionLabel("PRECIOS Y DEMANDA DE HOY") }
                        item { RumaList(state.quotes) { QuoteRow(it) } }
                    }
                    if (state.news.isNotEmpty()) {
                        item { SectionLabel("QUÉ ESTÁ PASANDO") }
                        items(state.news, key = { it.id }) { NewsCard(it, accent, onOpenNews) }
                    }
                }
            }
        }
    }
}

/** The one material to bring in today, as the screen's hero (PWA "Lo más buscado hoy"). */
@Composable
private fun Highlight(q: MarketQuoteUi, accent: Color, onAsk: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().rumaHero(RumaGradient.forAccent(accent), glow = RumaGradient.glowOf(accent))
            .padding(RumaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LO MÁS BUSCADO HOY", color = RumaGradient.softOn(accent), style = RumaType.overline, modifier = Modifier.weight(1f))
            q.changePct?.let { HeroChip((if (q.rising) "Sube " else "Baja ") + it) }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(q.name, color = RumaColors.OnDark, style = RumaType.screenTitle, modifier = Modifier.weight(1f))
            Text(q.pricePerKg, color = RumaColors.OnDark, style = RumaType.screenTitle)
        }
        Text("${q.pointsPerKg} · ${q.yours}", color = RumaGradient.softOn(accent), style = RumaType.bodySmall)
        if (onAsk != null) {
            HeroRule()
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = "Preguntar a Rumi", role = Role.Button, onClick = onAsk)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Para ti: ¿qué junto esta semana?", color = RumaGradient.softOn(accent), style = RumaType.body, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = RumaColors.OnDark, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A material on the board: its code, how wanted it is, and what a kilo pays. */
@Composable
private fun QuoteRow(q: MarketQuoteUi) {
    val (label, dot) = demandOf(q.demand)
    RumaRow(
        q.name,
        subtitle = q.yours,
        leading = { GlassBadge(text = q.code.take(3)) },
        extra = {
            GlassChip(label, dot = dot)
            if (q.centers > 0) Text(
                if (q.centers == 1) "1 acopio lo compra" else "${q.centers} acopios lo compran",
                color = RumaColors.Text2, style = RumaType.caption,
            )
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(q.pricePerKg, color = RumaColors.Money, style = RumaType.rowTitle)
                q.changePct?.let {
                    Text(
                        (if (q.rising) "Sube " else "Baja ") + it,
                        color = if (q.rising) RumaColors.Success else RumaColors.Error, style = RumaType.caption,
                    )
                }
            }
        },
    )
}

private fun demandOf(demand: String): Pair<String, Color> = when (demand) {
    "alta" -> "Demanda alta" to RumaColors.Success
    "media" -> "Demanda media" to RumaColors.MoneyLight
    "baja" -> "Demanda baja" to RumaColors.Silver
    else -> "Sin compradores" to RumaColors.Error
}

@Composable
private fun NewsCard(n: MarketNewsUi, accent: Color, onOpen: (String) -> Unit) {
    val source = listOfNotNull(n.source.takeIf { it.isNotBlank() }?.let { "Fuente: $it" }, n.date.takeIf { it.isNotBlank() })
        .joinToString(" · ")
    SoftCard(gap = RumaSpace.sm) {
        Text(n.title, color = RumaColors.Text, style = RumaType.rowTitle)
        if (n.summary.isNotBlank()) Text(n.summary, color = RumaColors.Text2, style = RumaType.body)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(source, color = RumaColors.Text2, style = RumaType.caption, modifier = Modifier.weight(1f))
            if (n.url != null) TextAction("Abrir", accent, "Abrir la noticia") { onOpen(n.url) } else Box(Modifier.heightIn(min = 48.dp))
        }
    }
}

private val SampleMarket = MarketUiState(
    quotes = listOf(
        MarketQuoteUi("ALU", "Aluminio", "S/4.00/kg", "1,600 pts/kg", "≈ S/1.60 para ti", "+29%", true, "alta", 4),
        MarketQuoteUi("PET", "PET", "S/1.00/kg", "400 pts/kg", "≈ S/0.40 para ti", "-17%", false, "media", 4),
    ),
    news = listOf(
        MarketNewsUi("1", "price", "El aluminio es el material mejor pagado del mes",
            "La cotización internacional está en S/11 588 por tonelada.",
            "IndexMundi", null, "1 Mar"),
    ),
    highlight = MarketQuoteUi("ALU", "Aluminio", "S/4.00/kg", "1,600 pts/kg", "≈ S/1.60 para ti", "+29%", true, "alta", 4),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 1000)
@Composable
private fun MarketPreview() {
    RumaTheme { MarketScreen(state = SampleMarket) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1000)
@Composable
private fun RiderMarketPreview() {
    RumaTheme { MarketScreen(state = SampleMarket, accent = RumaColors.Rider) }
}
