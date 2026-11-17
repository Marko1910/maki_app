package pe.ruma.app.ui.screens.generator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.SecondaryButton
import pe.ruma.app.ui.components.rumaOutlined
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.DetailHeader
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.HeroRule
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.RumaSkeleton
import pe.ruma.app.ui.components.ProgressTrack
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.SmallButton
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.components.StepDots
import pe.ruma.app.ui.components.rumaCard
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

private val SampleWallet = WalletUi(
    balancePoints = 4250,
    balanceCash = "= S/4.25",
    progress = 0.85f,
    nextRedeemHint = "Faltan 750 pts para tu próximo canje de S/5.00",
    options = listOf(
        RedeemOptionUi(kind = "cash", title = "Yape / Plin", rule = "1,000 pts = S/1.00 · Mínimo S/5.00", suggested = true),
        RedeemOptionUi(
            kind = "municipal_discount", code = "MUNI_ARBITRIOS", partner = "Municipalidad",
            title = "Descuento en Arbitrios", rule = "5 % de tu arbitrio anual · 2,750 pts\nHasta 50 % al año · este año llevas 0 %", suggested = false,
        ),
        RedeemOptionUi(kind = "store_discount", title = "Descuento Tiendas Eco", rule = "500 pts = 5% de descuento", suggested = false, cost = 5000, affordable = false),
        RedeemOptionUi(kind = "donation", title = "Donar a Reforestación", rule = "200 pts = 1 árbol plantado", suggested = false),
    ),
    history = listOf(
        RedeemHistoryUi("Yape S/5.00", "10 Jun", "−5,000 pts"),
        RedeemHistoryUi("Yape S/3.00", "25 May", "−3,000 pts"),
    ),
)

/**
 * Mi Wallet, laid out like the PWA's: one hero with the balance, what is still to be
 * weighed, then the options and the past redemptions each as one grouped list. Every
 * option ends in its own small "Canjear" (or what is missing), so comparing them is
 * reading one column instead of scrolling past four cards.
 */
@Composable
fun GeneratorWalletScreen(
    state: WalletUi? = null,
    busy: Boolean = false,
    /** Collected, not yet weighed: shown with the step it is waiting on. */
    pendingPoints: Int = 0,
    error: String? = null,
    onRetry: () -> Unit = {},
    onScan: () -> Unit = {},
    onBack: () -> Unit = {},
    onRedeem: (RedeemOptionUi) -> Unit = {},
    /** Title of the option just redeemed: shows the "¿y ahora?" card on top. */
    justRedeemed: String? = null,
    onHome: () -> Unit = {},
) {
    // Sample only for @Preview: on a device a made-up balance is worse than a placeholder.
    val s = state ?: SampleWallet.takeIf { LocalInspectionMode.current }
    var pending by remember { mutableStateOf<RedeemOptionUi?>(null) }

    Column(
        Modifier.fillMaxSize().rumaScreen().statusBarsPadding(),
    ) {
        DetailHeader("Mi Wallet", onBack)
        if (s == null) {
            if (error != null) RumaError(error, onRetry)
            else RumaSkeleton(listOf(168.dp, 20.dp, 72.dp, 72.dp, 72.dp), Modifier.padding(horizontal = RumaSpace.gutter), "Cargando tu wallet…")
            return@Column
        }
        // The redemption's "¿y ahora?" lands on top: bring it into view from wherever the button was.
        val list = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(justRedeemed) { if (justRedeemed != null) list.animateScrollToItem(0) }
        LazyColumn(
            Modifier.weight(1f),
            state = list,
            contentPadding = PaddingValues(
                start = RumaSpace.gutter, end = RumaSpace.gutter,
                top = RumaSpace.xs, bottom = RumaSpace.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
        ) {
            if (justRedeemed != null) item(key = "redeemed") { RedeemDoneCard(justRedeemed, onScan, onHome) }
            item { BalanceHero(s, pendingPoints) }
            if (pendingPoints > 0) item {
                SoftCard(Modifier.fillMaxWidth(), gap = RumaSpace.md) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("POR CONFIRMAR", Modifier.weight(1f))
                        StatusChip("+${groupInt(pendingPoints)} pts", RumaColors.Money)
                    }
                    StepDots(listOf("Recogido" to "en tu puerta", "Pesado" to "en el acopio", "Disponible" to "en tu saldo"), current = 1)
                    Text(
                        "Pasan a tu saldo cuando el acopio pese el material; no tienes que hacer nada. El monto final puede ajustarse según el peso.",
                        color = RumaColors.Text2, style = RumaType.bodySmall,
                    )
                }
            }
            if (s.balancePoints == 0 && pendingPoints == 0) item {
                Column(Modifier.rumaCard()) {
                    RumaRow(
                        "Empieza a sumar puntos", subtitle = "Registra tus residuos y pide un recojo",
                        leading = { GlassBadge(icon = Icons.Outlined.PhotoCamera) }, onClick = onScan,
                    )
                }
            }
            item { SectionLabel("OPCIONES DE CANJE") }
            item {
                if (s.options.isEmpty()) {
                    RumaEmpty(Icons.Filled.AccountBalanceWallet, "Aún no hay opciones de canje", accent = RumaColors.Gen)
                } else {
                    RumaList(s.options) { opt -> RedeemOptionRow(opt, s.balancePoints, busy) { pending = opt } }
                }
            }
            item { SectionLabel("HISTORIAL DE CANJES") }
            item {
                if (s.history.isEmpty()) {
                    Text(
                        "Aún no tienes canjes. Cuando hagas uno, aquí verás su fecha y su monto.",
                        color = RumaColors.Text2, style = RumaType.body,
                    )
                } else {
                    RumaList(s.history) { row ->
                        RumaRow(
                            row.title, subtitle = row.date,
                            trailing = { Text(row.amount, color = RumaColors.Money, style = RumaType.rowTitle) },
                        )
                    }
                }
            }
        }
    }

    pending?.let { opt ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            containerColor = RumaColors.Surface,
            titleContentColor = RumaColors.Text,
            textContentColor = RumaColors.Text2,
            title = { Text("Confirmar canje", style = RumaType.sectionHeading) },
            text = {
                Text(
                    "Vas a canjear ${"%,d".format(java.util.Locale.US, opt.cost)} Eco-Puntos por “${opt.title}”." +
                        if (opt.monetaryValue > 0) " Recibirás S/%.2f.".format(java.util.Locale.US, opt.monetaryValue) else "",
                    style = RumaType.body,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = RumaSpace.xl * 2 + RumaSpace.sm)
                        .semantics { contentDescription = "Confirmar canje" },
                    onClick = { onRedeem(opt); pending = null },
                ) {
                    Text(if (busy) "Procesando…" else "Canjear", color = if (busy) RumaColors.Text3 else RumaColors.Gen, style = RumaType.button)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = RumaSpace.xl * 2 + RumaSpace.sm)
                        .semantics { contentDescription = "Cancelar canje" },
                    onClick = { pending = null },
                ) {
                    Text("Cancelar", color = if (busy) RumaColors.Text3 else RumaColors.Text2, style = RumaType.button)
                }
            },
        )
    }
}

