package com.darius.unison.room

import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.TransportAction
import com.darius.unison.model.TransportCommandPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportCommandTrackerTest {
    private val peer = PeerId("peer-000000000001")

    @Test
    fun resolvedTargetIsRetainedForLaterLifecyclePhases() {
        val tracker = TransportCommandTracker()
        tracker.remember(
            "next",
            TransportCommandTracker.Route(peer, TransportAction.NEXT),
        )
        val target = QueueItemId("target")

        tracker.updateTarget("next", queueItemId = target)

        assertEquals(target, tracker.route("next")?.queueItemId)
    }

    @Test
    fun terminalCompletionRemovesRoute() {
        val tracker = TransportCommandTracker()
        tracker.remember(
            "play",
            TransportCommandTracker.Route(peer, TransportAction.PLAY),
        )

        assertEquals(TransportAction.PLAY, tracker.complete("play")?.action)
        assertNull(tracker.route("play"))
    }

    @Test
    fun completedTombstonesRemainBoundedDuringLongRooms() {
        val tracker = TransportCommandTracker(maxEntries = 3)
        repeat(5) { index ->
            tracker.remember(
                "command-$index",
                TransportCommandTracker.Route(peer, TransportAction.SEEK),
            )
            tracker.transition("command-$index", TransportCommandPhase.SETTLED)
        }

        assertEquals(0, tracker.size)
        assertEquals(3, tracker.completedSize)
        assertNull(tracker.route("command-4"))
    }

    @Test
    fun acceptedCannotBePublishedTwiceOrAfterScheduling() {
        val tracker = TransportCommandTracker()
        tracker.remember("play", TransportCommandTracker.Route(peer, TransportAction.PLAY))

        assertTrue(
            tracker.transition("play", TransportCommandPhase.ACCEPTED)
                is TransportCommandTracker.Transition.Applied
        )
        assertEquals(
            TransportCommandTracker.Transition.Duplicate,
            tracker.transition("play", TransportCommandPhase.ACCEPTED),
        )
        assertTrue(
            tracker.transition("play", TransportCommandPhase.SCHEDULED)
                is TransportCommandTracker.Transition.Applied
        )
        assertEquals(
            TransportCommandTracker.Transition.Invalid,
            tracker.transition("play", TransportCommandPhase.ACCEPTED),
        )
    }

    @Test
    fun exactlyOneTerminalTransitionIsApplied() {
        val tracker = TransportCommandTracker()
        tracker.remember("play", TransportCommandTracker.Route(peer, TransportAction.PLAY))
        tracker.transition("play", TransportCommandPhase.ACCEPTED)

        assertTrue(
            tracker.transition("play", TransportCommandPhase.SETTLED)
                is TransportCommandTracker.Transition.Applied
        )
        assertEquals(
            TransportCommandTracker.Transition.AlreadyTerminal,
            tracker.transition("play", TransportCommandPhase.REJECTED),
        )
    }

    @Test
    fun activeCommandsAreNeverSilentlyEvicted() {
        val tracker = TransportCommandTracker(maxEntries = 1)
        repeat(3) { index ->
            tracker.remember(
                "command-$index",
                TransportCommandTracker.Route(peer, TransportAction.SEEK),
            )
        }

        assertEquals(3, tracker.size)
        assertEquals(TransportAction.SEEK, tracker.route("command-0")?.action)
    }
}
