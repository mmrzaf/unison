package com.darius.unison.room

import com.darius.unison.sync.PlaybackSyncState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSyncCadencePolicyTest {
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
            ),
        )
    }

    @Test
    fun scheduledPlaybackResumesMonitoring() {
        assertEquals(
            PlaybackSyncCadencePolicy.WAITING_INTERVAL_MS,
            PlaybackSyncCadencePolicy.intervalMs(
                false,
                false,
                true,
                false,
                PlaybackSyncState.WAITING_FOR_MEDIA,
            ),
        )
    }

    @Test
    fun activeCorrectionRetainsFastFeedback() {
        assertEquals(
            PlaybackSyncCadencePolicy.ACTIVE_CORRECTION_INTERVAL_MS,
            PlaybackSyncCadencePolicy.intervalMs(
                true,
                true,
                false,
                false,
                PlaybackSyncState.SOFT_CORRECTING,
            ),
        )
        assertEquals(
            PlaybackSyncCadencePolicy.ACTIVE_CORRECTION_INTERVAL_MS,
            PlaybackSyncCadencePolicy.intervalMs(
                true,
                true,
                false,
                true,
                PlaybackSyncState.TRACKING,
            ),
        )
    }

    @Test
    fun stablePlayingRoomUsesOneSecondCadence() {
        assertEquals(
            PlaybackSyncCadencePolicy.STABLE_PLAYING_INTERVAL_MS,
            PlaybackSyncCadencePolicy.intervalMs(
                true,
                true,
                false,
                false,
                PlaybackSyncState.TRACKING,
            ),
        )
    }
}
