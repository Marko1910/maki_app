package pe.ruma.app.ui.screens.generator

import kotlin.math.roundToInt
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateContentSize
import pe.ruma.app.ui.components.RumiAvatar
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import pe.ruma.app.R
import pe.ruma.app.data.WeekProgress
import pe.ruma.app.data.rumaLevel
import pe.ruma.app.navigation.GenRoutes
import pe.ruma.app.ui.components.rumaCard
import pe.ruma.app.ui.components.RumaError
import pe.ruma.app.ui.components.RumaLoading
import pe.ruma.app.ui.components.RumaSkeleton
import pe.ruma.app.ui.components.rumaOutlined
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.components.NavRow
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.ProgressTrack
import pe.ruma.app.ui.components.SecondaryButton
import pe.ruma.app.ui.components.SoftCard
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaType

/** Neutral until loaded; preview data is provided separately below. */
data class HomeUiState(
    val displayName: String = "",
    val ecoPoints: Int = 0,
    val riderEtaText: String = "",
    val hasUnreadNotifications: Boolean = false,
    val riderIncoming: Boolean = false,
    val riderTitle: String = "",
    /** Collected but not yet weighed at the acopio — shown so earned points never "vanish". */
    val pendingPoints: Int = 0,
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GeneratorHomeScreen(
    state: HomeUiState = HomeUiState(),
    week: WeekProgress? = null,
    rumaDays: Int? = null,
    goal: Int = 3,
    loading: Boolean = false,
    error: String? = null,
    weekError: String? = null,
    onRetry: () -> Unit = {},
    onGoalChange: (Int) -> Unit = {},
    /** Points that reached the balance since the last visit; 0 hides the banner. */
    creditedGain: Int = 0,
    onCreditedSeen: () -> Unit = {},
    /** The home's one next step; null keeps the plain "Registrar residuos". */
    nextStep: NextStep? = null,
    /** A pickup just asked for: a short, non-blocking "va en camino"; null hides it. */
    dispatch: DispatchUi? = null,
    onDispatchSeen: () -> Unit = {},
    /** "Invita y gana" offered at a happy moment (points credited, weekly goal met); at most weekly. */
    invitePrompt: Boolean = false,
    onInviteDismiss: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    LazyColumn(
        Modifier.fillMaxSize().rumaScreen().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = RumaSpace.gutter, vertical = RumaSpace.lg),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
    ) {
        item { HomeHeader(state) { onNavigate(GenRoutes.NOTIFICATIONS) } }
        when {
            loading -> item { RumaSkeleton(listOf(56.dp, 240.dp, 108.dp, 136.dp, 52.dp), label = "Cargando tu inicio…") }
            error != null -> item { RumaError(error, onRetry) }
            else -> {
                if (dispatch != null) item(key = "dispatch") {
                    CelebrationBanner(
                        title = when {
                            dispatch.searching -> "¡Recojo pedido!"
                            dispatch.later != null -> "¡${dispatch.riderName} tomó tu recojo!"
                            else -> "¡${dispatch.riderName} va en camino!"
                        },
                        body = if (dispatch.searching) "Buscamos al Eco-Rider más cercano. Te avisamos cuando lo tome."
                        else (dispatch.later?.let { "Pasará ${it.lowercase()}. " }
                            ?: dispatch.etaMinutes?.let { "Llega en ~$it min. " } ?: "") + "Ten listos tus materiales.",
                        clickLabel = "Ver mi recojo", onTimeout = onDispatchSeen,
                    ) { onDispatchSeen(); onNavigate(GenRoutes.TRACKING) }
                }
                if (creditedGain > 0) item(key = "credited-$creditedGain") {
                    CelebrationBanner(
                        title = "¡+${groupInt(creditedGain)} pts ya están en tu saldo!",
                        body = "Son S/%.2f más para canjear. Toca para ver tu wallet.".format(java.util.Locale.US, creditedGain / POINTS_PER_SOL),
                        clickLabel = "Ir a Mi Wallet", onTimeout = onCreditedSeen,
                    ) { onCreditedSeen(); onNavigate(GenRoutes.WALLET) }
                }
                if (invitePrompt) item(key = "invite") {
                    InvitePromptCard(onOpen = { onNavigate(GenRoutes.INVITE) }, onDismiss = onInviteDismiss)
                }
                item(key = "next") { NextStepCard(nextStep ?: nextStep(null), onNavigate) }
                item {
                    when {
                        weekError != null -> RumaError(weekError, onRetry)
                        week == null -> RumaLoading("Cargando tu semana…")
                        else -> GoalRing(week)
                    }
                }
                item {
                    SoftCard(Modifier.fillMaxWidth().clickable(onClickLabel = "Ir a Mi Wallet", role = Role.Button, onClick = { onNavigate(GenRoutes.WALLET) }), gap = RumaSpace.md) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                            Icon(Icons.Filled.AccountBalanceWallet, null, tint = RumaColors.Gen)
                            Text("Mi Wallet", Modifier.weight(1f), style = RumaType.rowTitle, color = RumaColors.Text)
                            Icon(Icons.Filled.ChevronRight, null, tint = RumaColors.Text2)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Text("${groupInt(state.ecoPoints)} puntos", Modifier.weight(1f).padding(end = RumaSpace.sm), style = RumaType.cardTitle, color = RumaColors.Text)
                            Text("S/ %.2f disponibles".format(java.util.Locale.US, state.ecoPoints / POINTS_PER_SOL), style = RumaType.cardTitle, color = RumaColors.Gen)
                        }
                        if (state.pendingPoints > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                            Box(Modifier.size(RumaSpace.sm).clip(CircleShape).background(RumaColors.Money))
                            Text("+${groupInt(state.pendingPoints)} pts por confirmar · llegan cuando el acopio pese tu material", style = RumaType.bodySmall, color = RumaColors.Text2)
                        }
                    }
                }
                if (rumaDays != null) item { RumaCard(rumaDays) }
                item {
                    Column(Modifier.rumaCard()) {
                        NavRow(Icons.AutoMirrored.Filled.DirectionsBike, "Pedir recojo", "Toma una foto de tus residuos y un Eco-Rider pasa por ellos", framed = false, onClick = { onNavigate(GenRoutes.CAMERA) })
                        NavRow(Icons.Filled.Place, "Puntos de acopio", "Mira dónde reciclar y los precios", framed = false, onClick = { onNavigate(GenRoutes.CENTERS) })
                        NavRow(Icons.Outlined.Lightbulb, "Pregúntale a Rumi", "Qué va en cada tacho y cuánto vale tu material", framed = false, leading = { RumiAvatar() }, onClick = { onNavigate(GenRoutes.CHAT) })
                    }
                }
            }
        }
    }
}

