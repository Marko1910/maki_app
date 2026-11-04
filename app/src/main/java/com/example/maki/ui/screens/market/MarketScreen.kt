package com.example.maki.ui.screens.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.HeroCard
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.MakiError
import com.example.maki.ui.components.MakiLoading
import com.example.maki.ui.components.MetricTile
import com.example.maki.ui.components.NavRow
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.SoftCard
import com.example.maki.ui.components.StatusChip
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

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
    accent: Color = MakiColors.Gen,
    error: String? = null,
    onRetry: () -> Unit = {},
    onOpenNews: (String) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().makiScreen().statusBarsPadding()) {
        Header()
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = MakiSpace.gutter, end = MakiSpace.gutter, top = MakiSpace.xs, bottom = MakiSpace.xl),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
        ) {
            when {
                error != null -> item { MakiError(error, onRetry) }
                state == null -> item { MakiLoading("Consultando el mercado…", accent = accent) }
                state.quotes.isEmpty() && state.news.isEmpty() -> item {
                    MakiEmpty(
                        icon = Icons.Filled.Storefront,
                        title = "El mercado está en calma",
                        hint = "Cuando haya precios y novedades de tu zona los verás aquí.",
                        accent = accent,
                    )
                }
                else -> {
                    state.highlight?.let { item { Highlight(it, accent) } }
                    if (state.quotes.isNotEmpty()) {
                        item { SectionLabel("PRECIOS Y DEMANDA DE HOY") }
                        items(state.quotes, key = { it.code }) { QuoteCard(it, accent) }
                    }
                    if (state.news.isNotEmpty()) {
                        item { SectionLabel("QUÉ ESTÁ PASANDO") }
                        items(state.news, key = { it.id }) { NewsCard(it, onOpenNews) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(
        Modifier.fillMaxWidth().padding(start = MakiSpace.gutter, end = MakiSpace.gutter, top = MakiSpace.sm, bottom = MakiSpace.sm),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.xs),
    ) {
        Text("Mercado Vivo", color = MakiColors.Text, style = MakiType.screenTitle)
        Text(
            "Cuánto paga hoy cada material y qué están pidiendo los acopios",
            color = MakiColors.Text2, style = MakiType.bodySmall,
        )
    }
}

/** The one line that answers "¿qué junto esta semana?". */
@Composable
private fun Highlight(q: MarketQuoteUi, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(MakiSpace.stack)) {
        HeroCard(
            accent = accent,
            overline = "LO MÁS BUSCADO HOY",
            metric = q.pricePerKg,
            support = q.name,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MakiSpace.stack)) {
            MetricTile(Icons.Filled.EmojiEvents, q.pointsPerKg, "Eco-Puntos", accent, Modifier.weight(1f))
            MetricTile(Icons.Filled.AccountBalanceWallet, q.yours, "Para ti", accent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuoteCard(q: MarketQuoteUi, accent: Color) {
    SoftCard(gap = MakiSpace.md) {
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(q.name, color = MakiColors.Text, style = MakiType.cardTitle, modifier = Modifier.weight(1f))
            Text(q.pricePerKg, color = MakiColors.Text, style = MakiType.metricMedium)
        }
        Text(q.yours, color = MakiColors.Text2, style = MakiType.bodySmall)
        Text(q.pointsPerKg, color = accent, style = MakiType.label)
        q.changePct?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (q.rising) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                    null, tint = if (q.rising) MakiColors.Success else MakiColors.Error,
                    modifier = Modifier.size(MakiSpace.lg),
                )
                Text(
                    (if (q.rising) "Sube " else "Baja ") + it,
                    color = if (q.rising) MakiColors.Success else MakiColors.Error, style = MakiType.label,
                )
            }
        }
        DemandChip(q.demand)
        if (q.centers > 0) {
            Text(
                if (q.centers == 1) "1 acopio lo compra" else q.centers.toString() + " acopios lo compran",
                color = MakiColors.Text2, style = MakiType.label,
            )
        }
    }
}

@Composable
private fun DemandChip(demand: String) {
    val (label, color) = when (demand) {
        "alta" -> "Demanda alta" to MakiColors.Success
        "media" -> "Demanda media" to MakiColors.Money
        "baja" -> "Demanda baja" to MakiColors.Text2
        else -> "Sin compradores" to MakiColors.Error
    }
    StatusChip(label, color)
}

@Composable
private fun NewsCard(n: MarketNewsUi, onOpen: (String) -> Unit) {
    val (icon, tint) = newsVisual(n.kind)
    val source = listOf(n.source, n.date).filter { it.isNotBlank() }.joinToString(" · ")
    if (n.url != null) {
        NavRow(
            icon = icon, title = n.title,
            subtitle = listOf(n.summary, source).filter { it.isNotBlank() }.joinToString("\n"),
            accent = tint, onClick = { onOpen(n.url) },
        )
        return
    }
    SoftCard(gap = MakiSpace.sm) {
        Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(MakiSpace.xl))
            Text(n.title, color = MakiColors.Text, style = MakiType.cardTitle, modifier = Modifier.weight(1f))
        }
        if (n.summary.isNotBlank()) {
            Text(n.summary, color = MakiColors.Text2, style = MakiType.bodySmall)
        }
        Text(
            source,
            color = MakiColors.Text2, style = MakiType.caption,
        )
    }
}

private fun newsVisual(kind: String): Pair<ImageVector, Color> = when (kind) {
    "price" -> Icons.Filled.Payments to MakiColors.Money
    "demand" -> Icons.Filled.Storefront to MakiColors.Rider
    "regulation" -> Icons.Filled.Gavel to MakiColors.Text2
    else -> Icons.Filled.Lightbulb to MakiColors.Success
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
    MAKITheme { MarketScreen(state = SampleMarket) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1000)
@Composable
private fun RiderMarketPreview() {
    MAKITheme { MarketScreen(state = SampleMarket, accent = MakiColors.Rider) }
}
