package com.example.maki.ui.screens.generator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.maki.data.DetectResultDto
import com.example.maki.data.DetectedItemDto
import com.example.maki.data.ImageEncoder
import com.example.maki.data.LivenessSensors
import com.example.maki.data.MakiPrefs
import com.example.maki.data.MakiRepository
import com.example.maki.data.ScanObsItemDto
import com.example.maki.data.ScanObservationDto
import com.example.maki.data.TrainingDataStore
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiFont
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

private val DetectGreen = Color(0xFF34D399)
private val DetectAmber = Color(0xFFFBBF24)
private val Ink = Color(0xFF0B0F0D)

/** Pause between analysed frames — slow enough to stay inside the model's free-tier rate. */
private const val FRAME_INTERVAL_MS = 1200L

/**
 * Live counting scanner. The camera stays open: every ~2s a frame goes to the
 * detect-material Edge Function, which runs the anti-spoof gate + VLM and returns what
 * it counted in that frame, signed. The user walks around their waste watching the tally
 * grow and presses "Terminar" — only then does the server persist the detection, from
 * its own signed counts (the client can't inflate them).
 *
 * Counting is MAX per material across frames, never a sum: the same bottle appears in
 * many frames, so summing would pay for it once per frame.
 */
@Composable
fun GeneratorCameraScreen(
    onClose: () -> Unit = {},
    onDetected: (DetectResultDto) -> Unit = {},
    onInfo: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val scope = rememberCoroutineScope()

    val sessionId = remember { UUID.randomUUID().toString() }
    val observations = remember { mutableStateListOf<ScanObservationDto>() }
    // Running tally per material code, and the boxes from the frame just analysed.
    val tally = remember { mutableStateListOf<DetectedItemDto>() }
    var boxes by remember { mutableStateOf<List<DetectedItemDto>>(emptyList()) }
    var lastFrame by remember { mutableStateOf<Bitmap?>(null) }

    var flashOn by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("Recorre tus residuos: la IA los va contando") }
    var grabber by remember { mutableStateOf<FrameGrabber?>(null) }
    // First scan ever: explain the camera before the user is staring at a viewfinder.
    var showCoach by remember { mutableStateOf(!inspection && !MakiPrefs.cameraCoachSeen) }

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

    // The scan loop: one frame in flight at a time, for as long as the screen is open.
    LaunchedEffect(hasPermission, grabber, closing, showCoach) {
        val source = grabber
        if (!hasPermission || source == null || closing || showCoach || inspection) return@LaunchedEffect
        val sensors = LivenessSensors(context).apply { start() }
        try {
            while (isActive) {
                val bitmap = source.grab()
                if (bitmap == null) { delay(FRAME_INTERVAL_MS); continue }
                lastFrame = bitmap
                analyzing = true
                val result = runCatching {
                    val encoded = withContext(Dispatchers.IO) { ImageEncoder.toBase64(bitmap, quality = 70) }
                    MakiRepository.previewFrame(sessionId, encoded, sensors.snapshot())
                }.getOrElse { e ->
                    analyzing = false
                    hint = e.message?.takeIf { it.isNotBlank() } ?: "Sin conexión con el detector."
                    delay(2000)
                    null
                }
                analyzing = false
                if (result != null) {
                    hint = when {
                        result.rejected != null -> result.message ?: "Sigue apuntando a tus residuos."
                        else -> {
                            observations += ScanObservationDto(
                                items = result.items.map { ScanObsItemDto(it.code, it.quantity, it.quality) },
                                token = result.token.orEmpty(),
                            )
                            boxes = result.items
                            mergeIntoTally(tally, result.items)
                            "${tally.sumOf { it.quantity }} materiales contados"
                        }
                    }
                    if (result.rejected != null) boxes = emptyList()
                }
                delay(FRAME_INTERVAL_MS)
            }
        } finally {
            sensors.stop()
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        if (hasPermission && !inspection) {
            CameraPreview(
                flashOn = flashOn,
                onGrabberReady = { grabber = it },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!hasPermission) {
            PermissionPrompt(onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }

        // Boxes from the last analysed frame, over the live feed.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            boxes.forEach { DetectionBox(it, w, h) }
        }

        if (analyzing) ScanSweep()

        // Top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleScrim(Icons.Filled.Close, onClick = onClose)
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x59000000)).padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Shield, null, tint = DetectGreen, modifier = Modifier.size(15.dp))
                Text(
                    if (analyzing) "Contando…" else "Escaneo verificado",
                    color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                )
            }
            CircleScrim(Icons.Filled.Bolt, tint = if (flashOn) DetectAmber else Color.White) {
                flashOn = !flashOn
                onInfo(if (flashOn) "Flash activado" else "Flash desactivado")
            }
        }

        if (hasPermission) {
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (tally.isNotEmpty()) TallyStrip(tally)
                Text(
                    hint,
                    color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                PrimaryButton(
                    label = if (tally.isEmpty()) "Terminar" else "Terminar · ${tally.sumOf { it.quantity }} materiales",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = tally.isNotEmpty() && !closing,
                ) {
                    closing = true
                    scope.launch {
                        // Keep one verified frame as a local YOLO training sample.
                        lastFrame?.let { bmp ->
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                                    val jpg = ImageEncoder.writeJpeg(bmp, File(dir, "scan_${System.currentTimeMillis()}.jpg"))
                                    TrainingDataStore.save(context, jpg, tally.first().code)
                                }
                            }
                        }
                        val result = runCatching { MakiRepository.commitScan(sessionId, observations.toList()) }
                            .getOrElse { e ->
                                onInfo(e.message?.takeIf { it.isNotBlank() } ?: "No se pudo cerrar el escaneo.")
                                closing = false
                                return@launch
                            }
                        if (result.rejected != null || result.items.isEmpty()) {
                            onInfo(result.message ?: "No se pudo verificar el escaneo. Intenta de nuevo.")
                            closing = false
                        } else {
                            onDetected(result)
                        }
                    }
                }
            }
        }

        if (closing) ClosingOverlay()

        if (showCoach) {
            CameraCoachDialog(onUnderstood = {
                MakiPrefs.cameraCoachSeen = true
                showCoach = false
            })
        }
    }
}

