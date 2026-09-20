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

        assertEquals(PlayerEventInterpreter.Action.None, interpreter.observe(state, true))
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
            interpreter.observe(state, coordinator = true),
        )
        assertEquals(
            PlayerEventInterpreter.Action.None,
            interpreter.observe(state, coordinator = true),
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
            interpreter.observe(state, true),
        )
        assertEquals(PlayerEventInterpreter.Action.None, interpreter.observe(state, true))
    }

    @Test
    fun stateEndedThenExplicitBoundaryForSameCycleEmitsOnce() {
        val interpreter = PlayerEventInterpreter()
        val finalState =
            PlayerState(
                queueItemId = ended,
                positionMs = 10_000,
                durationMs = 10_000,
                ended = true,
                seekRevision = 2,
                itemTransitionRevision = 4,
            )

        assertEquals(
            PlayerEventInterpreter.Action.PlaybackEnded(ended, 10_000, 10_000),
            interpreter.observe(finalState, true),
        )
        val explicitBoundary =
            finalState.copy(
                itemBoundaryRevision = 1,
                boundaryEndedQueueItemId = ended,
                boundaryEndedPositionMs = 10_000,
                boundaryEndedDurationMs = 10_000,
            )
        assertEquals(
            PlayerEventInterpreter.Action.None,
            interpreter.observe(explicitBoundary, true),
        )
    }

    @Test
    fun replayOfSameQueueItemCanProduceANewEndCycle() {
        val interpreter = PlayerEventInterpreter()
        val firstEnd =
            PlayerState(
                queueItemId = ended,
                positionMs = 10_000,
                durationMs = 10_000,
                ended = true,
                seekRevision = 1,
                itemTransitionRevision = 1,
            )
        assertEquals(
            PlayerEventInterpreter.Action.PlaybackEnded(ended, 10_000, 10_000),
            interpreter.observe(firstEnd, true),
        )

        val replayEnd = firstEnd.copy(seekRevision = 2)
        assertEquals(
            PlayerEventInterpreter.Action.PlaybackEnded(ended, 10_000, 10_000),
            interpreter.observe(replayEnd, true),
        )
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

        assertEquals(PlayerEventInterpreter.Action.None, interpreter.observe(state, false))
        // Promotion to coordinator later must not replay a boundary already observed as
        // participant.
        assertEquals(PlayerEventInterpreter.Action.None, interpreter.observe(state, true))
    }
}
