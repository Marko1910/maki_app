package com.example.maki.ui.screens.market

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.maki.data.MakiRepository
import com.example.maki.data.MarketNewsDto
import com.example.maki.data.MaterialDemandDto
import com.example.maki.data.MaterialDto
import com.example.maki.data.MaterialPriceDto
import com.example.maki.ui.screens.generator.MakiTime
import com.example.maki.ui.screens.generator.POINTS_PER_SOL
import com.example.maki.ui.screens.generator.groupInt
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Mercado Vivo, shared by both roles: the same board answers "¿qué junto?" for a
 * family and "¿qué vale el viaje?" for a rider, so it lives in one ViewModel
 * instead of being copied into each flow's.
 */
class MarketViewModel : ViewModel() {

    var state by mutableStateOf<MarketUiState?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun load(force: Boolean = false) {
        if (state != null && !force) return
        viewModelScope.launch {
            try {
                val materials = MakiRepository.loadMaterials()
                val prices = MakiRepository.loadPrices()
                val demand = runCatching { MakiRepository.loadMaterialDemand() }.getOrDefault(emptyList())
                val news = runCatching { MakiRepository.loadMarketNews() }.getOrDefault(emptyList())
                state = build(materials, prices, demand, news)
                error = null
            } catch (e: Exception) {
                error = e.message ?: "No pudimos leer el mercado."
            }
        }
    }
}

private fun build(
    materials: List<MaterialDto>,
    prices: List<MaterialPriceDto>,
    demand: List<MaterialDemandDto>,
    news: List<MarketNewsDto>,
): MarketUiState {
    val quotes = materials.mapNotNull { m ->
        val pts = m.points_per_kg.takeIf { it > 0 } ?: return@mapNotNull null
        val quote = prices.firstOrNull { it.code == m.code }
        val d = demand.firstOrNull { it.code == m.code }
        MarketQuoteUi(
            code = m.code,
            name = m.name,
            pricePerKg = quote?.price_per_kg?.takeIf { it > 0 }
                ?.let { "S/%.2f/kg".format(Locale.US, it) } ?: "Sin precio",
            pointsPerKg = "${groupInt(pts.toInt())} pts/kg",
            yours = "≈ S/%.2f".format(Locale.US, pts / POINTS_PER_SOL),
            changePct = quote?.pct_change?.takeIf { abs(it) >= 0.1 }
                ?.let { "${if (it > 0) "+" else ""}${"%.0f".format(Locale.US, it)}%" },
            rising = (quote?.pct_change ?: 0.0) >= 0,
            demand = d?.demanda ?: "media",
            centers = d?.centros ?: 0,
        )
    }
    // Worth the trip = pays the most among the ones the acopios still want.
    val highlight = quotes.filter { it.demand == "alta" }.maxByOrNull { it.pointsValue() }
        ?: quotes.maxByOrNull { it.pointsValue() }

    return MarketUiState(
        quotes = quotes,
        news = news.map {
            MarketNewsUi(
                id = it.id,
                kind = it.kind,
                title = it.title,
                summary = it.summary.orEmpty(),
                source = it.source.orEmpty(),
                url = it.url?.takeIf { u -> u.startsWith("http") },
                // A publication date is a date, not an instant: keeping the time
                // part shifted every UTC-midnight entry a day back in Lima.
                date = MakiTime.dayMonth(it.published_at.take(10)),
            )
        },
        highlight = highlight,
    )
}

/** Points per kilo as a number again, to rank the board. */
private fun MarketQuoteUi.pointsValue(): Int =
    pointsPerKg.filter { it.isDigit() }.toIntOrNull() ?: 0
