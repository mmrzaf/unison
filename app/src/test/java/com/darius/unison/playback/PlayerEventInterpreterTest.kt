package com.darius.unison.playback

import com.darius.unison.model.QueueItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerEventInterpreterTest {
    private val ended = QueueItemId("ended")
    private val selected = QueueItemId("selected")

    @Test
    fun programmaticItemTransitionNeverAuthorsCanonicalProgression() {
        val interpreter = PlayerEventInterpreter()
        val state =
            PlayerState(
                queueItemId = selected,
                itemTransitionRevision = 1,
                itemTransitionReason = PlayerItemTransitionReason.SEEK,
            )

        assertEquals(PlayerEventInterpreter.Action.None, interpreter.observe(state, true, 0))
    }

    @Test
    fun naturalBoundaryReportsTheItemThatEndedNotMedia3sNewSelection() {
        val interpreter = PlayerEventInterpreter()
        val state =
            PlayerState(
                queueItemId = selected,
                positionMs = 0,
                itemTransitionRevision = 1,
                itemTransitionReason = PlayerItemTransitionReason.AUTO,
                itemBoundaryRevision = 1,
                boundaryEndedQueueItemId = ended,
                boundaryEndedPositionMs = 10_000,
                boundaryEndedDurationMs = 10_000,
            )

        assertEquals(
            PlayerEventInterpreter.Action.PlaybackEnded(ended, 10_000, 10_000),
            interpreter.observe(state, coordinator = true, nowNs = 0),
        )
        assertEquals(
            PlayerEventInterpreter.Action.None,
            interpreter.observe(state, coordinator = true, nowNs = 1),
        )
    }

    @Test
    fun finalPlaylistEndIsHandledOnce() {
        val interpreter = PlayerEventInterpreter()
        val state =
            PlayerState(
                queueItemId = ended,
                positionMs = 10_000,
                durationMs = 10_000,
                ended = true,
            )

        assertEquals(
            PlayerEventInterpreter.Action.PlaybackEnded(ended, 10_000, 10_000),
            interpreter.observe(state, true, 0),
        )
        assertEquals(PlayerEventInterpreter.Action.None, interpreter.observe(state, true, 1))
    }

    @Test
    fun participantConsumesBoundaryButNeverAuthorsRoomTransition() {
        val interpreter = PlayerEventInterpreter()
        val state =
            PlayerState(
                queueItemId = selected,
                itemBoundaryRevision = 1,
                boundaryEndedQueueItemId = ended,
                boundaryEndedPositionMs = 10_000,
                boundaryEndedDurationMs = 10_000,
            )

        assertEquals(PlayerEventInterpreter.Action.None, interpreter.observe(state, false, 0))
        // Promotion to coordinator later must not replay a boundary already observed as participant.
        assertEquals(PlayerEventInterpreter.Action.None, interpreter.observe(state, true, 1))
    }
}