/**
 * Folds a frame's items into the running tally: best count seen per material, never a
 * sum — the same objects reappear frame after frame.
 */
private fun mergeIntoTally(tally: SnapshotStateList<DetectedItemDto>, items: List<DetectedItemDto>) {
    items.forEach { item ->
        val index = tally.indexOfFirst { it.code == item.code }
        if (index < 0) {
            tally += item
        } else if (item.quantity > tally[index].quantity) {
            tally[index] = item
        }
    }
}

/** The materials counted so far, one chip per material. */
@Composable
private fun TallyStrip(tally: List<DetectedItemDto>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        tally.forEach { item ->
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xCC0B0F0D))
                    .border(1.dp, DetectGreen.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.name.ifBlank { item.code },
                    color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                )
                Text(
                    "x${item.quantity}",
                    color = DetectGreen, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
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
                .background(DetectGreen.copy(alpha = 0.7f)),
        )
    }
}

@Composable
private fun ClosingOverlay() {
    Box(Modifier.fillMaxSize().background(Color(0xCC0B0F0D)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = DetectGreen, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            Text("Cerrando el escaneo…", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        }
    }
}

/** One detection: the model's box plus a "PET · 92%" tag anchored to its top edge. */
@Composable
private fun DetectionBox(item: DetectedItemDto, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    val box = item.box?.takeIf { it.size >= 4 } ?: return
    val x0 = box[0].toFloat().coerceIn(0f, 1f)
    val y0 = box[1].toFloat().coerceIn(0f, 1f)
    val x1 = box[2].toFloat().coerceIn(0f, 1f)
    val y1 = box[3].toFloat().coerceIn(0f, 1f)

    Box(
        Modifier
            .offset(x = width * x0, y = height * y0)
            .size(width = width * (x1 - x0), height = height * (y1 - y0))
            .border(2.5.dp, DetectGreen, RoundedCornerShape(10.dp))
            .background(DetectGreen.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .alpha(0.9f),
    ) {
        Row(
            Modifier.offset(y = (-26).dp).clip(RoundedCornerShape(8.dp)).background(DetectGreen)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${item.name.ifBlank { item.code }} x${item.quantity}",
                color = Ink, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp,
            )
            Text(
                "${(item.confidence * 100).toInt()}%",
                color = Ink.copy(alpha = 0.7f), fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 11.sp,
            )
        }
    }
}

/** Shown once, before the first scan: what the camera does and what it needs. */
@Composable
private fun CameraCoachDialog(onUnderstood: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(28.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Cómo funciona la cámara",
                color = Ink, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
            )
            CoachStep(Icons.Outlined.PhotoCamera, "Apunta a tus residuos", "La cámara queda abierta y la IA va contando lo que ve.")
            CoachStep(Icons.Filled.Vibration, "Recorre el material", "Muévete alrededor: así verificamos que sea real y no una foto.")
            CoachStep(Icons.Filled.AutoAwesome, "Pulsa Terminar", "Cerramos el conteo y te mostramos cuántos PET, latas o vidrios hay.")
            Text(
                "Los puntos se acreditan cuando el Eco-Rider recoge el material.",
                color = Color(0xFF6B7280), fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp,
            )
            PrimaryButton("Entendido", Modifier.fillMaxWidth(), onClick = onUnderstood)
        }
    }
}

