package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.RoomOptions
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRequestPolicyTest {
    private val peer = PeerId("peer-000000000001")
    private val item =
        QueueItem.create(
            track =
                TrackDescriptor(TrackId("a".repeat(64)), 10, "audio/mpeg", 1_000, title = "Song"),
            addedBy = peer,
        )

    @Test
    fun `single connected source defers until its local track is prepared`() {
        val snapshot = snapshot(item, wait = true)
        assertTrue(PlaybackRequestPolicy.requiresPreparationForPlay(snapshot, emptySet()))
    }

    @Test
    fun preparedCurrentTrackCanPlay() {
        val snapshot = snapshot(item, wait = true)
        assertFalse(PlaybackRequestPolicy.requiresPreparationForPlay(snapshot, setOf(item.queueItemId)))
    }

    @Test
    fun roomOptionCannotBypassMediaReadiness() {
        val snapshot = snapshot(item, wait = false)
        assertTrue(PlaybackRequestPolicy.requiresPreparationForPlay(snapshot, emptySet()))
    }

    @Test
    fun emptyQueueIsHandledByTheReducerNotDeferred() {
        val snapshot =
            RoomSnapshot(
                roomId = "room",
                roomName = "Room",
                term = CoordinatorTerm(1, peer),
                sequence = 0,
                members = listOf(MemberSnapshot(peer, "Friend")),
            )
        assertFalse(PlaybackRequestPolicy.requiresPreparationForPlay(snapshot))
    }

    private fun snapshot(item: QueueItem, wait: Boolean): RoomSnapshot =
        RoomSnapshot(
            roomId = "room",
            roomName = "Room",
            term = CoordinatorTerm(1, peer),
            sequence = 0,
            members = listOf(MemberSnapshot(peer, "Friend")),
            queue = listOf(item),
            playback = CanonicalPlaybackState(queueItemId = item.queueItemId),
            options = RoomOptions(waitAtTrackBoundary = wait),
        )
}
