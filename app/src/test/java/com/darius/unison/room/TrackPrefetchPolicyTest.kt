package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPrefetchPolicyTest {
    private val peer = PeerId("11111111-1111-1111-1111-111111111111")
    private val queue =
        (0 until 8).map { index ->
            QueueItem.create(
                TrackDescriptor(
                    trackId = TrackId(index.toString(16).padStart(64, '0')),
                    sizeBytes = 1_000,
                    title = "Track $index",
                ),
                peer,
            )
        }
    private val snapshot =
        RoomSnapshot(
            roomId = "room",
            roomName = "Room",
            term = CoordinatorTerm(1, peer),
            sequence = 1,
            members = listOf(MemberSnapshot(peer, "Device")),
            queue = queue,
            playback = CanonicalPlaybackState(queue[2].queueItemId, 0, 1, true),
        )

    @Test
    fun desiredWindowContainsCurrentAndThreeUpcomingUniqueTracks() {
        assertEquals(
            queue.subList(2, 6).map { it.track.trackId },
            TrackPrefetchPolicy.desiredItems(snapshot).map { it.track.trackId },
        )
    }

    @Test
    fun explicitPendingTargetIsPrefetchedBeforeRegularWindow() {
        val desired =
            TrackPrefetchPolicy.prioritizedDesiredItems(
                snapshot = snapshot,
                priorityQueueItemId = queue[7].queueItemId,
            )

        assertEquals(queue[7].track.trackId, desired.first().track.trackId)
        assertEquals(
            queue.subList(2, 6).map { it.track.trackId }.toSet() + queue[7].track.trackId,
            desired.map { it.track.trackId }.toSet(),
        )
    }

    @Test
    fun explicitPendingTargetDoesNotDuplicateTrackAlreadyInWindow() {
        val desired =
            TrackPrefetchPolicy.prioritizedDesiredItems(
                snapshot = snapshot,
                priorityQueueItemId = queue[4].queueItemId,
            )

        assertEquals(queue[4].track.trackId, desired.first().track.trackId)
        assertEquals(desired.map { it.track.trackId }.distinct(), desired.map { it.track.trackId })
    }

    @Test
    fun obsoleteNearCompleteTransferIsAllowedToFinish() {
        val obsolete = queue[0].track.trackId
        val slow = queue[1].track.trackId
        val progress =
            mapOf(
                obsolete to
                    TransferProgress(obsolete, 900, 1_000, peer, peer, MemberTrackState.RECEIVING),
                slow to TransferProgress(slow, 200, 1_000, peer, peer, MemberTrackState.RECEIVING),
            )
        val cancellable =
            TrackPrefetchPolicy.cancellableObsoleteTracks(
                previousDesired = setOf(obsolete, slow),
                nextDesired = emptySet(),
                progressByTrack = progress,
            )
        assertFalse(obsolete in cancellable)
        assertTrue(slow in cancellable)
    }
}
