package pe.ruma.app.ui.screens.generator

import android.util.Size
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import pe.ruma.app.data.DetectResultDto
import pe.ruma.app.data.DetectedItemDto
import pe.ruma.app.data.ImageEncoder
import pe.ruma.app.data.LivenessSensors
import pe.ruma.app.data.RumaPrefs
import pe.ruma.app.data.RumaRepository
import pe.ruma.app.data.ScanObsItemDto
import pe.ruma.app.data.ScanObservationDto
import pe.ruma.app.data.TrainingDataStore
import pe.ruma.app.ui.components.PrimaryButton
import pe.ruma.app.ui.components.SecondaryButton
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaType
import pe.ruma.app.ui.theme.RumaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors


/** detect-material's MAX_GROUPS: one group per photo, so this is the most photos a scan holds. */
private const val MAX_PHOTOS = 10

/** A photo is read once and carefully: more pixels than a live frame, so labels are legible. */
private const val PHOTO_MAX_DIM = 1600

/** How many times a photo is re-sent while every provider is busy before giving up. */
private const val BUSY_RETRIES = 3

private enum class Phase { Aim, Analyzing, Result }

private const val NEXT_PHOTO_HINT = "Toma otra foto: las marcas ya contadas no se repiten, solo suma lo nuevo"

/**
 * Photo scan: the user gathers their waste, takes ONE photo, waits on a "la IA está
 * analizando" screen and gets the count, the brands and the estimated points. More waste
 * goes in another photo — each photo is its own signed group and photos add up.
 *
 * It replaced the live scan (2026-11-01). Counting a walk-around needs MAX per material
 * across frames, which on the phone turned into miscounts: the same bottles re-framed in
 * a new group were booked twice, and every frame was a metered vision call (~24/min,
 * which is what capped the free tier at ~3 people scanning at once). One photo is one call.
 *
 * The server side is unchanged: each photo is a `preview` (liveness gate + VLM, signed
 * count) and "Terminar" is the `commit` that books them — the client can't inflate them.
 */
