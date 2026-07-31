package com.darius.unison.playback

import com.darius.unison.model.QueueItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerItemTransitionPolicyTest {
    private val item = QueueItemId("item")

    @Test
    fun `automatic transition creates one natural advance`() {
        val state = transition(PlayerItemTransitionReason.AUTO, revision = 1)

        val first = PlayerItemTransitionPolicy.evaluate(0, state)
        val duplicate = PlayerItemTransitionPolicy.evaluate(first.handledRevision, state)

        assertEquals(PlayerItemTransitionPolicy.Action.NATURAL_ADVANCE, first.action)
        assertEquals(PlayerItemTransitionPolicy.Action.NONE, duplicate.action)
    }

    @Test
    fun `repeat transition creates natural repeat`() {
        val decision =
            PlayerItemTransitionPolicy.evaluate(0, transition(PlayerItemTransitionReason.REPEAT, 1))

        assertEquals(PlayerItemTransitionPolicy.Action.NATURAL_REPEAT, decision.action)
    }

    @Test
    fun `programmatic seek never creates canonical transition`() {
        val decision =
            PlayerItemTransitionPolicy.evaluate(0, transition(PlayerItemTransitionReason.SEEK, 1))

        assertEquals(PlayerItemTransitionPolicy.Action.NONE, decision.action)
        assertEquals(1L, decision.handledRevision)
    }

    @Test
    fun `playlist reconciliation never creates canonical transition`() {
        val decision =
            PlayerItemTransitionPolicy.evaluate(
                0,
                transition(PlayerItemTransitionReason.PLAYLIST_CHANGED, 1),
            )

        assertEquals(PlayerItemTransitionPolicy.Action.NONE, decision.action)
        assertEquals(1L, decision.handledRevision)
    }

    @Test
    fun `unknown transition is consumed but ignored`() {
        val decision =
            PlayerItemTransitionPolicy.evaluate(
                0,
                transition(PlayerItemTransitionReason.UNKNOWN, 4),
            )

        assertEquals(PlayerItemTransitionPolicy.Action.NONE, decision.action)
        assertEquals(4L, decision.handledRevision)
    }

    @Test
    fun `programmatic reconciliation storm produces no canonical actions`() {
        var handledRevision = 0L
        val actions =
            (1L..200L).map { revision ->
                val reason =
                    if (revision % 2L == 0L) {
                        PlayerItemTransitionReason.SEEK
                    } else {
                        PlayerItemTransitionReason.PLAYLIST_CHANGED
                    }
                val decision =
                    PlayerItemTransitionPolicy.evaluate(
                        handledRevision,
                        transition(reason, revision),
                    )
                handledRevision = decision.handledRevision
                decision.action
            }

        assertEquals(setOf(PlayerItemTransitionPolicy.Action.NONE), actions.toSet())
        assertEquals(200L, handledRevision)
    }

    private fun transition(reason: PlayerItemTransitionReason, revision: Long) =
        PlayerState(
            queueItemId = item,
            playWhenReady = true,
            itemTransitionRevision = revision,
            itemTransitionReason = reason,
        )
}
