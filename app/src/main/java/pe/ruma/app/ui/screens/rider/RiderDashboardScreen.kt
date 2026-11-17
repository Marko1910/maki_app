package pe.ruma.app.ui.screens.rider

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
import pe.ruma.app.navigation.RiderRoutes
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import pe.ruma.app.ui.components.TextAction
import pe.ruma.app.ui.components.RumaRow
import pe.ruma.app.ui.components.RumaList
import pe.ruma.app.ui.components.HeroRule
import pe.ruma.app.ui.components.HeroChip
import pe.ruma.app.ui.components.GlassBadge
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.ProgressTrack
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.components.rumaCard
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType
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
        Modifier.fillMaxSize().rumaScreen(RumaColors.Rider).statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = RumaSpace.gutter, end = RumaSpace.gutter, top = RumaSpace.sm, bottom = RumaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.stack),
    ) {
        Header(state, onBell = { onNavigate(RiderRoutes.NOTIFICATIONS) })
        ShiftCard(
            state,
            onChange = onShiftChange,
            onPlan = { pickTime = true },
            onClearPlan = { onPlanShift(null) },
        )
        SiguienteParada(state, busy, onGo = { onNavigate(RiderRoutes.NAVIGATION) })
        Text("Tus recojos", style = RumaType.sectionHeading, color = RumaColors.Text)
        RumaList(
            listOf(
                Triple(Icons.Filled.Inventory2, "Tablero de solicitudes", state.boardHint.ifBlank { "Mira las solicitudes abiertas cerca de ti" }) to RiderRoutes.BOARD,
                Triple(Icons.Filled.Map, "Mi ruta del día", "Orden más rápido por calles y hora de llegada") to RiderRoutes.ROUTE,
                Triple(Icons.Filled.LocalShipping, "Entregar en acopio", "Lleva tus recojos cerrados y cobra al pesaje") to RiderRoutes.DELIVERY,
            ),
        ) { (row, route) ->
            RumaRow(row.second, subtitle = row.third, leading = { GlassBadge(icon = row.first) }, onClick = { onNavigate(route) })
        }
        Text("${state.completed} completados · ${state.onBoard} en el tablero", style = RumaType.label, color = RumaColors.Text2)
        Text("Tu progreso", style = RumaType.sectionHeading, color = RumaColors.Text)
        Progreso(
            state,
            onEarnings = { onNavigate(RiderRoutes.EARNINGS) },
            onRanking = { onNavigate(RiderRoutes.RANKING) },
        )
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
        containerColor = RumaColors.Surface,
        title = { Text("¿A qué hora sales?", style = RumaType.cardTitle, color = RumaColors.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.md), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = picker)
                Text(
                    "Tu turno se encenderá solo a esa hora y te avisaremos.",
                    color = RumaColors.Text2, style = RumaType.label,
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
            }) { Text("Programar", style = RumaType.button, color = RumaColors.Rider) }
        },
        dismissButton = {
            TextButton(modifier = Modifier.semantics { contentDescription = "Cancelar programación" }, onClick = onDismiss) { Text("Cancelar", style = RumaType.button, color = RumaColors.Text2) }
        },
    )
}

@Composable
private fun Header(state: RiderDashboardUiState, onBell: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = RumaSpace.xs / 2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RumaSpace.xs / 4)) {
            Text((if (state.riderName.isBlank()) "Hola" else "Hola, ${state.riderName}"), color = RumaColors.Text, style = RumaType.screenTitle)
            Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, tint = RumaColors.Rider, modifier = Modifier.size(RumaSpace.md))
                Text(state.zone, color = RumaColors.Text2, style = RumaType.bodySmall)
            }
        }
        Box(contentAlignment = Alignment.TopEnd) {
            CircleButton(Icons.Outlined.Notifications, onBell)
            if (state.unreadCount > 0) {
                Box(
                    Modifier.padding(top = RumaSpace.sm, end = RumaSpace.xs).size(RumaSpace.lg).clip(CircleShape)
                        .background(RumaColors.Error).border(RumaSpace.xs / 2, RumaColors.Surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${state.unreadCount}", color = RumaColors.OnDark, style = RumaType.caption) }
            }
        }
    }
}

@Composable
private fun CircleButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.rumaCard(RumaRadius.pill).size(RumaSpace.lg * 3)
            .clickable(onClickLabel = "Ver notificaciones", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = RumaColors.Text, modifier = Modifier.size(RumaSpace.xl)) }
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
    Column(
        Modifier.rumaHero(RumaGradient.rider, glow = RumaGradient.glowOf(RumaColors.Rider))
            .fillMaxWidth().padding(RumaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TU TURNO", color = RumaColors.OnRiderSoft, style = RumaType.overline, modifier = Modifier.weight(1f))
            Switch(
                checked = state.onShift,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = RumaColors.OnDark,
                    checkedTrackColor = RumaColors.Success,
                    checkedBorderColor = RumaColors.Success,
                    uncheckedThumbColor = RumaColors.OnDark,
                    uncheckedTrackColor = RumaColors.WhiteTint,
                    uncheckedBorderColor = RumaColors.OnGradientTrack,
                ),
                modifier = Modifier.heightIn(min = RumaSpace.lg * 3).semantics {
                    contentDescription = if (state.onShift) "En turno, tocar para salir de turno"
                                         else "Fuera de turno, tocar para entrar en turno"
                },
            )
        }
        Text(
            if (state.onShift) "En turno" else "Fuera de turno",
            color = RumaColors.OnDark, style = RumaType.sectionHeading,
        )
        Text(
            if (state.onShift) "Recibes recojos del despacho automático"
            else "No te asignarán recojos; puedes tomarlos del tablero",
            color = RumaColors.OnRiderSoft, style = RumaType.bodySmall,
        )
        HeroRule()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ganado hoy", color = RumaColors.OnRiderSoft, style = RumaType.body, modifier = Modifier.weight(1f))
            Text(state.earnedToday, color = RumaColors.OnDark, style = RumaType.sectionHeading)
        }
        if (state.shiftPlan.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroChip(state.shiftPlan)
                Box(Modifier.weight(1f))
                TextAction("Quitar", RumaColors.OnDark, "Quitar salida programada", onClearPlan)
            }
        } else if (!state.onShift) {
            TextAction("Programar salida", RumaColors.OnDark, "Programar la hora de salida", onPlan)
        }
    }
}

