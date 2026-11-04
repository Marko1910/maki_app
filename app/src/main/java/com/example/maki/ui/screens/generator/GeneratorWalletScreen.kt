package com.example.maki.ui.screens.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.HeroCard
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.MakiEmpty
import com.example.maki.ui.components.ProgressTrack
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.StatusChip
import com.example.maki.ui.components.makiCard
import com.example.maki.ui.components.makiOutlined
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType

data class RedeemRow(val title: String, val date: String, val amount: String)

private val SampleWallet = WalletUi(
    balancePoints = 4250,
    balanceCash = "= S/4.25",
    progress = 0.85f,
    nextRedeemHint = "Faltan 750 pts para tu próximo canje de S/5.00",
    options = listOf(
        RedeemOptionUi(kind = "cash", title = "Yape / Plin", rule = "1,000 pts = S/1.00 · Mínimo S/5.00", suggested = true),
        RedeemOptionUi(
            kind = "municipal_discount", code = "MUNI_ARBITRIOS", partner = "Municipalidad",
            title = "Descuento en Arbitrios", rule = "1,000 pts = S/1.00 en tus arbitrios", suggested = false,
        ),
        RedeemOptionUi(kind = "store_discount", title = "Descuento Tiendas Eco", rule = "500 pts = 5% de descuento", suggested = false),
        RedeemOptionUi(kind = "donation", title = "Donar a Reforestación", rule = "200 pts = 1 árbol plantado", suggested = false),
    ),
    history = listOf(
        RedeemHistoryUi("Yape S/5.00", "10 Jun", "−5,000 pts"),
        RedeemHistoryUi("Yape S/3.00", "25 May", "−3,000 pts"),
    ),
)

@Composable
fun GeneratorWalletScreen(
    state: WalletUi? = null,
    busy: Boolean = false,
    onBack: () -> Unit = {},
    onRedeem: (RedeemOptionUi) -> Unit = {},
) {
    val s = state ?: SampleWallet
    var pending by remember { mutableStateOf<RedeemOptionUi?>(null) }

    Column(
        Modifier.fillMaxSize().makiScreen().statusBarsPadding(),
    ) {
        DetailHeader("Mi Wallet", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = MakiSpace.gutter, end = MakiSpace.gutter,
                top = MakiSpace.xs, bottom = MakiSpace.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
        ) {
            item { BalanceHero(s) }
            item { SectionLabel("OPCIONES DE CANJE") }
            if (s.options.isEmpty()) {
                item {
                    MakiEmpty(
                        Icons.Filled.AccountBalanceWallet, "Aún no hay opciones de canje",
                        accent = MakiColors.Gen,
                    )
                }
            }
            items(s.options) { opt ->
                val v = redeemVisual(opt.code, opt.kind)
                RedeemOption(
                    v.icon, v.tint, v.bg, opt.title, opt.rule, partner = opt.partner, suggested = opt.suggested,
                    actionLabel = if (opt.affordable) v.action else "Saldo insuficiente",
                    actionFilled = opt.affordable && v.filled,
                    enabled = opt.affordable,
                    onAction = { if (opt.affordable) pending = opt else onRedeem(opt) },
                )
            }
            item { SectionLabel("HISTORIAL DE CANJES") }
            if (s.history.isEmpty()) {
                item {
                    MakiEmpty(
                        Icons.Filled.AccountBalanceWallet, "Aún no tienes canjes",
                        accent = MakiColors.Gen,
                    )
                }
            } else {
                items(s.history) { row ->
                    HistoryCard(RedeemRow(row.title, row.date, row.amount))
                }
            }
        }
    }

    pending?.let { opt ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            containerColor = MakiColors.Surface,
            titleContentColor = MakiColors.Text,
            textContentColor = MakiColors.Text2,
            title = { Text("Confirmar canje", style = MakiType.sectionHeading) },
            text = {
                Text(
                    "Vas a canjear ${"%,d".format(java.util.Locale.US, opt.cost)} Eco-Puntos por “${opt.title}”." +
                        if (opt.monetaryValue > 0) " Recibirás S/%.2f.".format(java.util.Locale.US, opt.monetaryValue) else "",
                    style = MakiType.body,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = MakiSpace.xl * 2 + MakiSpace.sm)
                        .semantics { contentDescription = "Confirmar canje" },
                    onClick = { onRedeem(opt); pending = null },
                ) {
                    Text(if (busy) "Procesando…" else "Canjear", color = if (busy) MakiColors.Text3 else MakiColors.Gen, style = MakiType.button)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = MakiSpace.xl * 2 + MakiSpace.sm)
                        .semantics { contentDescription = "Cancelar canje" },
                    onClick = { pending = null },
                ) {
                    Text("Cancelar", color = if (busy) MakiColors.Text3 else MakiColors.Text2, style = MakiType.button)
                }
            },
        )
    }
}

