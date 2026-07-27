package com.darius.unison.sync

import kotlin.math.abs

class PlaybackSyncEngine(
    private val ignoreThresholdMs: Long = 35,
    private val seekThresholdMs: Long = 100,
) {
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
                val speed = if (drift > 0) 1.01f else 0.99f
                val duration = (magnitude * 100).coerceIn(600L, 3_000L)
                Correction.AdjustSpeed(speed, duration)
            }
        }
    }
}
