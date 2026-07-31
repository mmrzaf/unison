package com.darius.unison.playback

import com.darius.unison.model.QueueItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerEventInterpreterTest {
    private val item = QueueItemId("item")

    @Test
    fun programmaticTransitionsRemainObservationsOnly() {
        val interpreter = PlayerEventInterpreter()

        val action =
            interpreter.observe(
                state = state(PlayerItemTransitionReason.SEEK, revision = 1),
                coordinator = true,
                nowNs = 0,
            )

        assertEquals(PlayerEventInterpreter.Action.None, action)
    }

    @Test
    fun automaticAndRepeatTransitionsBecomeExplicitDecisions() {
        val interpreter = PlayerEventInterpreter()
        val advance =
            interpreter.observe(
                state = state(PlayerItemTransitionReason.AUTO, revision = 1),
                coordinator = true,
                nowNs = 0,
            )
        val repeat =
            interpreter.observe(
                state = state(PlayerItemTransitionReason.REPEAT, revision = 2),
                coordinator = true,
                nowNs = 100,
            )

        assertEquals(PlayerEventInterpreter.Action.NaturalAdvance(item, 1_000), advance)
        assertEquals(PlayerEventInterpreter.Action.NaturalRepeat(item, 1_000), repeat)
    }

    @Test
    fun endedEventIsHandledOnceUntilStateChanges() {
        val interpreter = PlayerEventInterpreter()
        val ended = state(PlayerItemTransitionReason.UNKNOWN, revision = 0).copy(ended = true)

        val first = interpreter.observe(ended, coordinator = true, nowNs = 0)
        val second = interpreter.observe(ended, coordinator = true, nowNs = 1)

        assertEquals(PlayerEventInterpreter.Action.PlaybackEnded(item, 1_000, 10_000), first)
        assertEquals(PlayerEventInterpreter.Action.None, second)
    }

    @Test
    fun automaticTransitionBurstTripsAndResetRestoresNormalBehavior() {
        val interpreter =
            PlayerEventInterpreter(
                PlayerTransitionCircuitBreaker(
                    maxTransitions = 2,
                    windowNs = 1_000,
                    cooldownNs = 5_000,
                )
            )

        assertEquals(
            PlayerEventInterpreter.Action.NaturalAdvance(item, 1_000),
            interpreter.observe(state(PlayerItemTransitionReason.AUTO, 1), true, 0),
        )
        assertEquals(
            PlayerEventInterpreter.Action.TransitionLoopDetected,
            interpreter.observe(state(PlayerItemTransitionReason.AUTO, 2), true, 100),
        )

        interpreter.reset(PlayerState())
        assertEquals(
            PlayerEventInterpreter.Action.NaturalAdvance(item, 1_000),
            interpreter.observe(state(PlayerItemTransitionReason.AUTO, 1), true, 200),
        )
    }

    @Test
    fun participantNeverAuthorsCanonicalTransition() {
        val interpreter = PlayerEventInterpreter()
        val action =
            interpreter.observe(
                state(PlayerItemTransitionReason.AUTO, 1),
                coordinator = false,
                nowNs = 0,
            )

        assertEquals(PlayerEventInterpreter.Action.None, action)
    }

    private fun state(reason: PlayerItemTransitionReason, revision: Long): PlayerState =
        PlayerState(
            queueItemId = item,
            positionMs = 1_000,
            durationMs = 10_000,
            playWhenReady = true,
            itemTransitionRevision = revision,
            itemTransitionReason = reason,
        )
}
