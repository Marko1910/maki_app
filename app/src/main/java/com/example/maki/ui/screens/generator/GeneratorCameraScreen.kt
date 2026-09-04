package com.example.maki.ui.screens.generator

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
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import com.example.maki.data.TrainingDataStore
import com.example.maki.ui.components.PrimaryButton
import com.example.maki.ui.theme.MAKITheme
import com.example.maki.ui.theme.MakiFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private val DetectGreen = Color(0xFF34D399)
private val DetectAmber = Color(0xFFFBBF24)
private val Ink = Color(0xFF0B0F0D)

/**
 * What the scanner is doing. The phases exist so the shutter reads as a real
 * camera: the live feed stops, the frame the user took stays on screen while the
 * AI works, and the detections land on that same frozen frame.
 */
private sealed interface ScanPhase {
    /** Live camera feed, waiting for the shutter. */
    data object Live : ScanPhase
    /** Frame captured, request in flight — the feed is gone. */
    data class Analyzing(val frame: Bitmap?) : ScanPhase
    /** Verified: boxes drawn over the frozen frame before moving on. */
    data class Detected(val frame: Bitmap?, val items: List<DetectedItemDto>) : ScanPhase
}

// Immersive live-camera scanner. Capture is the ONLY input (no gallery): a photo
// of a screen or a saved image is exactly the spoof we block. Each capture goes to
// the detect-material Edge Function with a gyroscope motion summary; the server runs
// the anti-spoof gate + VLM, persists the detection and returns the verified result
// with a bounding box per material, which this screen draws over the frozen frame.
@Composable
fun GeneratorCameraScreen(
    onClose: () -> Unit = {},
    onDetected: (DetectResultDto) -> Unit = {},
    onInfo: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val scope = rememberCoroutineScope()

    var flashOn by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf<ScanPhase>(ScanPhase.Live) }
    var shutterFlash by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
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

    val live = phase is ScanPhase.Live

    Box(Modifier.fillMaxSize().background(Ink)) {
        when (val p = phase) {
            is ScanPhase.Live -> {
                if (hasPermission && !inspection) {
                    CameraPreview(
                        flashOn = flashOn,
                        onImageCaptureReady = { imageCapture = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (!hasPermission) {
                    PermissionPrompt(onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) })
                }
            }
            is ScanPhase.Analyzing -> FrozenFrame(p.frame, emptyList(), scanning = true)
            is ScanPhase.Detected -> FrozenFrame(p.frame, p.items, scanning = false)
        }

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
                Text("Escaneo verificado", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            if (live) {
                CircleScrim(Icons.Filled.Bolt, tint = if (flashOn) DetectAmber else Color.White) {
                    flashOn = !flashOn
                    onInfo(if (flashOn) "Flash activado" else "Flash desactivado")
                }
            } else {
                Box(Modifier.size(40.dp))   // keeps the title centred while scanning
            }
        }

        if (hasPermission && live) {
            Text(
                "Mueve el teléfono alrededor del residuo y captura",
                color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp, start = 32.dp, end = 32.dp),
            )

            // Capture (the only capture path — no gallery import by design).
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).size(76.dp).clip(CircleShape)
                    .background(Color(0x33FFFFFF)).border(4.dp, Color.White, CircleShape)
                    .clickable {
                        scope.launch {
                            shutterFlash = true
                            launch { delay(160); shutterFlash = false }
                            runScan(
                                imageCapture, context,
                                onInfo = onInfo,
                                onFrozen = { phase = ScanPhase.Analyzing(it) },
                                onDetected = onDetected,
                                onResult = { frame, items -> phase = ScanPhase.Detected(frame, items) },
                                onFailed = { phase = ScanPhase.Live },
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White)) }
        }

        // The shutter's white blink — the closing-camera cue, before the freeze.
        if (shutterFlash) Box(Modifier.fillMaxSize().background(Color.White))

        if (showCoach) {
            CameraCoachDialog(onUnderstood = {
                MakiPrefs.cameraCoachSeen = true
                showCoach = false
            })
        }
    }
}

/**
 * Captures a frame + gyroscope motion, freezes the frame on screen, sends both to
 * the server and routes the verified result.
 */