/**
 * "Siguiente mejor acción": one card that says what is worth doing now and one button that
 * does it. It replaces the fixed "Registrar residuos" and the separate live-pickup card.
 */
@Composable
private fun NextStepCard(step: NextStep, onNavigate: (String) -> Unit) {
    SoftCard(Modifier.fillMaxWidth().animateContentSize(), gap = RumaSpace.md) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
            Text("TU SIGUIENTE PASO", Modifier.weight(1f), style = RumaType.overline, color = RumaColors.Gen)
            if (step.kind == NextStep.Kind.LIVE) StatusChip("EN VIVO", RumaColors.Money)
        }
        Text(step.title, style = RumaType.cardTitle, color = RumaColors.Text)
        if (step.body.isNotBlank()) Text(step.body, style = RumaType.body, color = RumaColors.Text2)
        PrimaryButton(
            step.action, Modifier.fillMaxWidth(),
            icon = when (step.kind) {
                NextStep.Kind.LIVE, NextStep.Kind.PICKUP -> Icons.AutoMirrored.Filled.DirectionsBike
                NextStep.Kind.CASH -> Icons.Filled.AccountBalanceWallet
                else -> Icons.Outlined.PhotoCamera
            },
            onClick = { onNavigate(step.route) },
        )
    }
}

/** A short, non-blocking celebration: points that reached the balance, a pickup just asked for. */
@Composable
private fun CelebrationBanner(title: String, body: String, clickLabel: String, onTimeout: () -> Unit, onClick: () -> Unit) {
    val scale = remember { Animatable(0.92f) }
    val left = remember { Animatable(1f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    // A moment, not a fixture: it leaves on its own after 7 s; the bar shows the time running out.
    LaunchedEffect(Unit) { left.animateTo(0f, tween(CREDITED_MS, easing = LinearEasing)); onTimeout() }
    Row(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .rumaOutlined(RumaColors.Gen)
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .padding(RumaSpace.lg)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(RumaSpace.xxl).clip(CircleShape).background(RumaColors.Gen), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = RumaColors.OnDark, modifier = Modifier.size(RumaSpace.lg))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RumaSpace.xs)) {
            Text(title, style = RumaType.rowTitle, color = RumaColors.Text)
            Text(body, style = RumaType.bodySmall, color = RumaColors.Text2)
            ProgressTrack(left.value, Modifier.height(3.dp), track = RumaColors.Border, fill = RumaColors.Gen)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = RumaColors.Text2)
    }
}

