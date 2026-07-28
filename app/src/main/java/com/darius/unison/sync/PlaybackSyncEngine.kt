package com.darius.unison.sync

import kotlin.math.abs

/**
 * Stability-first drift recovery. Scheduled room commands remain the primary synchronization path;
 * this policy only repairs sustained divergence without turning routine jitter into seek churn.
 */
class PlaybackSyncEngine(
    private val ignoreThresholdMs: Long = 300,
    private val seekThresholdMs: Long = 1_500,
    private val minimumSpeedDelta: Float = 0.02f,
    private val maximumSpeedDelta: Float = 0.04f,
    private val minimumCorrectionDurationMs: Long = 6_000,
    private val maximumCorrectionDurationMs: Long = 30_000,
) {
    init {
        require(ignoreThresholdMs >= 0)
        require(seekThresholdMs > ignoreThresholdMs)
        require(minimumSpeedDelta > 0f)
        require(maximumSpeedDelta >= minimumSpeedDelta)
        require(minimumCorrectionDurationMs > 0)
        require(maximumCorrectionDurationMs >= minimumCorrectionDurationMs)
    }

    sealed interface Correction {
        data object None : Correction
        data class AdjustSpeed(val speed: Float, val durationMs: Long) : Correction
        data class Seek(val positionMs: Long) : Correction
    }

    fun correction(expectedPositionMs: Long, actualPositionMs: Long): Correction {
        val drift = expectedPositionMs - actualPositionMs
        val magnitude = abs(drift)
        return when {
            magnitude < ignoreThresholdMs -> Correction.None
            magnitude >= seekThresholdMs -> Correction.Seek(expectedPositionMs.coerceAtLeast(0))
            else -> {
                val range = (seekThresholdMs - ignoreThresholdMs).coerceAtLeast(1)
                val severity = ((magnitude - ignoreThresholdMs).toDouble() / range)
                    .coerceIn(0.0, 1.0)
                    .toFloat()
                val speedDelta = minimumSpeedDelta +
                    (maximumSpeedDelta - minimumSpeedDelta) * severity
                val durationMs = (magnitude / speedDelta)
                    .toLong()
                    .coerceIn(minimumCorrectionDurationMs, maximumCorrectionDurationMs)
                val speed = if (drift > 0) 1f + speedDelta else 1f - speedDelta
                Correction.AdjustSpeed(speed, durationMs)
            }
        }
    }
}
