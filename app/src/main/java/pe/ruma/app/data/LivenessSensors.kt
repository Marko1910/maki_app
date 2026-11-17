package pe.ruma.app.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
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
    private var lastAccelMag = Double.NaN

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
            // Change in |acceleration| between samples: ~0 lying still, a few m/s² while
            // the phone is carried around. x10 puts a hand-held sweep over the same gate.
            val mag = sqrt(e.values[0].toDouble() * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
            val delta = if (lastAccelMag.isNaN()) 0.0 else abs(mag - lastAccelMag)
            lastAccelMag = mag
            delta * 10.0
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
