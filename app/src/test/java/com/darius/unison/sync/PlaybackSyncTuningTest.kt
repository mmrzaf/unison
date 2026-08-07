package com.darius.unison.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSyncTuningTest {
    @Test
    fun balancedIsTheDefaultControllerProfile() {
        assertEquals(PlaybackSyncProfile.BALANCED.tuning(), PlaybackSyncController().tuning)
    }

    @Test
    fun profilesHaveIntentionalAggressivenessOrdering() {
        val tight = PlaybackSyncProfile.TIGHT.tuning()
        val balanced = PlaybackSyncProfile.BALANCED.tuning()
        val smooth = PlaybackSyncProfile.SMOOTH.tuning()

        assertTrue(tight.driftEnterThresholdMs < balanced.driftEnterThresholdMs)
        assertTrue(balanced.driftEnterThresholdMs < smooth.driftEnterThresholdMs)
        assertTrue(tight.speedCommandIntervalMs < balanced.speedCommandIntervalMs)
        assertTrue(balanced.speedCommandIntervalMs < smooth.speedCommandIntervalMs)
        assertTrue(tight.hardSeekThresholdMs < smooth.hardSeekThresholdMs)
        assertTrue(tight.maxSpeedDelta >= smooth.maxSpeedDelta)
    }

    @Test
    fun profileChangeReacquiresInsteadOfKeepingFeedbackHistory() {
        val controller = PlaybackSyncController(PlaybackSyncProfile.TIGHT.tuning())
        controller.reset(preserveLearnedBaseline = false)
        controller.updateTuning(PlaybackSyncProfile.SMOOTH.tuning())

        assertEquals(PlaybackSyncState.ACQUIRING, controller.state)
        assertEquals(PlaybackSyncProfile.SMOOTH.tuning(), controller.tuning)
        assertEquals(1f, controller.baselineSpeed)
    }
}