private const val CREDITED_MS = 7000

private val RumaArt = listOf(R.drawable.ruma_1, R.drawable.ruma_2, R.drawable.ruma_3, R.drawable.ruma_4, R.drawable.ruma_5)

/** The month's pile as a pre-rendered 3D bale (landing/3d/ruma-fardo-v2.blend) that grows with each day you recycle. */
@Composable
private fun RumaCard(days: Int) {
    val level = rumaLevel(days)
    val month = java.text.SimpleDateFormat("MMMM", java.util.Locale("es", "PE"))
        .apply { timeZone = java.util.TimeZone.getTimeZone("America/Lima") }.format(java.util.Date())
    val left = (level.next ?: days) - days
    Row(
        Modifier.fillMaxWidth().rumaCard().padding(RumaSpace.lg)
            .semantics(mergeDescendants = true) { contentDescription = "Tu ruma de $month: $days días reciclando, nivel ${level.stage} de 5" },
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 104.dp, height = 96.dp), contentAlignment = Alignment.BottomCenter) {
            Image(painterResource(RumaArt[level.stage - 1]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RumaSpace.xs)) {
            Text("TU RUMA DE ${month.uppercase(java.util.Locale("es", "PE"))}", style = RumaType.overline, color = RumaColors.Gen)
            Text("$days ${if (days == 1) "día" else "días"} reciclando", style = RumaType.cardTitle, color = RumaColors.Text)
            if (level.next != null) {
                ProgressTrack(level.progress, track = RumaColors.Border, fill = RumaColors.Gen)
                Text(
                    if (days == 0) "Cada día que registras tus residuos, tu ruma crece."
                    else "${if (left == 1) "Falta 1 día" else "Faltan $left días"} para el nivel ${level.stage + 1}",
                    style = RumaType.bodySmall, color = RumaColors.Text2,
                )
            } else {
                Text("¡Fardo completo! Tu ruma de este mes ya está amarrada.", style = RumaType.bodySmall, color = RumaColors.Text2)
            }
        }
    }
}

/** 3D renders of the ring (Blender), one per fraction days/goal reachable with goals of 1..7 days, keyed in permille. */
private val RingFrames = mapOf(0 to R.drawable.ring_0000, 143 to R.drawable.ring_0143, 167 to R.drawable.ring_0167, 200 to R.drawable.ring_0200, 250 to R.drawable.ring_0250, 286 to R.drawable.ring_0286, 333 to R.drawable.ring_0333, 400 to R.drawable.ring_0400, 429 to R.drawable.ring_0429, 500 to R.drawable.ring_0500, 571 to R.drawable.ring_0571, 600 to R.drawable.ring_0600, 667 to R.drawable.ring_0667, 714 to R.drawable.ring_0714, 750 to R.drawable.ring_0750, 800 to R.drawable.ring_0800, 833 to R.drawable.ring_0833, 857 to R.drawable.ring_0857, 1000 to R.drawable.ring_1000)

/** Dense renders every 2.5 %, only for the fill-up animation. */
private val RingFill = listOf(R.drawable.ringa_0000, R.drawable.ringa_0025, R.drawable.ringa_0050, R.drawable.ringa_0075, R.drawable.ringa_0100, R.drawable.ringa_0125, R.drawable.ringa_0150, R.drawable.ringa_0175, R.drawable.ringa_0200, R.drawable.ringa_0225, R.drawable.ringa_0250, R.drawable.ringa_0275, R.drawable.ringa_0300, R.drawable.ringa_0325, R.drawable.ringa_0350, R.drawable.ringa_0375, R.drawable.ringa_0400, R.drawable.ringa_0425, R.drawable.ringa_0450, R.drawable.ringa_0475, R.drawable.ringa_0500, R.drawable.ringa_0525, R.drawable.ringa_0550, R.drawable.ringa_0575, R.drawable.ringa_0600, R.drawable.ringa_0625, R.drawable.ringa_0650, R.drawable.ringa_0675, R.drawable.ringa_0700, R.drawable.ringa_0725, R.drawable.ringa_0750, R.drawable.ringa_0775, R.drawable.ringa_0800, R.drawable.ringa_0825, R.drawable.ringa_0850, R.drawable.ringa_0875, R.drawable.ringa_0900, R.drawable.ringa_0925, R.drawable.ringa_0950, R.drawable.ringa_0975, R.drawable.ringa_1000)

