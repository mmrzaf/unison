package com.darius.unison.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPausePolicyTest {
    @Test
    fun `watchdog cannot pause immediately after healthy automatic transition`() {
        assertFalse(
            PlaybackPausePolicy.shouldApply(
                cause = PlaybackPauseCause.WATCHDOG_RECONCILIATION,
                playWhenReady = true,
                lastNaturalTransitionNs = 1_000_000_000L,
                nowNs = 2_000_000_000L,
            )
        )
    }

    @Test
    fun `explicit and settled pauses are never hidden by transition guard`() {
        assertTrue(
            PlaybackPausePolicy.shouldApply(
                cause = PlaybackPauseCause.SCHEDULED_TRANSPORT,
                playWhenReady = true,
                lastNaturalTransitionNs = 1_000_000_000L,
                nowNs = 1_100_000_000L,
            )
        )
        assertTrue(
            PlaybackPausePolicy.shouldApply(
                cause = PlaybackPauseCause.WATCHDOG_RECONCILIATION,
                playWhenReady = true,
                lastNaturalTransitionNs = 1_000_000_000L,
                nowNs = 3_000_000_001L,
            )
        )
    }
}