private data class RedeemVisual(
    val icon: ImageVector, val tint: Color, val bg: Color, val action: String, val filled: Boolean,
)

/**
 * Styling by kind first, with the code as a fallback: MAKI works with the
 * municipality, and a discount on your arbitrios is not the same offer as a
 * shop coupon — the catalogue gives it its own kind. Matching the kind, and
 * not just this one code, is what lets a second district arrive as a new
 * code without losing the municipal treatment.
 */
private fun redeemVisual(code: String, kind: String): RedeemVisual = when {
    kind == "municipal_discount" || code == "MUNI_ARBITRIOS" ->
        RedeemVisual(Icons.Filled.AccountBalance, MakiColors.Rider, MakiColors.RiderTint, "Canjear descuento", true)
    kind == "cash" -> RedeemVisual(Icons.Filled.Smartphone, MakiColors.Money, MakiColors.MoneyTint, "Canjear ahora", true)
    kind == "store_discount" -> RedeemVisual(Icons.Filled.Storefront, MakiColors.Gen, MakiColors.GenTint, "Ver tiendas", false)
    kind == "donation" -> RedeemVisual(Icons.Filled.Forest, MakiColors.Success, MakiColors.SuccessTint, "Donar", false)
    else -> RedeemVisual(Icons.Filled.Smartphone, MakiColors.Gen, MakiColors.GenTint, "Canjear", false)
}

@Composable
private fun BalanceHero(s: WalletUi) {
    HeroCard(
        accent = MakiColors.Gen,
        overline = "SALDO DISPONIBLE",
        metric = "%,d".format(java.util.Locale.US, s.balancePoints),
        support = "Eco-Puntos",
    ) {
        Text(s.balanceCash, color = MakiColors.OnDark, style = MakiType.metricMedium)
        ProgressTrack(s.progress)
        Text(s.nextRedeemHint, color = MakiColors.OnGenSoft, style = MakiType.label)
    }
}

@Composable
private fun RedeemOption(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    rule: String,
    actionLabel: String,
    partner: String = "",
    suggested: Boolean = false,
    actionFilled: Boolean = false,
    enabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier
            .makiCard()
            .then(if (suggested) Modifier.makiOutlined(MakiColors.Gen) else Modifier)
            .fillMaxWidth()
            .padding(MakiSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.md), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, iconTint, iconBg, boxSize = MakiSpace.xl * 2 + MakiSpace.sm, iconSize = MakiSpace.xl + MakiSpace.xs, radius = MakiRadius.badge)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs)) {
                Text(title, color = MakiColors.Text, style = MakiType.rowTitle)
                Text(rule, color = MakiColors.Text2, style = MakiType.bodySmall)
                if (partner.isNotBlank()) StatusChip(partner, iconTint)
            }
            if (suggested) StatusChip("SUGERIDO", MakiColors.Gen)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(MakiRadius.chip))
                .background(
                    when {
                        !enabled -> MakiColors.Border
                        actionFilled -> iconTint
                        else -> iconBg
                    }
                )
                .clickable(enabled = enabled, onClickLabel = actionLabel, role = Role.Button, onClick = onAction)
                .heightIn(min = MakiSpace.xl * 2 + MakiSpace.sm)
                .padding(vertical = MakiSpace.md),
            horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val c = when {
                !enabled -> MakiColors.Text2
                actionFilled -> MakiColors.OnDark
                else -> iconTint
            }
            Text(actionLabel, color = c, style = MakiType.button)
            if (enabled) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c, modifier = Modifier.size(MakiSpace.lg))
        }
    }
}

@Composable
private fun HistoryCard(row: RedeemRow) {
    Row(
        Modifier.fillMaxWidth().makiCard().padding(MakiSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Filled.Smartphone, MakiColors.Money, MakiColors.MoneyTint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs)) {
            Text(row.title, color = MakiColors.Text, style = MakiType.rowTitle)
            Text(row.date, color = MakiColors.Text2, style = MakiType.label)
        }
        Text(row.amount, color = MakiColors.Text2, style = MakiType.rowTitle)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WalletPreview() {
    MAKITheme { GeneratorWalletScreen() }
}