@Composable
fun GeneratorCameraScreen(
    onClose: () -> Unit = {},
    onDetected: (DetectResultDto, ScanPhoto?) -> Unit = { _, _ -> },
    onInfo: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val scope = rememberCoroutineScope()

    val sessionId = remember { UUID.randomUUID().toString() }
    val observations = remember { mutableStateListOf<ScanObservationDto>() }
    // The photo on screen and the earlier ones, folded by the same rule `commit` applies.
    val tally = remember { mutableStateListOf<DetectedItemDto>() }
    val closedPhotos = remember { mutableStateListOf<List<DetectedItemDto>>() }
    var group by remember { mutableIntStateOf(0) }
    val counted = pileTally(closedPhotos + listOf(tally.toList()))

    var phase by remember { mutableStateOf(Phase.Aim) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    // The last booked photo, kept as the local training sample when the scan closes.
    var sample by remember { mutableStateOf<Bitmap?>(null) }
    var acceptedPhoto by remember { mutableStateOf<ScanPhoto?>(null) }
    var previousPhoto by remember { mutableStateOf<ScanPhoto?>(null) }
    var boxes by remember { mutableStateOf<List<DetectedItemDto>>(emptyList()) }
    var flashOn by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("Junta tus residuos con las etiquetas a la vista y toma una foto") }
    var taker by remember { mutableStateOf<PhotoTaker?>(null) }
    // First scan ever: explain the camera before the user is staring at a viewfinder.
    var showCoach by remember { mutableStateOf(!inspection && !RumaPrefs.cameraCoachSeen) }

    var hasPermission by remember {
        mutableStateOf(
            inspection ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) onInfo("Necesitas permitir la cámara para detectar residuos.")
    }
    // Ask for the camera only after the explainer is dismissed — two dialogs at
    // once on a first launch is the fastest way to get a permission denied.
    LaunchedEffect(showCoach) {
        if (!hasPermission && !inspection && !showCoach) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Motion sensors run for the whole screen: the liveness gate wants a sweep in the last
    // few seconds, and the user makes it while lifting the phone to aim.
    val sensors = remember { LivenessSensors(context) }
    DisposableEffect(inspection) {
        if (!inspection) sensors.start()
        onDispose { sensors.stop() }
    }

    /** Drops the photo on screen (or the one that failed) and goes back to aiming, same group. */
    fun retake() {
        observations.removeAll { it.group == group }
        tally.clear()
        boxes = emptyList()
        photo = null
        acceptedPhoto = previousPhoto
        sample = previousPhoto?.bitmap
        phase = Phase.Aim
        hint = if (group == 0) "Junta tus residuos con las etiquetas a la vista y toma una foto"
        else NEXT_PHOTO_HINT
    }

    fun shoot() {
        val source = taker ?: return
        // Right away: a real photo takes a moment to focus, and the tap must answer now.
        phase = Phase.Analyzing
        scope.launch {
            val bitmap = source.take() ?: run {
                hint = "No se pudo tomar la foto. Intenta otra vez."
                phase = Phase.Aim
                return@launch
            }
            photo = bitmap
            val photoGroup = group
            val encoded = withContext(Dispatchers.IO) {
                ImageEncoder.toBase64(bitmap, quality = 85)
            }
            // The free vision tiers are busy at random: wait them out a few times on the
            // same photo instead of making the user shoot again.
            var result: DetectResultDto? = null
            var failure: String? = null
            for (attempt in 0..BUSY_RETRIES) {
                result = runCatching { RumaRepository.previewFrame(sessionId, encoded, sensors.snapshot(), photoGroup) }
                    .getOrElse { e -> failure = e.message?.takeIf { it.isNotBlank() } ?: "Sin conexión con la IA."; null }
                if (result?.rejected != "busy" || attempt == BUSY_RETRIES) break
                delay(result.retry_after_ms ?: 3000L)
            }
            val r = result
            when {
                r == null -> hint = failure ?: "Sin conexión con la IA."
                r.rejected != null -> hint = r.message ?: "No pudimos leer la foto. Intenta otra vez."
                else -> {
                    observations += ScanObservationDto(
                        items = r.items.map { ScanObsItemDto(it.code, it.quantity, it.quality, it.brand) },
                        token = r.token.orEmpty(),
                        group = photoGroup,
                    )
                    boxes = r.items
                    sample = bitmap
                    acceptedPhoto = ScanPhoto(bitmap, r.items)
                    tally += r.items
                    val shown = r.items.sumOf { it.quantity }
                    val added = pileTally(closedPhotos + listOf(r.items)).sumOf { it.quantity } -
                        pileTally(closedPhotos).sumOf { it.quantity }
                    hint = when {
                        added == shown -> "Encontramos $shown en esta foto · ≈ ${r.items.sumOf { it.points }} pts estimados"
                        added > 0 -> "Encontramos $shown: ${shown - added} ya estaban contadas y suma $added nueva${if (added > 1) "s" else ""}"
                        else -> "Ya estaban contadas. Si trajiste otra botella igual, súmala con + al terminar."
                    }
                }
            }
            phase = Phase.Result
        }
    }

    Box(Modifier.fillMaxSize().background(RumaColors.Ink)) {
        if (hasPermission && !inspection) {
            // Stays mounted under the frozen photo, so "Otra foto" is instant.
            CameraPreview(
                flashOn = flashOn,
                onReady = { taker = it },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!hasPermission) {
            PermissionPrompt(onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }

        // The photo being read, frozen, with what the AI found drawn over it.
        photo?.let { bmp ->
            PhotoBoxes(ScanPhoto(bmp, boxes), Modifier.fillMaxSize().background(RumaColors.Ink))
        }

        if (phase == Phase.Analyzing) {
            ScanSweep()
            AnalyzingOverlay()
        }

        // Top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleScrim(Icons.Filled.Close, onClick = onClose)
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(RumaColors.Scrim).padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Shield, null, tint = RumaColors.DetectOk, modifier = Modifier.size(15.dp))
                Text(
                    if (group > 0) "Foto ${group + 1}" else "Escaneo verificado",
                    color = RumaColors.OnDark, style = RumaType.chip,
                )
            }
            CircleScrim(Icons.Filled.Bolt, tint = if (flashOn) RumaColors.DetectWarn else RumaColors.OnDark) {
                flashOn = !flashOn
                onInfo(if (flashOn) "Flash activado" else "Flash desactivado")
            }
        }

        if (hasPermission && phase != Phase.Analyzing) {
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // On the frozen photo the hint already gives the count and the chip sat on top of the boxes;
                // it earns its place while aiming the next photo, as the running pile.
                if (counted.isNotEmpty() && phase != Phase.Result) TallyStrip(counted, group)
                Text(
                    hint,
                    color = RumaColors.OnDark, style = RumaType.body,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(RumaColors.Scrim)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                when (phase) {
                    Phase.Aim -> {
                        ShutterButton(enabled = taker != null && !closing, onClick = ::shoot)
                        // Photos already booked can be closed from here too.
                        if (counted.isNotEmpty()) {
                            FinishButton(counted, closing, Modifier.fillMaxWidth()) {
                                finish(scope, context, sample, counted, sessionId, observations, onInfo, { onDetected(it, acceptedPhoto) }) { closing = it }
                            }
                        }
                    }
                    Phase.Result -> {
                        // Text-only: retaking is the escape hatch, not the main path.
                        Text(
                            "Repetir esta foto",
                            color = RumaColors.OnDark, style = RumaType.label,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !closing, onClickLabel = "Repetir esta foto") { retake() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (tally.isNotEmpty() && group < MAX_PHOTOS - 1) {
                                SecondaryButton(
                                    label = "Otra foto",
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Add,
                                    color = RumaColors.DetectOk,
                                    enabled = !closing,
                                ) {
                                    closedPhotos += tally.toList()
                                    previousPhoto = acceptedPhoto
                                    tally.clear()
                                    group += 1
                                    boxes = emptyList()
                                    photo = null
                                    phase = Phase.Aim
                                    hint = NEXT_PHOTO_HINT
                                }
                            }
                            if (counted.isNotEmpty()) {
                                FinishButton(counted, closing, Modifier.weight(1f)) {
                                    finish(scope, context, sample, counted, sessionId, observations, onInfo, { onDetected(it, acceptedPhoto) }) { closing = it }
                                }
                            } else {
                                PrimaryButton("Tomar de nuevo", Modifier.weight(1f), icon = Icons.Outlined.PhotoCamera, onClick = ::retake)
                            }
                        }
                    }
                    Phase.Analyzing -> Unit
                }
            }
        }

        if (closing) ClosingOverlay()

        if (showCoach) {
            CameraCoachDialog(onUnderstood = {
                RumaPrefs.cameraCoachSeen = true
                showCoach = false
            })
        }
    }
}

