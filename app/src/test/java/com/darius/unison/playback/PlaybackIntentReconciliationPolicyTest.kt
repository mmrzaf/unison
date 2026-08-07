package com.darius.unison.playback

import com.darius.unison.model.LocalPlaybackParticipation
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackIntentReconciliationPolicyTest {
    @Test
    fun `canonical play resumes an ordinary active mismatch`() {
        assertEquals(
            PlaybackIntentReconciliationPolicy.Action.PLAY,
            PlaybackIntentReconciliationPolicy.decide(
                canonicalPlaying = true,
                localPlayWhenReady = false,
                participation = LocalPlaybackParticipation.ACTIVE,
            ),
        )
    }

    @Test
    fun `canonical state sync never overrides an inhibited or rejoining device`() {
        listOf(
            LocalPlaybackParticipation.OUTPUT_INHIBITED,
            LocalPlaybackParticipation.REJOINING,
        ).forEach { participation ->
            assertEquals(
                PlaybackIntentReconciliationPolicy.Action.NONE,
                PlaybackIntentReconciliationPolicy.decide(true, false, participation),
            )
        }
    }

    @Test
    fun `canonical pause still pauses an active phone`() {
        assertEquals(
            PlaybackIntentReconciliationPolicy.Action.PAUSE,
            PlaybackIntentReconciliationPolicy.decide(
                canonicalPlaying = false,
                localPlayWhenReady = true,
                participation = LocalPlaybackParticipation.ACTIVE,
            ),
        )
    }

    @Test
    fun `matching active transport intent does nothing`() {
        assertEquals(
            PlaybackIntentReconciliationPolicy.Action.NONE,
            PlaybackIntentReconciliationPolicy.decide(
                true, true, LocalPlaybackParticipation.ACTIVE),
        )
        assertEquals(
            PlaybackIntentReconciliationPolicy.Action.NONE,
            PlaybackIntentReconciliationPolicy.decide(
                false, false, LocalPlaybackParticipation.ACTIVE),
        )
    }

    @Test
    fun `play request on inhibited device rejoins live room regardless of stale local item`() {
        assertEquals(
            PlaybackIntentReconciliationPolicy.PlayRequestAction.REJOIN_LIVE_ROOM,
            PlaybackIntentReconciliationPolicy.decidePlayRequest(
                canonicalPlaying = true,
                participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
            ),
        )
    }

    @Test
    fun `play request mutates canonical room in every non rejoin state`() {
        listOf(
            PlaybackIntentReconciliationPolicy.decidePlayRequest(
                false, LocalPlaybackParticipation.OUTPUT_INHIBITED),
            PlaybackIntentReconciliationPolicy.decidePlayRequest(
                true, LocalPlaybackParticipation.ACTIVE),
            PlaybackIntentReconciliationPolicy.decidePlayRequest(
                true, LocalPlaybackParticipation.REJOINING),
        ).forEach {
            assertEquals(
                PlaybackIntentReconciliationPolicy.PlayRequestAction.MUTATE_CANONICAL_ROOM, it)
        }
    }
}
