package com.darius.unison.room

import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.RoomMediaReadiness
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMediaReadinessPolicyTest {
    private val peer = PeerId("peer-000000000001")
    private val track = TrackDescriptor(TrackId("a".repeat(64)), 100, "audio/mpeg", 60_000)
    private val item = QueueItem.create(track, peer)
    private val snapshot =
        RoomSnapshot(
            roomId = "room",
            roomName = "Room",
            term = CoordinatorTerm(1, peer),
            sequence = 0,
            members = listOf(MemberSnapshot(peer, "Alex")),
            queue = listOf(item),
        )

    @Test
    fun readyRequiresRoomReadinessAndVerifiedLocalAvailability() {
        val readiness =
            RoomMediaReadinessPolicy.derive(
                snapshot,
                roomReadyQueueItemIds = setOf(item.queueItemId),
                explicitPreparationQueueItemIds = emptySet(),
                locallyAvailableTrackIds = setOf(track.trackId),
            )

        assertEquals(RoomMediaReadiness.READY, readiness[item.queueItemId])
        assertTrue(RoomMediaReadinessPolicy.canPlay(item.queueItemId, readiness))
    }

    @Test
    fun coordinatorReadinessCannotHideMissingLocalMedia() {
        val readiness =
            RoomMediaReadinessPolicy.derive(
                snapshot,
                roomReadyQueueItemIds = setOf(item.queueItemId),
                explicitPreparationQueueItemIds = emptySet(),
                locallyAvailableTrackIds = emptySet(),
            )

        assertEquals(RoomMediaReadiness.NEEDS_PREPARATION, readiness[item.queueItemId])
        assertFalse(RoomMediaReadinessPolicy.canPlay(item.queueItemId, readiness))
    }

    @Test
    fun explicitPrepareIntentIsVisibleWithoutChangingCanonicalState() {
        val before = snapshot
        val readiness =
            RoomMediaReadinessPolicy.derive(
                snapshot,
                roomReadyQueueItemIds = emptySet(),
                explicitPreparationQueueItemIds = setOf(item.queueItemId),
                locallyAvailableTrackIds = emptySet(),
            )

        assertEquals(RoomMediaReadiness.PREPARING, readiness[item.queueItemId])
        assertEquals(before, snapshot)
    }

    @Test
    fun backgroundPrefetchDoesNotPretendUserRequestedPreparation() {
        val readiness =
            RoomMediaReadinessPolicy.derive(
                snapshot,
                roomReadyQueueItemIds = emptySet(),
                explicitPreparationQueueItemIds = emptySet(),
                locallyAvailableTrackIds = emptySet(),
            )

        assertEquals(RoomMediaReadiness.NEEDS_PREPARATION, readiness[item.queueItemId])
    }
}
