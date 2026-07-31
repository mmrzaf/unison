package com.darius.unison.playback

import com.darius.unison.model.QueueItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackIntentReconciliationPolicyTest {
    @Test
    fun `canonical play resumes an ordinary local mismatch`() {
        assertEquals(
            PlaybackIntentReconciliationPolicy.Action.PLAY,
            PlaybackIntentReconciliationPolicy.decide(
                canonicalPlaying = true,
                localPlayWhenReady = false,
                locallySuppressed = false,
            ),
        )
    }

    @Test
    fun `canonical state sync never overrides a local audio safety pause`() {
        assertEquals(
            PlaybackIntentReconciliationPolicy.Action.NONE,
            PlaybackIntentReconciliationPolicy.decide(
                canonicalPlaying = true,
                localPlayWhenReady = false,
                locallySuppressed = true,
            ),
        )
    }

    @Test
    fun `canonical pause still pauses a locally playing phone`() {
        assertEquals(
            PlaybackIntentReconciliationPolicy.Action.PAUSE,
            PlaybackIntentReconciliationPolicy.decide(
                canonicalPlaying = false,
                localPlayWhenReady = true,
                locallySuppressed = false,
            ),
        )
    }

    @Test
    fun `matching transport intent does nothing`() {
        assertEquals(
            PlaybackIntentReconciliationPolicy.Action.NONE,
            PlaybackIntentReconciliationPolicy.decide(true, true, false),
        )
        assertEquals(
            PlaybackIntentReconciliationPolicy.Action.NONE,
            PlaybackIntentReconciliationPolicy.decide(false, false, false),
        )
    }

    @Test
    fun `play request resumes only the locally suppressed matching item`() {
        val item = QueueItemId("item")
        assertEquals(
            PlaybackIntentReconciliationPolicy.PlayRequestAction.RESUME_LOCAL_OUTPUT,
            PlaybackIntentReconciliationPolicy.decidePlayRequest(
                canonicalPlaying = true,
                canonicalQueueItemId = item,
                localQueueItemId = item,
                locallySuppressed = true,
            ),
        )
    }

    @Test
    fun `play request mutates canonical room for every other state`() {
        val item = QueueItemId("item")
        val cases =
            listOf(
                PlaybackIntentReconciliationPolicy.decidePlayRequest(false, item, item, true),
                PlaybackIntentReconciliationPolicy.decidePlayRequest(true, item, item, false),
                PlaybackIntentReconciliationPolicy.decidePlayRequest(
                    true,
                    item,
                    QueueItemId("other"),
                    true,
                ),
                PlaybackIntentReconciliationPolicy.decidePlayRequest(true, null, item, true),
            )
        cases.forEach {
            assertEquals(
                PlaybackIntentReconciliationPolicy.PlayRequestAction.MUTATE_CANONICAL_ROOM,
                it,
            )
        }
    }
}
