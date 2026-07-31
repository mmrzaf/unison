package com.darius.unison.playback

import kotlin.math.abs
import kotlin.math.round

/**
 * Reduces audible artifacts from repeatedly reconfiguring the audio pipeline for microscopic
 * playback-speed changes. The synchronization controller may still evaluate every tick; this gate
 * only forwards stable, quantized actuator commands to the player.
 */
data class PlaybackSpeedCommandGateConfig(
    val minimumSpeed: Float = 0.995f,
    val maximumSpeed: Float = 1.005f,
    val quantizationStep: Float = 0.00025f,
    val minimumUpdateIntervalMs: Long = 4_000L,
    val urgentDelta: Float = 0.002f,
    val actualMatchTolerance: Float = 0.00010f,
) {
    init {
        require(minimumSpeed > 0f)
        require(maximumSpeed >= minimumSpeed)
        require(quantizationStep > 0f)
        require(minimumUpdateIntervalMs >= 0L)
        require(urgentDelta >= quantizationStep)
        require(actualMatchTolerance >= 0f)
    }
}

class PlaybackSpeedCommandGate(
    private val config: PlaybackSpeedCommandGateConfig = PlaybackSpeedCommandGateConfig()
) {
    private var lastAppliedSpeed: Float? = null
    private var lastAppliedAtNs: Long? = null

    /**
     * Returns the speed that should be applied now, or null when the current command should hold.
     */
    fun select(requestedSpeed: Float, actualSpeed: Float, nowNs: Long): Float? {
        val target = quantize(requestedSpeed)
        val actual = actualSpeed.coerceIn(config.minimumSpeed, config.maximumSpeed)

        val previousAt = lastAppliedAtNs
        if (previousAt != null && nowNs < previousAt) reset()

        if (abs(actual - target) <= config.actualMatchTolerance) {
            lastAppliedSpeed = target
            if (lastAppliedAtNs == null) lastAppliedAtNs = nowNs
            return null
        }

        val last = lastAppliedSpeed
        val elapsedNs = lastAppliedAtNs?.let { nowNs - it } ?: Long.MAX_VALUE
        val intervalElapsed = elapsedNs >= config.minimumUpdateIntervalMs * 1_000_000L
        val urgent = abs(target - actual) >= config.urgentDelta
        val directionChanged = last != null && crossesUnity(last, target)

        // Returning to 1.0x is still a player reconfiguration. Small restorations respect the same
        // minimum interval so threshold noise cannot toggle the audio pipeline every few ticks.
        if (!intervalElapsed && !urgent && !directionChanged) return null

        lastAppliedSpeed = target
        lastAppliedAtNs = nowNs
        return target
    }

    fun reset() {
        lastAppliedSpeed = null
        lastAppliedAtNs = null
    }

    private fun quantize(speed: Float): Float {
        val bounded = speed.coerceIn(config.minimumSpeed, config.maximumSpeed)
        if (abs(bounded - 1f) <= config.actualMatchTolerance) return 1f
        val steps = round((bounded - 1f) / config.quantizationStep)
        val quantized = 1f + steps * config.quantizationStep
        return quantized.coerceIn(config.minimumSpeed, config.maximumSpeed)
    }

    private fun crossesUnity(previous: Float, target: Float): Boolean {
        val previousSide = (previous - 1f).sign()
        val targetSide = (target - 1f).sign()
        return previousSide != 0 && targetSide != 0 && previousSide != targetSide
    }

    private fun Float.sign(): Int =
        when {
            this > 0f -> 1
            this < 0f -> -1
            else -> 0
        }
}
