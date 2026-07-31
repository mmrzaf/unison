package com.darius.unison.room

import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransportAction
import com.darius.unison.model.UserCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingTrackTransitionRegistryTest {
    private val peer = PeerId("peer")

    @Test
    fun newerNavigationConsumesOldPendingBaseAndReplacesOwnership() {
        val registry = PendingTrackTransitionRegistry()
        val first = pending("first", "item-a")
        registry.replace(first)

        val next = UserCommand.SkipNext(commandId = "next", requestedBy = peer)
        assertEquals(QueueItemId("item-a"), registry.relativeNavigationBase(next))

        val second = pending("next", "item-b")
        assertEquals(first, registry.replace(second))
        assertTrue(registry.matches("next", QueueItemId("item-b")))
        assertFalse(registry.matches("first"))
    }

    @Test
    fun immediateNavigationCanClearPriorPendingRequest() {
        val registry = PendingTrackTransitionRegistry()
        registry.replace(pending("old", "item-a"))

        val cleared = registry.clear()

        assertEquals("old", cleared?.commandId)
        assertNull(registry.peek())
    }

    @Test
    fun playAndPauseOnlyUpdateExplicitPendingIntentWhenRetained() {
        val registry = PendingTrackTransitionRegistry()
        registry.replace(pending("first", "item-a", resumePlayback = true))

        assertFalse(registry.updateResumePlayback(false)?.resumePlayback ?: true)
        assertTrue(registry.updateResumePlayback(true)?.resumePlayback == true)
    }

    @Test
    fun clearIfCommandCannotEraseReplacement() {
        val registry = PendingTrackTransitionRegistry()
        registry.replace(pending("new", "item-b"))

        assertNull(registry.clearIfCommand("old"))
        assertTrue(registry.matches("new"))
        assertEquals("new", registry.clearIfCommand("new")?.commandId)
        assertNull(registry.peek())
    }

    @Test
    fun duplicateTargetKeepsOriginalOwnerAndDeadlineIdentity() {
        val registry = PendingTrackTransitionRegistry()
        val original = pending("original", "item-a")
        registry.replace(original)

        assertEquals(original, registry.activeForTarget(QueueItemId("item-a")))
        assertEquals("original", registry.peek()?.commandId)
    }

    private fun pending(
        commandId: String,
        itemId: String,
        resumePlayback: Boolean = true,
    ) =
        PendingTrackTransition(
            commandId = commandId,
            action = TransportAction.NEXT,
            requestedBy = peer,
            queueItemId = QueueItemId(itemId),
            trackId = TrackId("track-$itemId"),
            resumePlayback = resumePlayback,
        )
}
