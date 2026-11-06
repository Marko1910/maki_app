package com.example.maki.ui.screens.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.maki.navigation.RiderRoutes
import com.example.maki.ui.components.makiScreen
import com.example.maki.ui.components.HeroCard
import com.example.maki.ui.components.MetricTile
import com.example.maki.ui.components.NavRow
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.components.ProgressTrack
import com.example.maki.ui.components.SectionLabel
import com.example.maki.ui.components.StatusChip
import androidx.compose.material3.HorizontalDivider
import com.example.maki.ui.components.makiHero
import com.example.maki.ui.theme.MakiGradient
import com.example.maki.ui.components.makiCard
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiColors
import com.example.maki.ui.theme.MakiRadius
import com.example.maki.ui.theme.MakiSpace
import com.example.maki.ui.theme.MakiType
import java.util.Calendar

// Neutral defaults: every field is filled by RiderViewModel from the database.
// A placeholder that looks like real data is worse than an em dash.
data class RiderDashboardUiState(
    val riderName: String = "",
    val zone: String = "",
    val unreadCount: Int = 0,
    /** Shift switch: off and the dispatcher stops assigning stops to this rider. */
    val onShift: Boolean = true,
    /** "Sales hoy 3:00 p. m." when a departure is booked; blank when none is. */
    val shiftPlan: String = "",
    /** Today's workload: the rider's own stops, done + still to do. */
    val pickupsTotal: String = "0",
    val completed: String = "0/0",
    /** Open requests anyone can take — not counted as the rider's own work. */
    val onBoard: String = "0",
    val boardHint: String = "",
    val earnedToday: String = "S/0.00",
    val hasNextStop: Boolean = false,
    /** False while the next stop is still an open request nobody has taken. */
    val nextStopIsMine: Boolean = false,
    val nextStopName: String = "Sin recojos pendientes",
    val nextStopAddress: String = "Te avisaremos cuando haya solicitudes",
    val nextStopDistance: String = "",
    val nextStopEta: String = "",
    val nextStopChips: List<String> = emptyList(),
    val monthEarnings: String = "S/0.00",
    val monthGrowth: String = "",
    val goalProgress: Float = 0f,
    val goalPctText: String = "",
    val goalText: String = "",
    val rankingTitle: String = "",
    val rankingSubtitle: String = "",
    val weather: String = "",
    val weatherHint: String = "",
)

/**
 * The rider's home: what they earn today, the one stop to go to next, and the
 * open board.
 *
 * Today's earnings lead so the rider can check progress between stops; the
 * shift controls and next destination remain within the same scrolling surface.
 */
@Composable
fun RiderDashboardScreen(
    state: RiderDashboardUiState = RiderDashboardUiState(),
    busy: Boolean = false,
    onNavigate: (String) -> Unit = {},
    onShiftChange: (Boolean) -> Unit = {},
    /** Books a departure (epoch millis) or clears it with null. */
    onPlanShift: (Long?) -> Unit = {},
) {
    var pickTime by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().makiScreen(MakiColors.Rider).statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = MakiSpace.gutter, end = MakiSpace.gutter, top = MakiSpace.sm, bottom = MakiSpace.xl),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.stack),
    ) {
        Header(state, onBell = { onNavigate(RiderRoutes.NOTIFICATIONS) })
        ShiftCard(
            state,
            onChange = onShiftChange,
            onPlan = { pickTime = true },
            onClearPlan = { onPlanShift(null) },
        )
        SiguienteParada(state, busy, onGo = { onNavigate(RiderRoutes.NAVIGATION) })
        Text("Tus recojos", style = MakiType.sectionHeading, color = MakiColors.Text)
        Column(Modifier.makiCard()) {
            NavRow(Icons.Filled.Inventory2, "Tablero de solicitudes", state.boardHint.ifBlank { "Mira las solicitudes abiertas cerca de ti" },
                accent = MakiColors.Rider, framed = false, onClick = { onNavigate(RiderRoutes.BOARD) })
            HorizontalDivider(color = MakiColors.Border)
            NavRow(Icons.Filled.Map, "Mi ruta del día", "Orden más rápido por calles y hora de llegada",
                accent = MakiColors.Rider, framed = false, onClick = { onNavigate(RiderRoutes.ROUTE) })
            HorizontalDivider(color = MakiColors.Border)
            NavRow(Icons.Filled.LocalShipping, "Entregar en acopio", "Lleva tus recojos cerrados y cobra al pesaje",
                accent = MakiColors.Rider, framed = false, onClick = { onNavigate(RiderRoutes.DELIVERY) })
        }
        Text("${state.completed} completados · ${state.onBoard} en el tablero", style = MakiType.label, color = MakiColors.Text2)
        Text("Tu progreso", style = MakiType.sectionHeading, color = MakiColors.Text)
        GananciasWidget(state, onClick = { onNavigate(RiderRoutes.EARNINGS) })
        RankingWidget(state, onClick = { onNavigate(RiderRoutes.RANKING) })
        // Nothing feeds the weather yet; an empty card reads as a broken one.
        if (state.weather.isNotBlank()) ClimaWidget(state)
    }

    if (pickTime) {
        ShiftTimeDialog(
            onDismiss = { pickTime = false },
            onPick = { millis -> pickTime = false; onPlanShift(millis) },
        )
    }
}

