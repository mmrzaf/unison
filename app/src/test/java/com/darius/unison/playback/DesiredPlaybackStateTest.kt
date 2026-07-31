package com.darius.unison.playback

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesiredPlaybackStateTest {
    @Test
    fun unrelatedMembershipSequenceDoesNotChangeContentRevision() {
        val first = snapshot(sequence = 10)
        val second = first.copy(sequence = 11)

        val firstDesired = DesiredPlaybackState.from(first)
        val secondDesired = DesiredPlaybackState.from(second)

        assertEquals(10L, firstDesired.canonicalSequence)
        assertEquals(11L, secondDesired.canonicalSequence)
        assertEquals(firstDesired.contentRevision, secondDesired.contentRevision)
    }

    @Test
    fun queuePlaybackAndOptionsChangeContentRevision() {
        val base = snapshot()
        val revisions =
            listOf(
                DesiredPlaybackState.from(base).contentRevision,
                DesiredPlaybackState.from(base.copy(queue = base.queue.reversed())).contentRevision,
                DesiredPlaybackState.from(
                        base.copy(
                            playback = base.playback.copy(isPlaying = !base.playback.isPlaying)
                        )
                    )
                    .contentRevision,
                DesiredPlaybackState.from(
                        base.copy(options = base.options.copy(waitAtTrackBoundary = false))
                    )
                    .contentRevision,
            )

        assertEquals(revisions.size, revisions.distinct().size)
    }

    @Test
    fun desiredStateContainsReconstructibleCanonicalIntent() {
        val snapshot = snapshot()
        val desired = DesiredPlaybackState.from(snapshot)

        assertEquals(snapshot.queue.map { it.queueItemId }, desired.queueItemIds)
        assertEquals(snapshot.playback.queueItemId, desired.currentQueueItemId)
        assertEquals(snapshot.playback.isPlaying, desired.playWhenReady)
        assertEquals(snapshot.repeatMode, desired.repeatMode)
        assertTrue(desired.preparedQueueItemIds.contains(snapshot.queue.first().queueItemId))
    }

    private fun snapshot(sequence: Long = 1L): RoomSnapshot {
        val peer = PeerId("peer")
        val first = queueItem("one", peer)
        val second = queueItem("two", peer)
        return RoomSnapshot(
            roomId = "room",
            roomName = "Room",
            term = CoordinatorTerm(1, peer),
            sequence = sequence,
            queue = listOf(first, second),
            preparedQueueItemIds = setOf(first.queueItemId),
            playback =
                CanonicalPlaybackState(
                    queueItemId = first.queueItemId,
                    positionAtTimestampMs = 1_500,
                    coordinatorTimestampNs = 5_000_000_000,
                    isPlaying = true,
                ),
        )
    }

    private fun queueItem(value: String, peer: PeerId): QueueItem =
        QueueItem(
            queueItemId = QueueItemId(value),
            track = TrackDescriptor(TrackId("track-$value"), sizeBytes = 100),
            addedByPeerId = peer,
            addedAtSequence = 1,
        )
}
