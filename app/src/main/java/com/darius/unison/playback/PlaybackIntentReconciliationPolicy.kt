package com.darius.unison.playback

import com.darius.unison.model.LocalPlaybackParticipation

/**
 * Reconciles canonical room transport intent without overriding a device-local safety pause.
 *
 * Audio-focus loss and "becoming noisy" are local output conditions, not room commands. Periodic
 * state sync must not unexpectedly restart sound on that phone. Conversely, when a locally
 * inhibited user presses Play while the canonical room is already advancing, that means "rejoin the
 * live room"; issuing a second canonical Play would unnecessarily reschedule every peer.
 */
object PlaybackIntentReconciliationPolicy {
    enum class Action {
        NONE,
        PLAY,
        PAUSE,
    }

    enum class PlayRequestAction {
        REJOIN_LIVE_ROOM,
        MUTATE_CANONICAL_ROOM,
    }

    fun decide(
        canonicalPlaying: Boolean,
        localPlayWhenReady: Boolean,
        participation: LocalPlaybackParticipation,
    ): Action =
        when {
            participation != LocalPlaybackParticipation.ACTIVE -> Action.NONE
            canonicalPlaying && !localPlayWhenReady -> Action.PLAY
            !canonicalPlaying && localPlayWhenReady -> Action.PAUSE
            else -> Action.NONE
        }

    fun decidePlayRequest(
        canonicalPlaying: Boolean,
        participation: LocalPlaybackParticipation,
    ): PlayRequestAction =
        if (canonicalPlaying && participation == LocalPlaybackParticipation.OUTPUT_INHIBITED) {
            PlayRequestAction.REJOIN_LIVE_ROOM
        } else {
            PlayRequestAction.MUTATE_CANONICAL_ROOM
        }
}
