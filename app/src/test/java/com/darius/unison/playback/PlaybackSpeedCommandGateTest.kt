package com.darius.unison.playback

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSpeedCommandGateTest {
    @Test
    fun firstCorrectionIsAppliedAndQuantized() {
        val gate = PlaybackSpeedCommandGate()
        val selected = gate.select(requestedSpeed = 1.0015962f, actualSpeed = 1f, nowNs = 0L)
        assertClose(1.0015f, selected)
    }

    @Test
    fun microscopicTargetMovementDoesNotReconfigurePlayer() {
        val gate = PlaybackSpeedCommandGate()
        assertClose(1.0015f, gate.select(1.0015962f, 1f, 0L))
        assertNull(gate.select(1.0015688f, 1.0015f, 500_000_000L))
        assertNull(gate.select(1.0014929f, 1.0015f, 2_500_000_000L))
    }

    @Test
    fun nextQuantizedStepWaitsForMinimumInterval() {
        val gate = PlaybackSpeedCommandGate()
        gate.select(1.00155f, 1f, 0L)
        assertNull(gate.select(1.00124f, 1.0015f, 1_000_000_000L))
        assertClose(1.00125f, gate.select(1.00124f, 1.0015f, 3_000_000_000L))
    }

    @Test
    fun largeCorrectionChangeBypassesInterval() {
        val gate = PlaybackSpeedCommandGate()
        gate.select(1.0015f, 1f, 0L)
        val selected = gate.select(0.9995f, 1.0015f, 500_000_000L)
        assertClose(0.9995f, selected)
    }

    @Test
    fun restoringNormalSpeedIsImmediate() {
        val gate = PlaybackSpeedCommandGate()
        gate.select(1.00075f, 1f, 0L)
        val selected = gate.select(1f, 1.00075f, 500_000_000L)
        assertClose(1f, selected)
    }

    @Test
    fun alreadyAppliedSpeedIsNoOp() {
        val gate = PlaybackSpeedCommandGate()
        assertNull(gate.select(1.00074f, 1.00075f, 0L))
    }

    @Test
    fun clampsUnsafeRequests() {
        val gate = PlaybackSpeedCommandGate()
        val selected = gate.select(1.5f, 1f, 0L)
        assertTrue(selected != null)
        assertClose(1.005f, selected)
    }
    @Test
    fun fineGrainedControllerTargetsProduceOnlyAHandfulOfPlayerReconfigurations() {
        val gate = PlaybackSpeedCommandGate()
        var actual = 1f
        var appliedCount = 0

        repeat(120) { tick ->
            val requested = 1.0016f - tick * (0.00095f / 119f)
            val selected = gate.select(
                requestedSpeed = requested,
                actualSpeed = actual,
                nowNs = tick * 500_000_000L,
            )
            if (selected != null) {
                actual = selected
                appliedCount++
            }
        }

        assertTrue(appliedCount <= 4)
        assertTrue(actual in 1.0007f..1.0008f)
    }

    private fun assertClose(expected: Float, actual: Float?) {
        assertTrue(actual != null)
        assertTrue(kotlin.math.abs(expected - (actual ?: 0f)) <= 0.000001f)
    }
}
