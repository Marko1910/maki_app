package pe.ruma.app.ui.screens.generator

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import pe.ruma.app.data.DetectedItemDto
import pe.ruma.app.ui.theme.RumaColors
import pe.ruma.app.ui.theme.RumaRadius
import pe.ruma.app.ui.theme.RumaSpace
import pe.ruma.app.ui.theme.RumaType

/** The upright bitmap sent to preview and only that photo's detections (not the folded tally). */
data class ScanPhoto(val bitmap: Bitmap, val items: List<DetectedItemDto>)

/** Fit the photo first, then use its exact bounds for both the image and the overlay. */
@Composable
internal fun PhotoBoxes(photo: ScanPhoto, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val ratio = photo.bitmap.width.toFloat() / photo.bitmap.height
        val width = minOf(maxWidth, maxHeight * ratio)
        val height = width / ratio
        Box(Modifier.size(width, height).clip(RoundedCornerShape(RumaRadius.card))) {
            Image(
                photo.bitmap.asImageBitmap(),
                contentDescription = "Tu foto" + photo.items.takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = ": ") { detectionLabel(it) }.orEmpty(),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            photo.items.forEach { item ->
                val bounds = item.box?.takeIf { it.size == 4 && it.all(Double::isFinite) } ?: return@forEach
                val x0 = bounds[0].toFloat().coerceIn(0f, 1f)
                val y0 = bounds[1].toFloat().coerceIn(0f, 1f)
                val x1 = bounds[2].toFloat().coerceIn(0f, 1f)
                val y1 = bounds[3].toFloat().coerceIn(0f, 1f)
                if (x1 <= x0 || y1 <= y0) return@forEach
                Box(
                    Modifier.absoluteOffset(x = width * x0, y = height * y0)
                        .size(width * (x1 - x0), height * (y1 - y0))
                        .border(RumaSpace.xs / 2, RumaColors.DetectOk, RoundedCornerShape(RumaRadius.chip)),
                )
                Text(
                    detectionLabel(item), color = RumaColors.OnDark, style = RumaType.chip,
                    modifier = Modifier.absoluteOffset(x = width * x0, y = height * y0)
                        .widthIn(max = width * (1f - x0))
                        .padding(RumaSpace.xs).clip(RoundedCornerShape(RumaRadius.chip))
                        .background(RumaColors.Gen).padding(horizontal = RumaSpace.sm, vertical = RumaSpace.xs),
                )
            }
        }
    }
}

private fun detectionLabel(item: DetectedItemDto): String =
    item.brand?.takeIf { it.isNotBlank() }?.let { "${item.code} · $it" } ?: item.name.ifBlank { item.code }

internal fun materialKind(code: String, fallback: String): String = when (code) {
    "PET" -> "Botella de plástico"
    "ALU" -> "Lata de aluminio"
    "VID" -> "Envase de vidrio"
    "CAR" -> "Cartón"
    "PIL" -> "Pilas"
    else -> fallback
}
