package pe.ruma.app.ui.screens.rider

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import pe.ruma.app.ui.components.TextAction
import pe.ruma.app.data.RumaLocation
import pe.ruma.app.data.RoutePlanner
import pe.ruma.app.ui.components.rumaScreen
import pe.ruma.app.ui.components.BackButton
import pe.ruma.app.ui.components.RumaEmpty
import pe.ruma.app.ui.components.OsmMap
import pe.ruma.app.ui.components.OsmMarker
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.SecondaryButton
import pe.ruma.app.ui.components.SectionLabel
import pe.ruma.app.ui.components.StatusChip
import pe.ruma.app.ui.components.rumaCard
import pe.ruma.app.ui.components.rumaHero
import pe.ruma.app.ui.screens.generator.RumaTime
import pe.ruma.app.ui.theme.RumaTheme
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaGradient
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint

// Immersive navigation screen for the stop the rider actually accepted.
data class RiderNavigationUiState(
    val stopName: String,
    val address: String,
    val distance: String,
    val eta: String,
    val instruction: String,
    val materials: List<String> = emptyList(),
    /** Destination coordinates; null when the pickup has none recorded yet. */
    val lat: Double? = null,
    val lng: Double? = null,
    /** Fastest street path from the rider to the door, when the router answered. */
    val path: List<RoutePlanner.LatLng> = emptyList(),
    val roadKm: Double? = null,
    val roadMinutes: Int? = null,
    /** The family's phone, to ring the bell from the street. */
    val phone: String? = null,
    /** "Parada 2 de 4" when the rider has a route; empty for a single stop. */
    val position: String = "",
    /** Where the stop stands: en camino / llegando / contando en la puerta. */
    val statusLabel: String = "En camino",
    /** At the door the ETA stops being the headline — the count does. */
    val atDoor: Boolean = false,
    val roadLoading: Boolean = false,
    val roadError: String? = null,
    val maneuver: String? = null,
    val maneuverMeters: Int? = null,
    val maneuverKey: String? = null,
)

/**
 * Map on top, destination sheet below. The sheet used to float over the map,
 * and the map framed both pins across the full screen — so "Tú" ended up hidden
 * behind the card. The map now only gets the space nothing covers.
 */