/**
 * The icon by kind first, with the code as a fallback: a discount on your arbitrios
 * is not the same offer as a shop coupon, and matching the kind lets a second
 * district arrive as a new code without losing the municipal look.
 */
private fun redeemIcon(code: String, kind: String): ImageVector = when {
    kind == "municipal_discount" || code == "MUNI_ARBITRIOS" -> Icons.Filled.AccountBalance
    kind == "cash" -> Icons.Filled.AccountBalanceWallet
    kind == "store_discount" -> Icons.Filled.Storefront
    kind == "donation" -> Icons.Filled.Forest
    else -> Icons.Outlined.Inventory2
}

@Composable
private fun RedeemOptionRow(opt: RedeemOptionUi, balance: Int, busy: Boolean, onRedeem: () -> Unit) {
    RumaRow(
        opt.title,
        subtitle = opt.rule,
        leading = { GlassBadge(icon = redeemIcon(opt.code, opt.kind)) },
        extra = {
            if (opt.partner.isNotBlank()) StatusChip(opt.partner, RumaColors.Text2)
            if (opt.suggested && opt.affordable) StatusChip("Sugerido", RumaColors.Money)
        },
        trailing = {
            when {
                opt.affordable -> SmallButton(
                    if (busy) "Procesando…" else "Canjear",
                    filled = opt.suggested, enabled = !busy, onClick = onRedeem,
                )
                else -> Column(Modifier.fillMaxWidth(0.3f), horizontalAlignment = Alignment.End) {
                    val (lead, figure) = opt.blocked?.let { "Ya usaste" to it.removePrefix("Ya usaste ") }
                        ?: ("Te faltan" to "${groupInt(opt.cost - balance)} pts")
                    Text(lead, color = RumaColors.Text2, style = RumaType.bodySmall, textAlign = TextAlign.End)
                    Text(figure, color = RumaColors.Text, style = RumaType.chip, textAlign = TextAlign.End)
                }
            }
        },
    )
}

/**
 * The close of a redemption: it worked, where to see it, and the next step. The order
 * of the buttons is the loop: the next scan is what fills the next redemption.
 */
@Composable
private fun RedeemDoneCard(title: String, onScan: () -> Unit, onHome: () -> Unit) {
    val scale = remember { Animatable(0.94f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    Column(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .rumaOutlined(RumaColors.Gen).padding(RumaSpace.lg)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(RumaSpace.md),
    ) {
        Text("¡Canje hecho!", style = RumaType.cardTitle, color = RumaColors.Text)
        Text(
            "“$title” quedó registrado; lo ves abajo, en tu historial de canjes. ¿Y ahora? Cada recojo suma para el próximo.",
            style = RumaType.body, color = RumaColors.Text2,
        )
        PrimaryButton("Registrar residuos", Modifier.fillMaxWidth(), icon = Icons.Outlined.PhotoCamera, onClick = onScan)
        SecondaryButton("Volver al inicio", Modifier.fillMaxWidth(), onClick = onHome)
    }
}

/** Points first, their value in soles on the same line, and what the next option asks. */
@Composable
private fun BalanceHero(s: WalletUi, pendingPoints: Int) {
    Column(
        Modifier.fillMaxWidth()
            .rumaHero(RumaGradient.forAccent(RumaColors.Gen), glow = RumaGradient.glowOf(RumaColors.Gen))
            .padding(RumaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        Text("SALDO DISPONIBLE", color = RumaColors.OnGenSoft, style = RumaType.overline)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("%,d".format(java.util.Locale.US, s.balancePoints), color = RumaColors.OnDark, style = RumaType.metricHero, modifier = Modifier.weight(1f))
            Text(s.balanceCash, color = RumaColors.OnDark, style = RumaType.sectionHeading)
        }
        Text(
            "Eco-Puntos" + if (pendingPoints > 0) " · +${groupInt(pendingPoints)} por confirmar" else "",
            color = RumaColors.OnGenSoft, style = RumaType.body,
        )
        if (s.nextRedeemHint.isNotBlank()) {
            HeroRule()
            ProgressTrack(s.progress)
            Text(s.nextRedeemHint, color = RumaColors.OnGenSoft, style = RumaType.bodySmall)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun WalletPreview() {
    RumaTheme { GeneratorWalletScreen(pendingPoints = 320) }
}