/**
 * Picks the hour the rider goes out. Anything already past today is taken as
 * tomorrow — nobody schedules a departure into the past.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftTimeDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val now = remember { Calendar.getInstance() }
    val picker = rememberTimePickerState(
        initialHour = (now.get(Calendar.HOUR_OF_DAY) + 1) % 24,
        initialMinute = 0,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MakiColors.Surface,
        title = { Text("¿A qué hora sales?", style = MakiType.cardTitle, color = MakiColors.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MakiSpace.md), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = picker)
                Text(
                    "Tu turno se encenderá solo a esa hora y te avisaremos.",
                    color = MakiColors.Text2, style = MakiType.label,
                )
            }
        },
        confirmButton = {
            TextButton(modifier = Modifier.semantics { contentDescription = "Programar salida" }, onClick = {
                val at = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, picker.hour)
                    set(Calendar.MINUTE, picker.minute)
                    set(Calendar.SECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                }
                onPick(at.timeInMillis)
            }) { Text("Programar", style = MakiType.button, color = MakiColors.Rider) }
        },
        dismissButton = {
            TextButton(modifier = Modifier.semantics { contentDescription = "Cancelar programación" }, onClick = onDismiss) { Text("Cancelar", style = MakiType.button, color = MakiColors.Text2) }
        },
    )
}

@Composable
private fun Header(state: RiderDashboardUiState, onBell: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = MakiSpace.xs / 2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs / 4)) {
            Text((if (state.riderName.isBlank()) "Hola" else "Hola, ${state.riderName}"), color = MakiColors.Text, style = MakiType.screenTitle)
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, tint = MakiColors.Rider, modifier = Modifier.size(MakiSpace.md))
                Text(state.zone, color = MakiColors.Text2, style = MakiType.bodySmall)
            }
        }
        Box(contentAlignment = Alignment.TopEnd) {
            CircleButton(Icons.Outlined.Notifications, onBell)
            if (state.unreadCount > 0) {
                Box(
                    Modifier.padding(top = MakiSpace.sm, end = MakiSpace.xs).size(MakiSpace.lg).clip(CircleShape)
                        .background(MakiColors.Error).border(MakiSpace.xs / 2, MakiColors.Surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${state.unreadCount}", color = MakiColors.OnDark, style = MakiType.caption) }
            }
        }
    }
}

@Composable
private fun CircleButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.makiCard(MakiRadius.pill).size(MakiSpace.lg * 3)
            .clickable(onClickLabel = "Ver notificaciones", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MakiColors.Text, modifier = Modifier.size(MakiSpace.xl)) }
}

/**
 * The shift: the one control that decides whether work arrives at all, plus the
 * rider's own plan — go out now, or promise an hour and let the app open the
 * shift by itself.
 */
