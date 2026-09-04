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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maki.ui.components.IconBadge
import com.example.maki.ui.components.DetailHeader
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.makiShadow
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiFont

data class RedeemRow(val title: String, val date: String, val amount: String)

private val SampleWallet = WalletUi(
    balancePoints = 4250,
    balanceCash = "= S/4.25",
    progress = 0.85f,
    nextRedeemHint = "Faltan 750 pts para tu próximo canje de S/5.00",
    options = listOf(
        RedeemOptionUi("cash", "Yape / Plin", "1,000 pts = S/1.00 · Mínimo S/5.00", suggested = true),
        RedeemOptionUi("store_discount", "Descuento Tiendas Eco", "500 pts = 5% de descuento", suggested = false),
        RedeemOptionUi("donation", "Donar a Reforestación", "200 pts = 1 árbol plantado", suggested = false),
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
        Modifier.fillMaxSize().background(MakiColors.Bg).statusBarsPadding(),
    ) {
        DetailHeader("Mi Wallet", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BalanceHero(s)
            SectionLabel("OPCIONES DE CANJE")
            s.options.forEach { opt ->
                val v = redeemVisual(opt.kind)
                RedeemOption(
                    v.icon, v.tint, v.bg, opt.title, opt.rule, suggested = opt.suggested,
                    actionLabel = if (opt.affordable) v.action else "Saldo insuficiente",
                    actionFilled = opt.affordable && v.filled,
                    enabled = opt.affordable,
                    onAction = { if (opt.affordable) pending = opt else onRedeem(opt) },
                )
            }
            if (s.history.isNotEmpty()) {
                SectionLabel("HISTORIAL DE CANJES")
                HistoryCard(s.history.map { RedeemRow(it.title, it.date, it.amount) })
            }
        }
    }

    pending?.let { opt ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text("Confirmar canje", fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "Vas a canjear ${"%,d".format(java.util.Locale.US, opt.cost)} Eco-Puntos por “${opt.title}”." +
                        if (opt.monetaryValue > 0) " Recibirás S/%.2f.".format(java.util.Locale.US, opt.monetaryValue) else "",
                    fontFamily = MakiFont,
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { onRedeem(opt); pending = null }) {
                    Text(if (busy) "Procesando…" else "Canjear", color = MakiColors.Gen, fontFamily = MakiFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pending = null }) {
                    Text("Cancelar", color = MakiColors.Text2, fontFamily = MakiFont)
                }
            },
        )
    }
}

private data class RedeemVisual(
    val icon: ImageVector, val tint: Color, val bg: Color, val action: String, val filled: Boolean,
)

private fun redeemVisual(kind: String): RedeemVisual = when (kind) {
    "cash" -> RedeemVisual(Icons.Filled.Smartphone, MakiColors.Money, MakiColors.MoneyTint, "Canjear ahora", true)
    "store_discount" -> RedeemVisual(Icons.Filled.Storefront, MakiColors.Gen, MakiColors.GenTint, "Ver tiendas", false)
    "donation" -> RedeemVisual(Icons.Filled.Forest, MakiColors.Success, MakiColors.SuccessTint, "Donar", false)
    else -> RedeemVisual(Icons.Filled.Smartphone, MakiColors.Gen, MakiColors.GenTint, "Canjear", false)
}

@Composable
private fun BalanceHero(s: WalletUi) {
    Column(
        Modifier
            .makiShadow(22.dp, 10.dp, Color(0x400F6E56))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(MakiColors.Gen, MakiColors.GenDark)))
            .fillMaxWidth()
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Saldo disponible", color = MakiColors.OnDarkSoft, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("%,d".format(java.util.Locale.US, s.balancePoints), color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
                Text("Eco-Puntos", color = MakiColors.OnDarkSoft, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(MakiColors.WhiteTint).padding(start = 11.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.AccountBalanceWallet, null, tint = Color(0xFFFFE7B0), modifier = Modifier.size(16.dp))
                Text(s.balanceCash, color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x2EFFFFFF))) {
                Box(Modifier.fillMaxWidth(s.progress).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White))
            }
            Text(s.nextRedeemHint, color = MakiColors.OnDarkSoft, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
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
    suggested: Boolean = false,
    actionFilled: Boolean = false,
    enabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier
            .makiShadow(16.dp, 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MakiColors.Surface)
            .then(if (suggested) Modifier.border(1.5.dp, MakiColors.Gen, RoundedCornerShape(16.dp)) else Modifier)
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, iconTint, iconBg, boxSize = 46.dp, iconSize = 24.dp, radius = 14.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text(rule, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
            if (suggested) {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(MakiColors.GenTint).padding(horizontal = 9.dp, vertical = 4.dp)) {
                    Text("SUGERIDO", color = MakiColors.Gen, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        !enabled -> MakiColors.Border
                        actionFilled -> MakiColors.Gen
                        else -> MakiColors.GenTint
                    }
                )
                .clickable(onClick = onAction).padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val c = when {
                !enabled -> MakiColors.Text2
                actionFilled -> Color.White
                else -> MakiColors.Gen
            }
            Text(actionLabel, color = c, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (enabled) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun HistoryCard(rows: List<RedeemRow>) {
    Column(
        Modifier.makiShadow(16.dp, 5.dp).clip(RoundedCornerShape(16.dp)).background(MakiColors.Surface)
            .fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        rows.forEachIndexed { i, r ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Filled.Smartphone, MakiColors.Money, Color(0x0FBA7517), boxSize = 36.dp, iconSize = 18.dp, radius = 18.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(r.title, color = MakiColors.Text, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(r.date, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
                Text(r.amount, color = MakiColors.Text2, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(MakiColors.Border))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WalletPreview() {
    MAKITheme { GeneratorWalletScreen() }
}
