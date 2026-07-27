package com.darius.unison.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSyncEngineTest {
    private val engine = PlaybackSyncEngine(ignoreThresholdMs = 35, seekThresholdMs = 100)

    @Test
    fun ignoresSmallDrift() {
        assertEquals(PlaybackSyncEngine.Correction.None, engine.correction(10_000, 9_970))
    }

    @Test
    fun gentlySpeedsUpWhenBehind() {
        val correction = engine.correction(10_000, 9_940)
        assertTrue(correction is PlaybackSyncEngine.Correction.AdjustSpeed)
        correction as PlaybackSyncEngine.Correction.AdjustSpeed
        assertTrue(correction.speed > 1f)
        assertTrue(correction.durationMs in 600L..3_000L)
    }

    @Test
    fun gentlySlowsDownWhenAhead() {
        val correction = engine.correction(10_000, 10_060) as PlaybackSyncEngine.Correction.AdjustSpeed
        assertTrue(correction.speed < 1f)
    }

    @Test
    fun seeksLargeDrift() {
        assertEquals(
            PlaybackSyncEngine.Correction.Seek(10_000),
            engine.correction(10_000, 9_850),
        )
    }
}