@Composable
private fun FinishButton(counted: List<DetectedItemDto>, closing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    PrimaryButton(
        label = "Terminar · ${counted.sumOf { it.quantity }}",
        modifier = modifier,
        enabled = !closing,
        onClick = onClick,
    )
}

/** "Terminar": books every photo through `commit` and hands the persisted result on. */
private fun finish(
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    sample: Bitmap?,
    counted: List<DetectedItemDto>,
    sessionId: String,
    observations: List<ScanObservationDto>,
    onInfo: (String) -> Unit,
    onDetected: (DetectResultDto) -> Unit,
    setClosing: (Boolean) -> Unit,
) {
    setClosing(true)
    scope.launch {
        // Keep one verified photo as a local YOLO training sample.
        sample?.let { bmp ->
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                    val jpg = ImageEncoder.writeJpeg(bmp, File(dir, "scan_${System.currentTimeMillis()}.jpg"))
                    TrainingDataStore.save(context, jpg, counted.first().code)
                }
            }
        }
        val result = runCatching { RumaRepository.commitScan(sessionId, observations.toList()) }
            .getOrElse { e ->
                onInfo(e.message?.takeIf { it.isNotBlank() } ?: "No se pudo cerrar el escaneo.")
                setClosing(false)
                return@launch
            }
        if (result.rejected != null || result.items.isEmpty()) {
            onInfo(result.message ?: "No se pudo verificar el escaneo. Intenta de nuevo.")
            setClosing(false)
        } else {
            onDetected(result)
        }
    }
}

/** The round shutter: the one action while aiming. */
@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    // A tick under the thumb: the photo was taken, even before the analysis answers.
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        Modifier.size(76.dp).clip(CircleShape).border(4.dp, RumaColors.OnDark, CircleShape).padding(7.dp)
            .clip(CircleShape).background(if (enabled) RumaColors.OnDark else RumaColors.Scrim)
            .clickable(enabled = enabled, onClickLabel = "Tomar foto", role = Role.Button, onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            }),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.PhotoCamera, null, tint = RumaColors.Ink, modifier = Modifier.size(28.dp)) }
}