/**
 * The next stop, with the one action that matters. An open request says so and
 * the button offers to take it; an accepted one goes straight to navigation.
 */
@Composable
private fun SiguienteParada(state: RiderDashboardUiState, busy: Boolean, onGo: () -> Unit) {
    Column(
        Modifier.rumaCard()
            .fillMaxWidth().padding(RumaSpace.lg),
        verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
    ) {
        if (state.hasNextStop) Text(
            if (state.nextStopIsMine) "SIGUIENTE PARADA" else "DISPONIBLE AHORA",
            color = RumaColors.Text2, style = RumaType.overline,
        )
        Text(state.nextStopName, color = RumaColors.Text, style = RumaType.sectionHeading)
        Text(state.nextStopAddress, color = RumaColors.Text2, style = RumaType.body)
        val distanceAndTime = listOf(state.nextStopDistance, state.nextStopEta).filter { it.isNotBlank() }.joinToString(" · ")
        if (distanceAndTime.isNotBlank()) Text(distanceAndTime, color = RumaColors.Text, style = RumaType.bodySmall)
        if (state.nextStopChips.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                state.nextStopChips.take(3).forEach { MaterialChip(it) }
            }
        }
        if (state.hasNextStop) {
            PrimaryButton(
                if (state.nextStopIsMine) "Ir a la parada" else "Tomar este recojo",
                Modifier.fillMaxWidth(),
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                color = RumaColors.Rider,
                enabled = !busy,
                onClick = onGo,
            )
        }
    }
}

@Composable
private fun MaterialChip(label: String) {
    StatusChip(label, RumaColors.Rider)
}

/**
 * The month's earnings against the goal and the ranking, as one grouped list
 * (PWA "Tu progreso"): two rows that each open their screen.
 */
@Composable
private fun Progreso(state: RiderDashboardUiState, onEarnings: () -> Unit, onRanking: () -> Unit) {
    Column(Modifier.fillMaxWidth().rumaCard()) {
        Column(
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = "Ver mis ganancias", role = Role.Button, onClick = onEarnings)
                .padding(horizontal = RumaSpace.lg, vertical = RumaSpace.md),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ganancias del mes", color = RumaColors.Text, style = RumaType.rowTitle, modifier = Modifier.weight(1f))
                Text(state.monthEarnings, color = RumaColors.Text, style = RumaType.rowTitle)
            }
            if (state.monthGrowth.isNotBlank()) StatusChip(state.monthGrowth, RumaColors.Success)
            if (state.goalText.isNotBlank()) {
                ProgressTrack(state.goalProgress, track = RumaColors.Hair, fill = RumaColors.Rider)
                Row {
                    Text(state.goalText, color = RumaColors.Text2, style = RumaType.bodySmall, modifier = Modifier.weight(1f))
                    Text(state.goalPctText, color = RumaColors.Text2, style = RumaType.bodySmall)
                }
            }
        }
        Text(
            "S/ 0,35 por kg pesado + S/ 6 por ruta de 12 recojos",
            color = RumaColors.Text2, style = RumaType.bodySmall,
            modifier = Modifier.padding(horizontal = RumaSpace.lg).padding(bottom = RumaSpace.md),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(RumaColors.Hair))
        RumaRow(
            state.rankingTitle.ifBlank { "Ranking de riders" },
            subtitle = state.rankingSubtitle.ifBlank { "Mira tu posición de esta semana" },
            leading = { GlassBadge(icon = Icons.Filled.EmojiEvents) },
            onClick = onRanking,
        )
    }
}

@Composable
private fun ClimaWidget(state: RiderDashboardUiState) {
    Row(
        Modifier.clip(RoundedCornerShape(RumaRadius.card)).background(RumaColors.RiderTint).fillMaxWidth().padding(RumaSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(RumaSpace.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.WbSunny, null, tint = RumaColors.Money, modifier = Modifier.size(RumaSpace.lg * 1.5f))
        Column(verticalArrangement = Arrangement.spacedBy(RumaSpace.xs / 4)) {
            Text(state.weather, color = RumaColors.Text, style = RumaType.rowTitle)
            Text(state.weatherHint, color = RumaColors.Text2, style = RumaType.label)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun RiderDashboardPreview() {
    RumaTheme {
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
