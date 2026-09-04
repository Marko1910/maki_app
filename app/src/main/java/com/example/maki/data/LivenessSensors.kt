package com.example.maki.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.delay
import kotlin.math.sqrt

/**
 * Anti-spoof liveness probe. While the user frames a scan we sample the gyroscope:
 * a real capture rotates the phone around the physical object, whereas a screenshot
 * or printed photo held in front of a stationary phone produces almost no motion.
 * The peak angular speed + sample count are sent to the server, which gates on them
 * before trusting the image — a flat replay can't fake device motion.
 *
 * Pairs with the detect-material Edge Function's liveness check and its VLM
 * screen-detection flag. ponytail: motion + VLM flag is the cheap, effective layer;
 * multi-frame parallax is the upgrade path if replay attacks get sophisticated.
 */
class LivenessSensors(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    @Volatile private var maxDps = 0.0
    @Volatile private var samples = 0

    private fun start() {
        maxDps = 0.0; samples = 0
        gyro?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun stop(): MotionDto {
        sm.unregisterListener(this)
        return MotionDto(maxGyroDps = maxDps, samples = samples, windowMs = 0)
    }

    override fun onSensorChanged(e: SensorEvent) {
        // |angular velocity| in rad/s -> deg/s.
        val mag = sqrt(e.values[0].toDouble() * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        val dps = Math.toDegrees(mag)
        if (dps > maxDps) maxDps = dps
        samples++
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}

    companion object {
        /** Samples motion for [windowMs] (runs alongside the photo capture). */
        suspend fun probe(context: Context, windowMs: Long = 1200L): MotionDto {
            val probe = LivenessSensors(context)
            probe.start()
            delay(windowMs)
            return probe.stop().copy(windowMs = windowMs)
        }
    }
}
