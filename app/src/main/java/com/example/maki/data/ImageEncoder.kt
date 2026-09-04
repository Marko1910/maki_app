package com.example.maki.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Turns a captured photo into what the detector and the UI both need: a downscaled,
 * **upright** bitmap. Small enough for the detect-material request — no raw
 * megapixels over the wire — and rotated the way the user held the phone, which
 * matters twice over now that the model returns bounding boxes: a sideways frame
 * gives worse detections and boxes that land on the wrong pixels.
 */
object ImageEncoder {

    /** Decoded, downscaled to [maxDim] on its long side, and EXIF-rotated upright. */
    fun decodeOriented(photo: File, maxDim: Int = 1024): Bitmap? {
        val bmp = BitmapFactory.decodeFile(photo.absolutePath) ?: return null
        val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        } else {
            bmp
        }
        return rotateToUpright(scaled, photo)
    }

    /** JPEG + base64, ready for the Edge Function payload. */
    fun toBase64(bitmap: Bitmap, quality: Int = 80): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun downscaledBase64(photo: File, maxDim: Int = 1024, quality: Int = 80): String =
        toBase64(decodeOriented(photo, maxDim) ?: error("not an image"), quality)

    private fun rotateToUpright(bitmap: Bitmap, photo: File): Bitmap {
        val degrees = runCatching {
            when (ExifInterface(photo.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
