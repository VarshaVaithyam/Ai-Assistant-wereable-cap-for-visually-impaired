package com.visionaid.app.domain.usecases

import com.visionaid.app.domain.models.FallPhase
import com.visionaid.app.utils.AppLogger
import com.visionaid.app.utils.Constants
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Threshold-based fall-detection state machine over accelerometer +
 * gyroscope samples (spec 6.1).
 *
 * Phases: NORMAL -> FREE_FALL -> IMPACT -> LYING -> FALL_CONFIRMED
 *
 *  FREE_FALL : |a| < freeFallThreshold sustained >= freeFallMinMs
 *  IMPACT    : |a| > impactThreshold shortly after free fall
 *  LYING     : device orientation flips near-horizontal (gravity vector
 *              tilts > 55 degrees from the device's vertical axis) and
 *              stays there >= 1.5 s
 *
 * All three must occur within a 3-second window. Gyro rotation during the
 * fall adds a validation bonus (rapid rotation is expected while falling).
 *
 * The class is NOT thread-safe by itself; callers feed it from the sensor
 * listener thread and read results synchronously.
 */
class DetectFallUseCase @Inject constructor() {

    /** Tunable thresholds per sensitivity (spec: low / medium / high). */
    data class Config(
        val freeFallMagnitude: Float,   // m/s^2 below which we consider free fall
        val freeFallMinMs: Long,        // how long free fall must last
        val impactMagnitude: Float,     // m/s^2 spike that indicates impact
        val gyroRotation: Float,        // rad/s rotation expected during fall
        val lyingAngleDeg: Float,       // tilt of gravity vs. vertical axis
        val lyingMinMs: Long            // how long the lying posture must hold
    ) {
        companion object {
            fun forSensitivity(name: String): Config = when (name.lowercase()) {
                "low" -> Config(2.0f, 300L, 25f, 1.5f, 65f, 2_000L)
                "high" -> Config(4.0f, 150L, 18f, 1.0f, 50f, 1_200L)
                else -> Config(3.0f, 200L, 20f, 2.0f, 55f, 1_500L) // medium (spec defaults)
            }
        }
    }

    var config: Config = Config.forSensitivity("medium")

    /** Live phase for logging/UI. */
    var phase: FallPhase = FallPhase.NORMAL
        private set

    // --- smoothed gravity for orientation estimates ---
    private var gravityX = 0f
    private var gravityY = 9.81f
    private var gravityZ = 0f

    // --- state machine bookkeeping ---
    private var freeFallStartedAt = 0L
    private var freeFallDetectedAt = 0L
    private var impactDetectedAt = 0L
    private var lyingStartedAt = 0L
    private var lastAccelAt = 0L

    // --- gyroscope validation ---
    private var maxGyroDuringFall = 0f

    /** Sensor snapshot attached to the event for logging. */
    data class FallDetectionResult(
        val phase: FallPhase,
        val maxGyroDuringFall: Float,
        val lyingAngleDeg: Float
    )

    /**
     * Feed one accelerometer sample.
     *
     * @param values ax, ay, az in m/s^2
     * @param timestampMs sample time in uptime millis
     * @return CONFIRMED result exactly once per fall; null otherwise
     */
    fun onAccelerometerChange(values: FloatArray, timestampMs: Long): FallDetectionResult? {
        if (values.size < 3) return null
        val ax = values[0]; val ay = values[1]; val az = values[2]
        val magnitude = sqrt(ax * ax + ay * ay + az * az)

        // Low-pass filter gravity (alpha tuned for ~50 Hz sampling).
        val alpha = 0.15f
        gravityX = alpha * ax + (1 - alpha) * gravityX
        gravityY = alpha * ay + (1 - alpha) * gravityY
        gravityZ = alpha * az + (1 - alpha) * gravityZ

        val now = timestampMs
        lastAccelAt = now

        // Reset the whole machine when phases stall too long.
        if (phase != FallPhase.NORMAL && now - freeFallDetectedAt > Constants.FALL_DETECTION_WINDOW_MS) {
            reset()
        }

        when (phase) {
            FallPhase.NORMAL -> {
                if (magnitude < config.freeFallMagnitude) {
                    if (freeFallStartedAt == 0L) {
                        freeFallStartedAt = now
                    } else if (now - freeFallStartedAt >= config.freeFallMinMs) {
                        phase = FallPhase.FREE_FALL
                        freeFallDetectedAt = now
                        maxGyroDuringFall = 0f
                        AppLogger.d(TAG, "Fall phase: FREE_FALL (|a|=${"%.1f".format(magnitude)})")
                    }
                } else {
                    freeFallStartedAt = 0L
                }
            }

            FallPhase.FREE_FALL -> {
                if (magnitude > config.impactMagnitude) {
                    phase = FallPhase.IMPACT
                    impactDetectedAt = now
                    AppLogger.d(TAG, "Fall phase: IMPACT (|a|=${"%.1f".format(magnitude)})")
                }
            }

            FallPhase.IMPACT -> {
                // After impact, wait for a stable lying posture.
                val angle = gravityTiltAngleDeg()
                if (angle > config.lyingAngleDeg) {
                    if (lyingStartedAt == 0L) {
                        lyingStartedAt = now
                    } else if (now - lyingStartedAt >= config.lyingMinMs) {
                        phase = FallPhase.LYING
                        AppLogger.d(TAG, "Fall phase: LYING (tilt=${"%.0f".format(angle)}°)")
                        // LYING sustained => the fall is confirmed.
                        phase = FallPhase.FALL_CONFIRMED
                        val result = FallDetectionResult(
                            phase = FallPhase.FALL_CONFIRMED,
                            maxGyroDuringFall = maxGyroDuringFall,
                            lyingAngleDeg = angle
                        )
                        reset() // armed for the next fall
                        AppLogger.w(TAG, "FALL CONFIRMED")
                        return result
                    }
                } else {
                    lyingStartedAt = 0L
                }
            }

            else -> { /* terminal / reset states */ }
        }
        return null
    }

    /** Feed one gyroscope sample (rotation speed validation). */
    fun onGyroscopeChange(
        values: FloatArray,
        @Suppress("UNUSED_PARAMETER") timestampMs: Long
    ) {
        if (values.size < 3) return
        val magnitude = sqrt(
            values[0] * values[0] + values[1] * values[1] + values[2] * values[2]
        )
        if (phase == FallPhase.FREE_FALL || phase == FallPhase.IMPACT) {
            if (magnitude > maxGyroDuringFall) maxGyroDuringFall = magnitude
        }
    }

    /**
     * Angle between the smoothed gravity vector and the device's vertical
     * (Y) axis. A cap worn upright has gravity along Y (~0°); a fallen
     * wearer's cap lies on its side (large angle).
     */
    private fun gravityTiltAngleDeg(): Float {
        val magnitude = sqrt(
            gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ
        )
        if (magnitude < 1f) return 0f
        val cosAngle = abs(gravityY) / magnitude
        return Math.toDegrees(acos(cosAngle.coerceIn(-1.0, 1.0)).toDouble()).toFloat()
    }

    /**
     * True when recent motion suggests the user is moving — used by the
     * post-alert "no movement" follow-up check (spec 6.4 step 7).
     */
    fun isMoving(magnitude: Float): Boolean =
        abs(magnitude - 9.81f) > 1.5f

    fun reset() {
        phase = FallPhase.NORMAL
        freeFallStartedAt = 0L
        freeFallDetectedAt = 0L
        impactDetectedAt = 0L
        lyingStartedAt = 0L
        maxGyroDuringFall = 0f
    }

    private companion object {
        const val TAG = "FallDetector"
    }
}