private fun ringFrame(fraction: Float): Int {
    val pm = (1000f * fraction.coerceIn(0f, 1f)).toInt()
    return RingFrames.minBy { kotlin.math.abs(it.key - pm) }.value
}

/** The arc measures days/goal; the streak is a separate count of completed weeks. */
@Composable
private fun GoalRing(week: WeekProgress) {
    val streak = if (week.streak > 0) "Racha: ${week.streak} ${if (week.streak == 1) "semana" else "semanas"}" else ""
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(RumaSpace.md)) {
        Box(
            Modifier.size(RumaSpace.xl * 12).clearAndSetSemantics {
                contentDescription = "${week.days} de ${week.goal} días esta semana. $streak"
                progressBarRangeInfo = ProgressBarRangeInfo(week.days.coerceAtMost(week.goal).toFloat(), 0f..week.goal.toFloat())
            }, contentAlignment = Alignment.Center,
        ) {
            val target = week.days.coerceAtMost(week.goal).toFloat() / week.goal.coerceAtLeast(1)
            val fill = remember { Animatable(0f) }
            var filling by remember { mutableStateOf(true) }
            LaunchedEffect(target) {
                filling = true; fill.snapTo(0f)
                fill.animateTo(target, tween(1200, easing = FastOutSlowInEasing))
                filling = false
            }
            // While filling, the dense 2.5 % renders; at rest, the exact frame for the fraction.
            val frame = if (filling) RingFill[(fill.value.coerceIn(0f, 1f) * 40).roundToInt()] else ringFrame(fill.value)
            Image(painterResource(frame), null, Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                    Text("${week.days}", style = RumaType.metricHero, color = RumaColors.Text)
                    Text("de ${week.goal}", style = RumaType.cardTitle, color = RumaColors.Text2)
                }
                Text("días esta semana", style = RumaType.body, color = RumaColors.Text2)
            }
        }
        // Below the ring to leave room for accessibility font scaling.
        if (streak.isNotEmpty()) StatusChip(streak, RumaColors.Gen)
        Text(if (week.left > 0) "Te falta ${week.left} ${if (week.left == 1) "día" else "días"} para completar tu meta." else "¡Cumpliste tu meta de esta semana!", style = RumaType.body, color = RumaColors.Text2, textAlign = TextAlign.Center)
    }
}
@Composable
private fun HomeHeader(state: HomeUiState, onBell: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RumaSpace.xs)) {
            val name = state.displayName.split(Regex("\\s+")).firstOrNull { it.isNotBlank() && !it.matches(Regex("(?i)fam\\.?|familia")) }
            Text((if (name == null) "Hola" else "Hola, $name"), color = RumaColors.Text, style = RumaType.screenTitle)
            Text("Cada acción cuenta.", color = RumaColors.Text2, style = RumaType.body)
        }
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier.size(RumaSpace.xl * 2 + RumaSpace.sm).rumaCard(RumaRadius.pill)
                    .clickable(onClickLabel = "Ver notificaciones", role = Role.Button, onClick = onBell),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Notifications,
                    if (state.hasUnreadNotifications) "Ver notificaciones sin leer" else "Ver notificaciones",
                    tint = RumaColors.Text, modifier = Modifier.size(RumaSpace.xl),
                )
            }
            if (state.hasUnreadNotifications) {
                Box(
                    Modifier.padding(top = RumaSpace.sm, end = RumaSpace.xs)
                        .size(RumaSpace.sm).clip(CircleShape).background(RumaColors.Money),
                )
            }
        }
    }
}

private val SampleHome = HomeUiState(
    displayName = "Fam. Nureña", ecoPoints = 4250, pendingPoints = 98,
    riderEtaText = "A 3 cuadras · Llega en ~5 min", hasUnreadNotifications = true,
    riderIncoming = true, riderTitle = "Carlos va en camino",
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun GeneratorHomePreview() {
    RumaTheme { GeneratorHomeScreen(state = SampleHome, week = WeekProgress(2, 3, 1, 4)) }
}
