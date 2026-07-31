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
        val snapshot = snapshot(item, prepared = false, wait = true)
        assertTrue(PlaybackRequestPolicy.shouldDeferPlay(snapshot))
    }

    @Test
    fun preparedCurrentTrackCanPlay() {
        val snapshot = snapshot(item, prepared = true, wait = true)
        assertFalse(PlaybackRequestPolicy.shouldDeferPlay(snapshot))
    }

    @Test
    fun nonBlockingRoomCanPlayBeforeGlobalPreparation() {
        val snapshot = snapshot(item, prepared = false, wait = false)
        assertFalse(PlaybackRequestPolicy.shouldDeferPlay(snapshot))
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
        assertFalse(PlaybackRequestPolicy.shouldDeferPlay(snapshot))
    }

    private fun snapshot(item: QueueItem, prepared: Boolean, wait: Boolean): RoomSnapshot =
        RoomSnapshot(
            roomId = "room",
            roomName = "Room",
            term = CoordinatorTerm(1, peer),
            sequence = 0,
            members = listOf(MemberSnapshot(peer, "Friend")),
            queue = listOf(item),
            preparedQueueItemIds = if (prepared) setOf(item.queueItemId) else emptySet(),
            playback = CanonicalPlaybackState(queueItemId = item.queueItemId),
            options = RoomOptions(waitAtTrackBoundary = wait),
        )
}