@Composable
fun RiderNavigationScreen(
    state: RiderNavigationUiState? = null,
    busy: Boolean = false,
    onBack: () -> Unit = {},
    onArrived: () -> Unit = {},
    /** Every fresh fix: the VM re-routes and flags "llegando" near the door. */
    onPosition: (RoutePlanner.LatLng) -> Unit = {},
    onRelease: () -> Unit = {},
    onRetry: () -> Unit = {},
    /** Optional location source for deterministic device tests; production uses the GPS. */
    positionSource: (suspend () -> RumaLocation.NavigationFix?)? = null,
) {
    if (state == null) {
        // No accepted stop: say so instead of drawing a map to nowhere.
        Column(
            Modifier.fillMaxSize().rumaScreen(RumaColors.Rider).statusBarsPadding(),
        ) {
            Box(Modifier.padding(RumaSpace.gutter)) { BackButton(onBack) }
            RumaEmpty(
                icon = Icons.Filled.Navigation,
                title = "No hay un recojo en curso",
                hint = "Acepta una solicitud desde el inicio para empezar a navegar.",
                accent = RumaColors.Rider,
            )
        }
        return
    }
    val destination = state.lat?.let { lat -> state.lng?.let { lng -> GeoPoint(lat, lng) } }
    val ctx = LocalContext.current
    var askRelease by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    var recenter by remember { mutableStateOf(0) }
    var voice by remember { mutableStateOf(false) }
    var voiceReady by remember { mutableStateOf(false) }
    var spoken by remember { mutableStateOf<String?>(null) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(ctx) {
        val engine = TextToSpeech(ctx) { status -> voiceReady = status == TextToSpeech.SUCCESS }
        tts = engine
        onDispose { engine.stop(); engine.shutdown() }
    }
    var here by remember { mutableStateOf<RoutePlanner.LatLng?>(null) }
    var locationMessage by remember { mutableStateOf<String?>("Buscando tu ubicación…") }
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    LaunchedEffect(voice, voiceReady, state.maneuverKey, state.maneuverMeters, locationMessage) {
        if (!voice || locationMessage != null) { tts?.stop(); return@LaunchedEffect }
        val meters = state.maneuverMeters ?: return@LaunchedEffect
        if (voiceReady && meters <= 150 && state.maneuverKey != spoken && state.maneuver != null) {
            val language = tts?.setLanguage(Locale.forLanguageTag("es-PE"))
            if (language != null && language >= TextToSpeech.LANG_AVAILABLE) {
                tts?.speak("En $meters metros, ${state.maneuver}", TextToSpeech.QUEUE_FLUSH, null, state.maneuverKey)
                spoken = state.maneuverKey
                voiceError = null
            } else { voice = false; voiceError = "Instala una voz en español en los ajustes de texto a voz." }
        }
    }

    // The rider's own position, refreshed while this screen is open — the map is
    // useless without "you are here", and the distance banner is a guess without it.
    val report by rememberUpdatedState(onPosition)
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while (true) {
                    val fix = if (positionSource != null) positionSource() else RumaLocation.navigationFix(ctx)
                    locationMessage = when {
                        positionSource == null && !RumaLocation.hasPermission(ctx) -> "Permite la ubicación precisa en los ajustes de Ruma."
                        fix == null -> "Esperando señal GPS. Comprueba que la ubicación esté activada."
                        fix.accuracyMeters > 60 -> "La ubicación es imprecisa. Busca un lugar con mejor señal."
                        else -> null
                    }
                    here = fix?.takeIf { it.accuracyMeters <= 60 }?.point
                    here?.let { report(it) }
                    delay(HERE_REFRESH_MS)
                }
            } finally {
                here = null
                locationMessage = "Esperando una ubicación reciente…"
                tts?.stop()
            }
        }
    }
    val herePoint = here?.let { GeoPoint(it.lat, it.lng) }

    // Only display road estimates from a real routing response.
    val km = state.roadKm
    val distanceLabel = km?.let { RoutePlanner.distanceLabel(it) } ?: "—"
    val minutes = state.roadMinutes.takeIf { locationMessage == null }
    val arrivalClock = when {
        state.atDoor -> "En el punto"
        minutes != null -> RumaTime.clockPlus(minutes)
        else -> "—"
    }
    // The headline a rider can act on: the hour they knock, not a countdown.
    val arrivalLabel = when {
        state.atDoor -> "Ya estás en el punto"
        minutes != null -> "Llegas ${RumaTime.clockPlus(minutes)}"
        else -> state.statusLabel
    }

    BoxWithConstraints(Modifier.fillMaxSize().rumaScreen(RumaColors.Rider)) {
    val sheetMax = maxHeight * 0.50f
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (destination != null) {
                OsmMap(
                    center = herePoint ?: destination,
                    modifier = Modifier.matchParentSize(),
                    zoom = 17.0,
                    markers = listOfNotNull(
                        OsmMarker(destination, state.stopName, RumaColors.Gen.toArgb()),
                        herePoint?.let { OsmMarker(it, "Tú", current = true) },
                    ),
                    route = state.path.map { GeoPoint(it.lat, it.lng) },
                    followFirst = follow,
                    recenter = recenter,
                    onMapGesture = { follow = false },
                )
            }
            // Back + the next instruction over the map (PWA `.nav-heading`): a flat
            // accent card, the distance to the turn above the turn itself.
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(RumaSpace.gutter),
                horizontalArrangement = Arrangement.spacedBy(RumaSpace.md), verticalAlignment = Alignment.Top,
            ) {
                BackButton(onBack)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(RumaRadius.card)).background(RumaColors.Rider)
                        .padding(RumaSpace.lg)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(RumaSpace.xs),
                ) {
                    Text(
                        state.maneuverMeters?.takeIf { locationMessage == null }?.let { "En $it m" }
                            ?: state.position.ifBlank { state.statusLabel },
                        color = RumaColors.OnDark, style = RumaType.bodySmall,
                    )
                    Text(
                        when {
                            destination == null -> "Destino sin ubicación"
                            locationMessage != null -> "Esperando ubicación GPS"
                            state.roadLoading && state.path.isEmpty() -> "Calculando ruta por calles"
                            state.roadError != null -> "Ruta sin actualizar"
                            state.path.isEmpty() -> "Esperando ruta"
                            else -> state.maneuver ?: arrivalLabel
                        },
                        color = RumaColors.OnDark, style = RumaType.rowTitle,
                    )
                }
            }
            TextButton(onClick = { follow = true; recenter++ }, enabled = herePoint != null,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = RumaSpace.gutter, bottom = RumaSpace.xxl)
                    .clip(RoundedCornerShape(RumaRadius.badge)).background(RumaColors.Surface)) {
                Icon(Icons.Filled.MyLocation, null, tint = RumaColors.Rider, modifier = Modifier.size(RumaSpace.lg + RumaSpace.xs / 2))
                Text("Centrar", style = RumaType.button, color = RumaColors.Rider, modifier = Modifier.padding(start = RumaSpace.sm))
            }
        }

        // The destination sheet (PWA `.nav-summary`): solid, so the map never shows through the text.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = RumaRadius.hero, topEnd = RumaRadius.hero))
                .background(RumaColors.Surface)
                .heightIn(max = sheetMax).verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(RumaSpace.xl),
            verticalArrangement = Arrangement.spacedBy(RumaSpace.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(
                    listOf("Destino", state.position, state.statusLabel).filter { it.isNotBlank() }.joinToString(" · "),
                    Modifier.weight(1f),
                )
                if (state.lat != null && state.lng != null) {
                    TextAction(
                        if (voice) "Silenciar guía" else "Activar voz", RumaColors.Rider,
                        if (voice) "Silenciar la guía por voz" else "Activar la guía por voz",
                    ) { voice = !voice; if (voice) spoken = null }
                }
            }
            Text(state.stopName, color = RumaColors.Text, style = RumaType.sectionHeading)
            Text(state.address, color = RumaColors.Text, style = RumaType.body)
            // The reference ("portón verde") is read at the door, not while riding.
            if (state.instruction.isNotBlank()) {
                Text(state.instruction, color = RumaColors.Text2, style = RumaType.bodySmall)
            }
            if (destination == null) {
                Text("Esta parada no tiene ubicación en el mapa. Llama al recolector.", color = RumaColors.Error, style = RumaType.bodySmall)
            }
            if (state.materials.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                    state.materials.forEach { MaterialChip(it) }
                }
            }
            locationMessage?.let { NavNotice(it) }
            if (state.roadError != null) {
                NavNotice(state.roadError) {
                    TextAction("Reintentar ruta", RumaColors.Rider) { if (!state.roadLoading) onRetry() }
                }
            }
            voiceError?.let { Text(it, style = RumaType.bodySmall, color = RumaColors.Error) }
            // Two figures between hairlines (PWA `.nav-metrics`).
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(RumaColors.Hair))
                Row(Modifier.padding(vertical = RumaSpace.md), horizontalArrangement = Arrangement.spacedBy(RumaSpace.xl)) {
                    NavMetric("Por calles", distanceLabel, Modifier.weight(1f))
                    NavMetric("Llegada estimada", arrivalClock, Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(RumaColors.Hair))
            }
            if (voice) Text("Mantén Ruma abierta para escuchar las indicaciones.", color = RumaColors.Text2, style = RumaType.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(RumaSpace.sm)) {
                state.phone?.let { phone ->
                    SecondaryButton(
                        "Llamar", icon = Icons.Filled.Phone, color = RumaColors.Rider,
                        onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } },
                    )
                }
                PrimaryButton("He llegado al punto", Modifier.weight(1f), icon = Icons.Filled.Place, color = RumaColors.Rider, enabled = !busy, onClick = onArrived)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextAction("No puedo ir · liberar este recojo", RumaColors.Error, "Liberar este recojo") { if (!busy) askRelease = true }
            }
            if (state.lat != null && state.lng != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextAction("Abrir en otra app", RumaColors.Text2) { openTurnByTurn(ctx, state.lat, state.lng, state.stopName) }
                }
            }
        }
    }
    }

    if (askRelease) {
        AlertDialog(
            onDismissRequest = { askRelease = false },
            containerColor = RumaColors.Surface,
            title = { Text("Liberar recojo", style = RumaType.cardTitle, color = RumaColors.Text) },
            text = {
                Text(
                    "Volverá al tablero para que otro Eco-Rider lo tome y avisaremos a ${state.stopName}.",
                    style = RumaType.body, color = RumaColors.Text2,
                )
            },
            confirmButton = {
                TextButton(modifier = Modifier.semantics { contentDescription = "Liberar recojo" }, onClick = { askRelease = false; onRelease() }) {
                    Text("Liberar", style = RumaType.button, color = RumaColors.Error)
                }
            },
            dismissButton = {
                TextButton(modifier = Modifier.semantics { contentDescription = "Seguir con el recojo" }, onClick = { askRelease = false }) { Text("Seguir", style = RumaType.button, color = RumaColors.Text2) }
            },
        )
    }
}

