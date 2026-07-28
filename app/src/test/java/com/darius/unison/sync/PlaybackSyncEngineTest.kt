package com.darius.unison.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSyncEngineTest {
    private val engine = PlaybackSyncEngine(
        ignoreThresholdMs = 300,
        seekThresholdMs = 1_500,
        minimumSpeedDelta = 0.02f,
        maximumSpeedDelta = 0.04f,
        minimumCorrectionDurationMs = 6_000,
        maximumCorrectionDurationMs = 30_000,
    )

    @Test
    fun ignoresRoutineDrift() {
        assertEquals(PlaybackSyncEngine.Correction.None, engine.correction(10_000, 9_701))
    }

    @Test
    fun thresholdDriftUsesSoftCorrection() {
        assertTrue(engine.correction(10_000, 9_700) is PlaybackSyncEngine.Correction.AdjustSpeed)
    }

    @Test
    fun proportionallySpeedsUpWhenBehind() {
        val correction = engine.correction(10_000, 9_000)
        assertTrue(correction is PlaybackSyncEngine.Correction.AdjustSpeed)
        correction as PlaybackSyncEngine.Correction.AdjustSpeed
        assertTrue(correction.speed in 1.03f..1.04f)
        assertEquals(30_000L, correction.durationMs)
    }

    @Test
    fun proportionallySlowsDownWhenAhead() {
        val correction = engine.correction(10_000, 10_500) as PlaybackSyncEngine.Correction.AdjustSpeed
        assertTrue(correction.speed in 0.97f..0.98f)
        assertTrue(correction.durationMs in 20_000L..22_000L)
    }

    @Test
    fun seeksOnlyClearlyBrokenDrift() {
        assertEquals(
            PlaybackSyncEngine.Correction.Seek(10_000),
            engine.correction(10_000, 8_500),
        )
    }

    @Test
    fun justBelowSeekThresholdRemainsSoft() {
        assertTrue(engine.correction(10_000, 8_501) is PlaybackSyncEngine.Correction.AdjustSpeed)
    }
}
