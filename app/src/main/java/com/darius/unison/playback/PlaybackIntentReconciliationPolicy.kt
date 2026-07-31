package com.darius.unison.playback

import com.darius.unison.model.QueueItemId

/**
 * Reconciles canonical room transport intent without overriding a device-local safety pause.
 *
 * Audio-focus loss and "becoming noisy" are local output conditions, not room commands. Periodic
 * state sync must not unexpectedly restart sound on that phone. Conversely, when the user presses
 * Play while the canonical room is already advancing, only that locally suppressed output should
 * resume; issuing a second canonical Play would unnecessarily reschedule every peer.
 */
object PlaybackIntentReconciliationPolicy {
    enum class Action {
        NONE,
        PLAY,
        PAUSE,
    }

    enum class PlayRequestAction {
        RESUME_LOCAL_OUTPUT,
        MUTATE_CANONICAL_ROOM,
    }

    fun decide(
        canonicalPlaying: Boolean,
        localPlayWhenReady: Boolean,
        locallySuppressed: Boolean,
    ): Action =
        when {
            canonicalPlaying && !localPlayWhenReady && !locallySuppressed -> Action.PLAY
            !canonicalPlaying && localPlayWhenReady -> Action.PAUSE
            else -> Action.NONE
        }

    fun decidePlayRequest(
        canonicalPlaying: Boolean,
        canonicalQueueItemId: QueueItemId?,
        localQueueItemId: QueueItemId?,
        locallySuppressed: Boolean,
    ): PlayRequestAction =
        if (
            canonicalPlaying &&
                locallySuppressed &&
                canonicalQueueItemId != null &&
                canonicalQueueItemId == localQueueItemId
        ) {
            PlayRequestAction.RESUME_LOCAL_OUTPUT
        } else {
            PlayRequestAction.MUTATE_CANONICAL_ROOM
        }
}