/**
 * Hands the leg to the phone's navigation app for spoken turn-by-turn — the one
 * thing an in-app OSM map can't do well. Google Maps when present, any map app
 * that understands geo: otherwise.
 */
private fun openTurnByTurn(context: Context, lat: Double, lng: Double, label: String) {
    val googleNav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng"))
    val anyMap = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})"))
    runCatching { context.startActivity(googleNav) }
        .recoverCatching { context.startActivity(anyMap) }
}

/** How often the navigation screen re-reads where the rider is. */
private const val HERE_REFRESH_MS = 5_000L

/** A notice in the sheet (PWA `.nav-notice`): what is wrong, and the one way to fix it. */
@Composable
private fun NavNotice(text: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(RumaRadius.badge - RumaSpace.xs / 2)).background(RumaColors.Bg)
            .border(1.dp, RumaColors.Hair, RoundedCornerShape(RumaRadius.badge - RumaSpace.xs / 2))
            .padding(horizontal = RumaSpace.lg, vertical = RumaSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = RumaColors.Text, style = RumaType.body, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun NavMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = RumaColors.Text2, style = RumaType.bodySmall)
        Text(value, color = RumaColors.Text, style = RumaType.rowTitle)
    }
}

@Composable
private fun MaterialChip(label: String) {
    StatusChip(label, RumaColors.Rider)
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun RiderNavigationPreview() {
    RumaTheme {
        RiderNavigationScreen(
            state = SampleNavigation,
        )
    }
}

private val SampleNavigation = RiderNavigationUiState(
    stopName = "Fam. Torres", address = "Calle Santa Rosa 215, Cartavio", distance = "1.2 km",
    eta = "5 min", instruction = "Frente al colegio, portón gris",
    materials = listOf("6 cartones", "4 vidrios"), phone = "+51987650003",
)