/** "Your material is being analysed": the wait is the product, so it says what is happening. */
@Composable
private fun AnalyzingOverlay() {
    Box(Modifier.fillMaxSize().background(RumaColors.Scrim), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp).clip(RoundedCornerShape(22.dp)).background(RumaColors.ScrimStrong).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(color = RumaColors.DetectOk, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            Text(
                "Tu material está siendo analizado por IA",
                color = RumaColors.OnDark, style = RumaType.sectionHeading, textAlign = TextAlign.Center,
            )
            Text(
                "Contamos cada envase y leemos sus marcas. Espera unos segundos…",
                color = RumaColors.OnDark, style = RumaType.body, textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * What the photos add up to, per material: the same fold as `foldPhotos` in `commit`, so the
 * counter on screen says what the scan books. A photo sharing no brand with the earlier ones
 * is new waste and adds up; one repeating a counted brand is the same pile framed again and
 * adds only what grew (3, then those 3 plus a Gatorade: 4, not 7). A label that became
 * legible may be a bottle already counted as unreadable.
 */
internal fun pileTally(photos: List<List<DetectedItemDto>>): List<DetectedItemDto> =
    photos.flatten().map { it.code }.distinct().map { code ->
        val brands = linkedMapOf<String, Int>()      // brandKey -> units
        val names = linkedMapOf<String, String>()    // brandKey -> label as read
        var unnamed = 0
        var quantity = 0
        var points = 0
        var quality = 0.0
        for (photo in photos) {
            val rows = photo.filter { it.code == code }
            if (rows.isEmpty()) continue
            quality = maxOf(quality, rows.maxOf { it.quality })
            val shown = rows.sumOf { it.quantity }
            val named = rows.filter { brandKey(it.brand) != null }.groupBy { brandKey(it.brand)!! }
            val samePile = named.keys.any { it in brands }
            // A counted brand missing from this photo may be an unreadable one in it.
            val covered = brands.entries.sumOf { (key, units) ->
                (units - (named[key]?.sumOf { it.quantity } ?: 0)).coerceAtLeast(0)
            }
            var newlyNamed = 0
            for ((key, same) in named) {
                val units = same.sumOf { it.quantity }
                val extra = if (samePile) (units - (brands[key] ?: 0)).coerceAtLeast(0) else units
                brands[key] = (brands[key] ?: 0) + extra
                names.getOrPut(key) { same.first().brand.orEmpty() }
                newlyNamed += extra
            }
            val shownUnnamed = shown - named.values.sumOf { same -> same.sumOf { it.quantity } }
            val grew = if (samePile) {
                (newlyNamed + (shownUnnamed - covered).coerceAtLeast(0) - unnamed).coerceAtLeast(0)
            } else {
                newlyNamed + shownUnnamed
            }
            val add = minOf(grew, shown)
            unnamed = (unnamed + add - newlyNamed).coerceAtLeast(0)
            quantity += add
            if (shown > 0) points += rows.sumOf { it.points } * add / shown
        }
        photos.flatten().first { it.code == code }.copy(
            quantity = quantity, points = points, quality = quality, brand = null, box = null,
            brands = brands.entries.associate { names.getValue(it.key) to it.value },
        )
    }

/** "Agua Fiel", "Aguafiel" and "AGUA-FIEL" are one label read three ways. */
internal fun brandKey(brand: String?): String? =
    brand?.lowercase(java.util.Locale.ROOT)?.filter { it.isLetterOrDigit() }?.ifEmpty { null }

/** The materials counted so far, one chip per material, plus the group in progress. */
@Composable
private fun TallyStrip(tally: List<DetectedItemDto>, group: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (group > 0) {
            Text(
                "Grupo ${group + 1}",
                color = RumaColors.OnDark, style = RumaType.chip,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(RumaColors.Scrim)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        tally.forEach { item ->
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(RumaColors.ScrimStrong)
                    .border(1.dp, RumaColors.DetectOk.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.name.ifBlank { item.code },
                    color = RumaColors.OnDark, style = RumaType.chip,
                )
                Text(
                    "x${item.quantity}",
                    color = RumaColors.DetectOk, style = RumaType.chip,
                )
            }
        }
    }
}

/** The "we are reading this frame" sweep, over the live feed while a frame is in flight. */
@Composable
private fun ScanSweep() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sweep = rememberInfiniteTransition(label = "sweep")
        val y by sweep.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
            label = "sweepY",
        )
        Box(
            Modifier.fillMaxWidth().offset(y = maxHeight * y).size(width = maxWidth, height = 2.dp)
                .background(RumaColors.DetectOk.copy(alpha = 0.7f)),
        )
    }
}