@Composable
private fun ShiftCard(
    state: RiderDashboardUiState,
    onChange: (Boolean) -> Unit,
    onPlan: () -> Unit,
    onClearPlan: () -> Unit,
) {
    val tint = if (state.onShift) MakiColors.Success else MakiColors.Text2
    Column(
        Modifier.makiHero(MakiGradient.rider, glow = MakiGradient.glowOf(MakiColors.Rider))
            .fillMaxWidth().padding(start = MakiSpace.lg, top = MakiSpace.sm, end = MakiSpace.sm, bottom = MakiSpace.md),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.sm),
    ) {
        Text("Ganado hoy", color = MakiColors.OnRiderSoft, style = MakiType.bodySmall)
        Text(state.earnedToday, color = MakiColors.OnDark, style = MakiType.metricLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(MakiSpace.sm).clip(CircleShape).background(tint))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MakiSpace.xs / 4)) {
                Text(
                    if (state.onShift) "En turno" else "Fuera de turno",
                    color = MakiColors.OnDark, style = MakiType.cardTitle,
                )
                Text(
                    if (state.onShift) "Recibes recojos del despacho automático"
                    else "No te asignarán recojos; puedes tomarlos del tablero",
                    color = MakiColors.OnRiderSoft, style = MakiType.label,
                )
            }
            Switch(
                checked = state.onShift,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MakiColors.OnDark,
                    checkedTrackColor = MakiColors.Success,
                    uncheckedThumbColor = MakiColors.Surface,
                    uncheckedTrackColor = MakiColors.Border,
                ),
                modifier = Modifier.heightIn(min = MakiSpace.lg * 3).semantics {
                    contentDescription = if (state.onShift) "En turno, tocar para salir de turno"
                                         else "Fuera de turno, tocar para entrar en turno"
                },
            )
        }
        if (state.shiftPlan.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(MakiRadius.badge)).background(MakiColors.WhiteTint)
                    .padding(horizontal = MakiSpace.md, vertical = MakiSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Alarm, null, tint = MakiColors.OnDark, modifier = Modifier.size(MakiSpace.lg))
                Text(
                    state.shiftPlan, color = MakiColors.OnDark, style = MakiType.bodySmall, modifier = Modifier.weight(1f),
                )
                Text(
                    "Quitar", color = MakiColors.OnRiderSoft, style = MakiType.label,
                    modifier = Modifier.clip(RoundedCornerShape(MakiRadius.chip)).clickable(onClickLabel = "Quitar salida programada", role = Role.Button, onClick = onClearPlan)
                        .heightIn(min = MakiSpace.lg * 3).padding(horizontal = MakiSpace.sm, vertical = MakiSpace.xs),
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm)) {
                if (!state.onShift) {
                    ShiftAction(Modifier.weight(1f), Icons.Filled.PlayArrow, "Salir ahora", filled = true) { onChange(true) }
                }
                ShiftAction(Modifier.weight(1f), Icons.Filled.Alarm, "Programar salida", filled = false, onClick = onPlan)
            }
        }
    }
}

@Composable
private fun ShiftAction(modifier: Modifier, icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(MakiRadius.badge))
            .background(if (filled) MakiColors.Rider else MakiColors.WhiteTint)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .heightIn(min = MakiSpace.lg * 3).padding(vertical = MakiSpace.md),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MakiColors.OnDark, modifier = Modifier.size(MakiSpace.lg))
        Text(
            label, color = MakiColors.OnDark,
            style = MakiType.button,
        )
    }
}

/** What today has produced so far — the number a rider checks between stops. */
@Composable
private fun TodayHero(state: RiderDashboardUiState) {
    HeroCard(accent = MakiColors.Rider, overline = "Ganado hoy", metric = state.earnedToday)
}

/**
 * The next stop, with the one action that matters. An open request says so and
 * the button offers to take it; an accepted one goes straight to navigation.
 */
@Composable
private fun SiguienteParada(state: RiderDashboardUiState, busy: Boolean, onGo: () -> Unit) {
    Column(
        Modifier.makiCard()
            .fillMaxWidth().padding(MakiSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MakiSpace.md),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, null, tint = MakiColors.Rider, modifier = Modifier.size(MakiSpace.lg))
            Text(
                if (state.nextStopIsMine) "SIGUIENTE PARADA" else "DISPONIBLE AHORA",
                color = MakiColors.Rider, style = MakiType.label,
                modifier = Modifier.weight(1f),
            )
            if (state.nextStopDistance.isNotBlank() && state.nextStopIsMine) {
                Text(state.nextStopDistance, color = MakiColors.Text2, style = MakiType.label)
            }
        }
        Text(state.nextStopName, color = MakiColors.Text, style = MakiType.sectionHeading)
        Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Place, null, tint = MakiColors.Text2, modifier = Modifier.size(MakiSpace.lg))
            Text(state.nextStopAddress, color = MakiColors.Text2, style = MakiType.bodySmall)
        }
        if (state.nextStopEta.isNotBlank()) {
            StatusChip(state.nextStopEta, MakiColors.Rider)
        }
        if (state.nextStopChips.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(MakiSpace.sm)) {
                state.nextStopChips.take(3).forEach { MaterialChip(it) }
            }
        }
        if (state.hasNextStop) {
            PrimaryButton(
                if (state.nextStopIsMine) "Ir a la parada" else "Tomar este recojo",
                Modifier.fillMaxWidth(),
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                color = MakiColors.Rider,
                enabled = !busy,
                onClick = onGo,
            )
        }
    }
}