@Composable
private fun CoachStep(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x140F6E56)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color(0xFF0F6E56), modifier = Modifier.size(19.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Ink, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(body, color = Color(0xFF6B7280), fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

/**
 * Hands the scan loop one frame at a time, on demand. ImageAnalysis (not ImageCapture)
 * is what keeps the scan continuous: no shutter sound, no file per frame, and the feed
 * never stops for the user.
 */
private class FrameGrabber : ImageAnalysis.Analyzer {
    private val pending = AtomicReference<CompletableDeferred<Bitmap>?>(null)

    override fun analyze(image: ImageProxy) {
        val request = pending.getAndSet(null)
        if (request != null) {
            runCatching { ImageEncoder.upright(image.toBitmap(), image.imageInfo.rotationDegrees) }
                .onSuccess { request.complete(it) }
                .onFailure { request.completeExceptionally(it) }
        }
        image.close()
    }

    /** Next frame off the camera, or null if none arrives (feed not ready yet). */
    suspend fun grab(timeoutMs: Long = 4000): Bitmap? {
        val request = CompletableDeferred<Bitmap>()
        pending.set(request)
        return withTimeoutOrNull(timeoutMs) { runCatching { request.await() }.getOrNull() }
    }
}

@Composable
private fun CameraPreview(
    flashOn: Boolean,
    onGrabberReady: (FrameGrabber) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val grabber = remember { FrameGrabber() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(Unit) { onGrabberReady(grabber) }
    LaunchedEffect(flashOn, camera) { camera?.cameraControl?.enableTorch(flashOn) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = CameraXPreview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    // 16:9 to match the preview, so the model's boxes land where the user sees the object.
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(executor, grabber) }
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                )
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
            color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.padding(top = 16.dp).clip(RoundedCornerShape(14.dp)).background(DetectGreen)
                .clickable(onClick = onGrant).padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Permitir cámara", color = Color(0xFF0B2A1E), fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun CircleScrim(icon: ImageVector, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(0x59000000)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp)) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CameraPreviewPreview() {
    MAKITheme { GeneratorCameraScreen() }
}
