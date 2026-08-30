package com.darius.unison.playback

import com.darius.unison.sync.PlaybackSyncProfile
import com.darius.unison.sync.PlaybackSyncTuning
import com.darius.unison.sync.tuning
import kotlin.math.abs
import kotlin.math.round

/**
 * Reduces audible artifacts from repeatedly reconfiguring the audio pipeline for microscopic
 * playback-speed changes. Measurement can run frequently; player actuation stays bounded and
 * deliberately sparse.
 */
class PlaybackSpeedCommandGate(tuning: PlaybackSyncTuning = PlaybackSyncProfile.BALANCED.tuning()) {
    private var tuning = tuning
    private var lastAppliedSpeed: Float? = null
    private var lastAppliedAtNs: Long? = null

    fun updateTuning(value: PlaybackSyncTuning) {
        if (value == tuning) return
        tuning = value
        reset()
    }

    /**
     * Returns the speed that should be applied now, or null when the current command should hold.
     */
    fun select(requestedSpeed: Float, actualSpeed: Float, nowNs: Long): Float? {
        val target = quantize(requestedSpeed)
        val actual = actualSpeed.coerceIn(tuning.minimumSpeed, tuning.maximumSpeed)

        val previousAt = lastAppliedAtNs
        if (previousAt != null && nowNs < previousAt) reset()

        if (abs(actual - target) <= tuning.actualSpeedMatchTolerance) {
            lastAppliedSpeed = target
            if (lastAppliedAtNs == null) lastAppliedAtNs = nowNs
            return null
        }

        val last = lastAppliedSpeed
        val elapsedNs = lastAppliedAtNs?.let { nowNs - it } ?: Long.MAX_VALUE
        val intervalElapsed = elapsedNs >= tuning.speedCommandIntervalMs * 1_000_000L
        val urgent = abs(target - actual) >= tuning.urgentSpeedDelta
        val directionChanged = last != null && crossesUnity(last, target)

        // Returning to baseline still reconfigures Media3. Small restorations obey the same rate
        // limit so threshold noise cannot toggle the audio pipeline every synchronization tick.
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
        val bounded = speed.coerceIn(tuning.minimumSpeed, tuning.maximumSpeed)
        if (abs(bounded - 1f) <= tuning.actualSpeedMatchTolerance) return 1f
        val steps = round((bounded - 1f) / tuning.speedQuantizationStep)
        val quantized = 1f + steps * tuning.speedQuantizationStep
        return quantized.coerceIn(tuning.minimumSpeed, tuning.maximumSpeed)
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