private suspend fun runScan(
    imageCapture: ImageCapture?,
    context: Context,
    onInfo: (String) -> Unit,
    onFrozen: (Bitmap?) -> Unit,
    onDetected: (DetectResultDto) -> Unit,
    onResult: (Bitmap?, List<DetectedItemDto>) -> Unit,
    onFailed: () -> Unit,
) = coroutineScope {
    var frame: Bitmap? = null
    try {
        // Sample device motion in parallel with the shutter — a still replay can't fake it.
        val motionDeferred = async { LivenessSensors.probe(context, 1200L) }
        val file = takePhoto(imageCapture, context)
        val motion = motionDeferred.await()
        if (file == null) { onInfo("La cámara aún no está lista."); onFailed(); return@coroutineScope }

        // Decode once: the same upright bitmap is what the user sees frozen and
        // what the model scores, so the boxes land on the pixels they describe.
        frame = withContext(Dispatchers.IO) { ImageEncoder.decodeOriented(file) }
        onFrozen(frame)

        val result = runCatching {
            val encoded = withContext(Dispatchers.IO) {
                frame?.let { ImageEncoder.toBase64(it) } ?: ImageEncoder.downscaledBase64(file)
            }
            MakiRepository.detectMaterial(listOf(encoded), motion)
        }.getOrElse { e ->
            // Surface the real failure (server error, timeout, ...) instead of a
            // generic "check your connection" that hides misconfigurations.
            onInfo(e.message?.takeIf { it.isNotBlank() } ?: "No se pudo verificar la detección. Revisa tu conexión.")
            file.delete()
            onFailed()
            return@coroutineScope
        }
        when {
            result.rejected != null || result.items.isEmpty() -> {
                onInfo(result.message ?: "No se pudo verificar el residuo. Intenta de nuevo.")
                file.delete()
                onFailed()
            }
            else -> {
                // Keep the verified frame as a local YOLO training sample (labelled by top material).
                runCatching { TrainingDataStore.save(context, file, result.items.first().code) }
                // Let the boxes land before leaving: the user should see what the AI saw.
                onResult(frame, result.items)
                delay(1100)
                onDetected(result)
            }
        }
    } catch (e: Exception) {
        onInfo(e.message?.takeIf { it.isNotBlank() } ?: "No se pudo completar el escaneo.")
        onFailed()
    }
}

/** Suspending wrapper around CameraX takePicture; null on any error. */
private suspend fun takePhoto(ic: ImageCapture?, context: Context): File? =
    suspendCancellableCoroutine { cont ->
        if (ic == null) { cont.resume(null); return@suspendCancellableCoroutine }
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        ic.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { cont.resume(file) }
                override fun onError(exc: ImageCaptureException) { cont.resume(null) }
            },
        )
    }

/**
 * The captured photo held still while the AI works, with a sweep line over it and
 * — once the result is back — a labelled box per detected material.
 */
@Composable
private fun FrozenFrame(frame: Bitmap?, items: List<DetectedItemDto>, scanning: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink)) {
        val w = maxWidth
        val h = maxHeight

        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "Residuo capturado",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(if (scanning) 0.55f else 1f),
            )
        }
        Box(Modifier.fillMaxSize().background(Color(if (scanning) 0xB30B0F0D else 0x330B0F0D)))

        if (scanning) {
            // Sweep line: the "we are reading this frame" cue, and the reason the
            // wait does not feel like the app froze.
            val sweep = rememberInfiniteTransition(label = "sweep")
            val y by sweep.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
                label = "sweepY",
            )
            Box(
                Modifier.fillMaxWidth().offset(y = h * y).size(width = w, height = 2.dp).background(DetectGreen.copy(alpha = 0.85f)),
            )
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(color = DetectGreen, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                Text("Analizando tu foto…", color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Shield, null, tint = DetectGreen, modifier = Modifier.size(14.dp))
                    Text(
                        "Cámara cerrada · comprobando que sea un residuo real",
                        color = Color(0xFFC9D2CE), fontFamily = MakiFont, fontWeight = FontWeight.Medium, fontSize = 12.sp,
                    )
                }
            }
        } else {
            items.forEach { item -> DetectionBox(item, w, h) }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xCC0B0F0D))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = DetectGreen, modifier = Modifier.size(16.dp))
                Text(
                    "${items.sumOf { it.quantity }} materiales detectados",
                    color = Color.White, fontFamily = MakiFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                )
            }
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
            .background(DetectGreen.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
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
            CoachStep(Icons.Outlined.PhotoCamera, "Apunta a tus residuos", "Colócalos sobre una superficie despejada y encuádralos.")
            CoachStep(Icons.Filled.Vibration, "Mueve un poco el teléfono", "Verificamos que sea material real y no una foto de pantalla.")
            CoachStep(Icons.Filled.AutoAwesome, "Toma la foto y espera", "La cámara se cierra y la IA analiza la imagen en unos segundos.")
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

@Composable
private fun CameraPreview(
    flashOn: Boolean,
    onImageCaptureReady: (ImageCapture) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(Unit) { onImageCaptureReady(imageCapture) }
    LaunchedEffect(flashOn, camera) { camera?.cameraControl?.enableTorch(flashOn) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = CameraXPreview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture,
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
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
