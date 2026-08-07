package com.darius.unison.room

import com.darius.unison.sync.PlaybackSyncState
import com.darius.unison.sync.PlaybackSyncTuning

/** Chooses the lowest useful synchronization cadence for the current playback condition. */
object PlaybackSyncCadencePolicy {
    fun intervalMs(
        queueItemPresent: Boolean,
        canonicalPlaying: Boolean,
        scheduledCommandPresent: Boolean,
        localBuffering: Boolean,
        syncState: PlaybackSyncState,
        tuning: PlaybackSyncTuning,
    ): Long? =
        when {
            (!queueItemPresent || !canonicalPlaying) && !scheduledCommandPresent -> null
            localBuffering -> tuning.activeCorrectionIntervalMs
            syncState == PlaybackSyncState.HARD_SEEKING ||
                syncState == PlaybackSyncState.SOFT_CORRECTING ||
                syncState == PlaybackSyncState.ACQUIRING ||
                syncState == PlaybackSyncState.SETTLING -> tuning.activeCorrectionIntervalMs
            syncState == PlaybackSyncState.TRACKING -> tuning.stablePlayingIntervalMs
            else -> tuning.waitingIntervalMs
        }
}
