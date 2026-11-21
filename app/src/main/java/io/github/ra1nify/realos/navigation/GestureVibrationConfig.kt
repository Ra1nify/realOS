package io.github.ra1nify.realos.navigation

import org.json.JSONObject
import io.github.ra1nify.realos.navigation.GestureVibrationConfig
import io.github.ra1nify.realos.navigation.GestureVibrationCodec

/**
 * Two-stage back gesture vibration tuning parameters.
 */
data class GestureVibrationConfig(
    val weakStrength: Int = DEFAULT_WEAK_STRENGTH,
    val stepDp: Float = DEFAULT_STEP_DP,
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    val strongStrength: Int = DEFAULT_STRONG_STRENGTH,
    val takeOverStrong: Boolean = true
) {
    val weakStrengthClamped: Int get() = weakStrength.coerceIn(STRENGTH_MIN, STRENGTH_MAX)
    val strongStrengthClamped: Int get() = strongStrength.coerceIn(STRENGTH_MIN, STRENGTH_MAX)
    val stepDpClamped: Float get() = stepDp.coerceIn(STEP_DP_MIN, STEP_DP_MAX)
    val intervalMsClamped: Long get() = intervalMs.coerceIn(INTERVAL_MIN, INTERVAL_MAX)

    companion object {
        const val KEY_VIBRATION = "systemui_gesture_back_vibration"

        const val STRENGTH_MIN = 0
        const val STRENGTH_MAX = 2048

        const val STEP_DP_MIN = 2f
        const val STEP_DP_MAX = 24f

        const val INTERVAL_MIN = 45L
        const val INTERVAL_MAX = 500L

        const val DEFAULT_WEAK_STRENGTH = 600
        const val DEFAULT_STEP_DP = 10f
        const val DEFAULT_INTERVAL_MS = 60L
        const val DEFAULT_STRONG_STRENGTH = 2000
    }
}

object GestureVibrationCodec {
    private const val VERSION = 1

    fun toJson(c: GestureVibrationConfig): String = runCatching {
        JSONObject().apply {
            put("v", VERSION)
            put("weakStrength", c.weakStrength)
            put("stepDp", c.stepDp.toDouble())
            put("intervalMs", c.intervalMs)
            put("strongStrength", c.strongStrength)
            put("takeOverStrong", c.takeOverStrong)
        }.toString()
    }.getOrDefault("")

    fun fromJson(s: String?): GestureVibrationConfig {
        if (s.isNullOrBlank()) return GestureVibrationConfig()
        val d = GestureVibrationConfig()
        return runCatching {
            val o = JSONObject(s)
            GestureVibrationConfig(
                weakStrength = o.optInt("weakStrength", d.weakStrength)
                    .coerceIn(GestureVibrationConfig.STRENGTH_MIN, GestureVibrationConfig.STRENGTH_MAX),
                stepDp = o.optDouble("stepDp", d.stepDp.toDouble()).toFloat()
                    .coerceIn(GestureVibrationConfig.STEP_DP_MIN, GestureVibrationConfig.STEP_DP_MAX),
                intervalMs = o.optLong("intervalMs", d.intervalMs)
                    .coerceIn(GestureVibrationConfig.INTERVAL_MIN, GestureVibrationConfig.INTERVAL_MAX),
                strongStrength = o.optInt("strongStrength", d.strongStrength)
                    .coerceIn(GestureVibrationConfig.STRENGTH_MIN, GestureVibrationConfig.STRENGTH_MAX),
                takeOverStrong = o.optBoolean("takeOverStrong", d.takeOverStrong)
            )
        }.getOrDefault(GestureVibrationConfig())
    }
}