@Composable
private fun ClosingOverlay() {
    Box(Modifier.fillMaxSize().background(RumaColors.ScrimStrong), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = RumaColors.DetectOk, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            Text("Cerrando el escaneo…", color = RumaColors.OnDark, style = RumaType.sectionHeading)
        }
    }
}

/** Shown once, before the first scan: what the camera does and what it needs. */
@Composable
private fun CameraCoachDialog(onUnderstood: () -> Unit) {
    Box(Modifier.fillMaxSize().background(RumaColors.ScrimStrong), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(28.dp).clip(RoundedCornerShape(22.dp)).background(RumaColors.OnDark).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Cómo funciona la cámara",
                color = RumaColors.Ink, style = RumaType.metricMedium,
            )
            CoachStep(Icons.Outlined.PhotoCamera, "Junta tus residuos", "Ponlos cerca de la cámara, con buena luz y con las etiquetas hacia ti, sin taparse entre ellos.")
            CoachStep(Icons.Filled.Vibration, "Toma la foto", "Mueve un poco el teléfono al apuntar y mantenlo quieto al disparar: una foto movida pierde botellas.")
            CoachStep(Icons.Filled.AutoAwesome, "La IA la analiza", "En unos segundos ves cuántos hay de cada marca, y puedes corregir el número. ¿Tienes más? Toma otra foto.")
            Text(
                "Tus puntos se acreditan cuando el acopio pesa tu material, después del recojo.",
                color = RumaColors.Text2, style = RumaType.caption,
            )
            PrimaryButton("Entendido", Modifier.fillMaxWidth(), onClick = onUnderstood)
        }
    }
}

@Composable
private fun CoachStep(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(RumaColors.GenTint),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = RumaColors.Gen, modifier = Modifier.size(19.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = RumaColors.Ink, style = RumaType.cardTitle)
            Text(body, color = RumaColors.Text2, style = RumaType.caption)
        }
    }
}

/**
 * Takes a real photo: focused and metered. It used to copy a frame off the analysis stream,
 * which CameraX serves at about 480x864 with no focus wait, and at that size the labels the
 * model has to read are a few blurred pixels. Asks for 1080p, not the full sensor: decoding
 * 12 MP into a bitmap can run a budget phone out of memory, and only [PHOTO_MAX_DIM] is sent.
 */
private class PhotoTaker(private val capture: ImageCapture, private val executor: Executor) {
    /** The upright photo, or null if the camera couldn't take it in time. */
    suspend fun take(timeoutMs: Long = 8000): Bitmap? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { result ->
            val callback = object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = runCatching {
                        ImageEncoder.scaled(
                            ImageEncoder.upright(image.toBitmap(), image.imageInfo.rotationDegrees),
                            PHOTO_MAX_DIM,
                        )
                    }.getOrNull()
                    image.close()
                    result.resume(bitmap)
                }

                override fun onError(exception: ImageCaptureException) = result.resume(null)
            }
            runCatching { capture.takePicture(executor, callback) }.onFailure { result.resume(null) }
        }
    }
}

@Composable
private fun CameraPreview(
    flashOn: Boolean,
    onReady: (PhotoTaker) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(flashOn, camera) { camera?.cameraControl?.enableTorch(flashOn) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = CameraXPreview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder()
                    // 16:9 to match the preview, so the model's boxes land where the user sees the object.
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                            )
                            .build()
                    )
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture,
                )
                onReady(PhotoTaker(capture, executor))
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        onRelease = { executor.shutdown() },
    )
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Activa la cámara para detectar tus residuos con IA",
            color = RumaColors.OnDark, style = RumaType.sectionHeading,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.padding(top = 16.dp).clip(RoundedCornerShape(14.dp)).background(RumaColors.DetectOk)
                .clickable(onClick = onGrant).padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Permitir cámara", color = RumaColors.GenDark, style = RumaType.cardTitle)
        }
    }
}

@Composable
private fun CircleScrim(icon: ImageVector, tint: Color = RumaColors.OnDark, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(RumaColors.Scrim).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp)) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CameraPreviewPreview() {
    RumaTheme { GeneratorCameraScreen() }
}