@Composable
private fun MaterialChip(label: String) {
    StatusChip(label, MakiColors.Rider)
}

/** The open board used to be a number nobody could open. Now it is a door. */
@Composable
private fun TableroCard(state: RiderDashboardUiState, onClick: () -> Unit) {
    NavRow(
        icon = Icons.Filled.Inventory2, title = "Tablero de solicitudes",
        subtitle = state.boardHint.ifBlank { "Mira las solicitudes abiertas cerca de ti" },
        accent = MakiColors.Rider, onClick = onClick,
    )
}

@Composable
private fun RutaCard(onClick: () -> Unit) {
    NavRow(Icons.Filled.Map, "Mi ruta del día", "Orden más rápido por calles y hora de llegada",
        accent = MakiColors.Rider, onClick = onClick)
}

/**
 * Always visible, like the route card. Counting the load here would cost a query on
 * every dashboard poll, and a rider who just closed their last stop has to be able
 * to find this without the card first noticing.
 */
@Composable
private fun EntregaCard(onClick: () -> Unit) {
    NavRow(Icons.Filled.LocalShipping, "Entregar en acopio", "Lleva tus recojos cerrados y cobra al pesaje",
        accent = MakiColors.Rider, onClick = onClick)
}

@Composable
private fun GananciasWidget(state: RiderDashboardUiState, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MakiSpace.sm)) {
        MetricTile(Icons.Filled.AccountBalanceWallet, state.monthEarnings, "Ganancias del mes",
            MakiColors.Rider, Modifier.fillMaxWidth(), onClick = onClick)
        if (state.monthGrowth.isNotBlank()) StatusChip(state.monthGrowth, MakiColors.Success)
        ProgressTrack(state.goalProgress, track = MakiColors.Border, fill = MakiColors.Rider)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(state.goalPctText, color = MakiColors.Text2, style = MakiType.label)
            Text(state.goalText, color = MakiColors.Text2, style = MakiType.label)
        }
    }
}

@Composable
private fun RankingWidget(state: RiderDashboardUiState, onClick: () -> Unit) {
    NavRow(
        Icons.Filled.EmojiEvents, state.rankingTitle.ifBlank { "Ranking de riders" },
        state.rankingSubtitle.ifBlank { "Mira tu posición de esta semana" },
        accent = MakiColors.Rider, onClick = onClick,
    )
}

@Composable
private fun ClimaWidget(state: RiderDashboardUiState) {
    Row(
        Modifier.clip(RoundedCornerShape(MakiRadius.card)).background(MakiColors.RiderTint).fillMaxWidth().padding(MakiSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(MakiSpace.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.WbSunny, null, tint = MakiColors.Money, modifier = Modifier.size(MakiSpace.lg * 1.5f))
        Column(verticalArrangement = Arrangement.spacedBy(MakiSpace.xs / 4)) {
            Text(state.weather, color = MakiColors.Text, style = MakiType.rowTitle)
            Text(state.weatherHint, color = MakiColors.Text2, style = MakiType.label)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun RiderDashboardPreview() {
    MAKITheme {
        RiderDashboardScreen(
            state = SampleDashboard,
        )
    }
}

private val SampleDashboard = RiderDashboardUiState(
    riderName = "Carlos", zone = "Moto · En turno", unreadCount = 2,
    completed = "2/5", onBoard = "4", boardHint = "4 recojos esperando un Eco-Rider",
    earnedToday = "S/12.40", hasNextStop = true, nextStopIsMine = true,
    nextStopName = "Fam. Quispe", nextStopAddress = "Jr. Las Flores 240",
    nextStopDistance = "A 800 m", nextStopEta = "Llegas 4:35 p. m.",
    nextStopChips = listOf("12 PET", "4 latas"),
    monthEarnings = "S/340.00", goalProgress = 0.75f,
    goalPctText = "75% de tu meta", goalText = "Meta: S/450.00",
)
