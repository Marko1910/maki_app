package pe.ruma.app.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Anti-spoof liveness probe. While the user walks the camera around their waste we
 * sample the gyroscope: a real scan rotates the phone around physical objects, whereas
 * a screenshot or printed photo held in front of a stationary phone produces almost no
 * motion. The peak angular speed + sample count go to the server, which gates on them
 * before trusting a frame — a flat replay can't fake device motion.
 *
 * Runs for the whole scan session: [start] once, [snapshot] per frame, [stop] when the
 * camera closes.
 *
 * A sweep is remembered for [MOTION_MEMORY_MS]: people move the phone, then hold it
 * still to aim — and with a per-frame window every frame they aimed at was rejected
 * as "not moving", so the scanner never counted anything.
 *
 * Pairs with the detect-material Edge Function's liveness check and its VLM
 * screen-detection flag. ponytail: motion + VLM flag is the cheap, effective layer;
 * multi-frame parallax is the upgrade path if replay attacks get sophisticated.
 */
class LivenessSensors(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    // Budget phones (a good share of Ruma's users) ship without a gyroscope; without a
    // fallback the liveness gate would reject every frame and the scanner would be dead
    // on those devices. ponytail: accelerometer jitter is a coarser motion signal,
    // scaled into the same deg/s budget — replace if a real replay attack shows up.
    private val accel: Sensor? = if (gyro == null) sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null

    @Volatile private var maxDps = 0.0
    @Volatile private var samples = 0
    @Volatile private var windowStart = 0L
    // The strongest recent movement and when it happened (see MOTION_MEMORY_MS).
    @Volatile private var recentPeak = 0.0
    @Volatile private var recentPeakAt = 0L
    private var slow: FloatArray? = null

    fun start() {
        maxDps = 0.0; samples = 0; windowStart = System.currentTimeMillis()
        recentPeak = 0.0; recentPeakAt = 0L
        (gyro ?: accel)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    /** Motion for this frame: this window's peak, or a sweep from the last few seconds. */
    fun snapshot(): MotionDto {
        val now = System.currentTimeMillis()
        val remembered = if (now - recentPeakAt <= MOTION_MEMORY_MS) recentPeak else 0.0
        val dto = MotionDto(maxGyroDps = maxOf(maxDps, remembered), samples = samples, windowMs = now - windowStart)
        maxDps = 0.0; samples = 0; windowStart = now
        return dto
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val dps = if (e.sensor.type == Sensor.TYPE_GYROSCOPE) {
            // |angular velocity| in rad/s -> deg/s.
            Math.toDegrees(sqrt(e.values[0].toDouble() * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]))
        } else {
            // How far the acceleration vector strays from its slow average (gravity). Tilting
            // the phone moves gravity across the axes, so a gentle sweep around the waste
            // reads several m/s² while a phone at rest stays near 0. |a| alone missed it:
            // rotating doesn't change the magnitude, so on the gyro-less Galaxy A12 only a
            // shake passed and every photo was rejected (2026-11-17). x10 maps ~5° onto the gate.
            if (slow == null) slow = e.values.copyOf()
            val g = slow!!
            var d2 = 0.0
            for (i in 0..2) {
                g[i] += 0.1f * (e.values[i] - g[i])
                val d = (e.values[i] - g[i]).toDouble()
                d2 += d * d
            }
            sqrt(d2) * 10.0
        }
        if (dps > maxDps) maxDps = dps
        val now = System.currentTimeMillis()
        if (dps >= recentPeak || now - recentPeakAt > MOTION_MEMORY_MS) {
            recentPeak = dps
            recentPeakAt = now
        }
        samples++
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}

    private companion object {
        /** A sweep counts for this long. Calibration knob: longer is friendlier, shorter is stricter. */
        const val MOTION_MEMORY_MS = 8_000L
    }
}
