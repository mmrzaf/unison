package com.darius.unison.room

import com.darius.unison.sync.PlaybackSyncProfile
import com.darius.unison.sync.PlaybackSyncState
import com.darius.unison.sync.tuning
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSyncCadencePolicyTest {
    private val tuning = PlaybackSyncProfile.BALANCED.tuning()

    @Test
    fun emptyAndPausedRoomsSuspendSynchronizationTicks() {
        assertEquals(
            null,
            PlaybackSyncCadencePolicy.intervalMs(
                false,
                false,
                false,
                false,
                PlaybackSyncState.WAITING_FOR_MEDIA,
                tuning,
            ),
        )
        assertEquals(
            null,
            PlaybackSyncCadencePolicy.intervalMs(
                true,
                false,
                false,
                false,
                PlaybackSyncState.PAUSED,
                tuning,
            ),
        )
    }

    @Test
    fun scheduledPlaybackResumesMonitoring() {
        assertEquals(
            tuning.waitingIntervalMs,
            PlaybackSyncCadencePolicy.intervalMs(
                false,
                false,
                true,
                false,
                PlaybackSyncState.WAITING_FOR_MEDIA,
                tuning,
            ),
        )
    }

    @Test
    fun activeCorrectionRetainsFastFeedback() {
        assertEquals(
            tuning.activeCorrectionIntervalMs,
            PlaybackSyncCadencePolicy.intervalMs(
                true,
                true,
                false,
                false,
                PlaybackSyncState.SOFT_CORRECTING,
                tuning,
            ),
        )
        assertEquals(
            tuning.activeCorrectionIntervalMs,
            PlaybackSyncCadencePolicy.intervalMs(
                true,
                true,
                false,
                true,
                PlaybackSyncState.TRACKING,
                tuning,
            ),
        )
    }

    @Test
    fun stablePlayingRoomUsesProfileCadence() {
        assertEquals(
            tuning.stablePlayingIntervalMs,
            PlaybackSyncCadencePolicy.intervalMs(
                true,
                true,
                false,
                false,
                PlaybackSyncState.TRACKING,
                tuning,
            ),
        )
    }
}
