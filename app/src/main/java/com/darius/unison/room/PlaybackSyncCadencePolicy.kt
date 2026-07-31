package com.darius.unison.room

import com.darius.unison.sync.PlaybackSyncState

/** Chooses the lowest useful synchronization cadence for the current playback condition. */
object PlaybackSyncCadencePolicy {
    fun intervalMs(
        queueItemPresent: Boolean,
        canonicalPlaying: Boolean,
        scheduledCommandPresent: Boolean,
        localBuffering: Boolean,
        syncState: PlaybackSyncState,
    ): Long? =
        when {
            (!queueItemPresent || !canonicalPlaying) && !scheduledCommandPresent -> null
            localBuffering -> ACTIVE_CORRECTION_INTERVAL_MS
            syncState == PlaybackSyncState.HARD_SEEKING ||
                syncState == PlaybackSyncState.SOFT_CORRECTING ||
                syncState == PlaybackSyncState.ACQUIRING ||
                syncState == PlaybackSyncState.SETTLING -> ACTIVE_CORRECTION_INTERVAL_MS
            syncState == PlaybackSyncState.TRACKING -> STABLE_PLAYING_INTERVAL_MS
            else -> WAITING_INTERVAL_MS
        }

    const val ACTIVE_CORRECTION_INTERVAL_MS = 500L
    const val STABLE_PLAYING_INTERVAL_MS = 1_000L
    const val WAITING_INTERVAL_MS = 1_500L
    const val SUSPENDED_RECHECK_INTERVAL_MS = 1_000L
}
