package pe.ruma.app.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the phone points, in degrees clockwise from north (0..360), for the "you are here" arrow on a map;
 * null on a phone without a rotation sensor (the arrow then shows as a plain dot). Smoothed on the unit
 * circle (so 359° → 1° doesn't swing through 180°) and only republished on a 3° change: a compass that
 * redraws the map 50 times a second drains the battery for nothing.
 */
@Composable
fun rememberDeviceHeading(): Float? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        // The fused vector needs a gyroscope; budget phones without one often still have a compass, which the
        // geomagnetic vector uses (accelerometer + magnetometer). No compass at all (e.g. Galaxy A12): plain dot.
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (sm == null || sensor == null) return@DisposableEffect onDispose { }
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        var sx = 0.0
        var sy = 0.0
        var started = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, e.values)
                SensorManager.getOrientation(rotation, orientation)
                val a = orientation[0].toDouble()   // azimuth, radians, clockwise from north
                if (!started) { sx = sin(a); sy = cos(a); started = true } else {
                    sx += SMOOTHING * (sin(a) - sx); sy += SMOOTHING * (cos(a) - sy)
                }
                val deg = ((Math.toDegrees(atan2(sx, sy)) + 360) % 360).toFloat()
                val last = heading
                if (last == null || angleDiff(deg, last) >= MIN_CHANGE_DEG) heading = deg
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return heading
}

private const val SMOOTHING = 0.15
private const val MIN_CHANGE_DEG = 3f

private fun angleDiff(a: Float, b: Float): Float = abs(((a - b + 540) % 360) - 180)
