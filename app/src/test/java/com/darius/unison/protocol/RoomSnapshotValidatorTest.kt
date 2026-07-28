package com.darius.unison.protocol

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSnapshotValidatorTest {
    private val validator = RoomSnapshotValidator(maxMembers = 8)
    private val coordinator = PeerId("11111111-1111-1111-1111-111111111111")
    private val guest = PeerId("22222222-2222-2222-2222-222222222222")
    private val itemId = QueueItemId("33333333-3333-3333-3333-333333333333")
    private val trackId = TrackId("a".repeat(64))

    private fun endpoint(peerId: PeerId, port: Int = 1234) = PeerEndpoint(
        peerId = peerId,
        displayName = "Device",
        hostAddress = "192.168.1.2",
        port = port,
        appVersion = "1.0",
    )

    private fun validSnapshot(): RoomSnapshot {
        val item = QueueItem(
            queueItemId = itemId,
            track = TrackDescriptor(trackId = trackId, sizeBytes = 1024, durationMs = 60_000, title = "Track"),
            addedByPeerId = coordinator,
            addedAtSequence = 1,
        )
        return RoomSnapshot(
            roomId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            roomName = "Room",
            term = CoordinatorTerm(1, coordinator),
            sequence = 1,
            members = listOf(
                MemberSnapshot(coordinator, "Coordinator", endpoint(coordinator)),
                MemberSnapshot(guest, "Guest", endpoint(guest)),
            ),
            queue = listOf(item),
            preparedQueueItemIds = setOf(itemId),
            playback = CanonicalPlaybackState(queueItemId = itemId),
        )
    }

    @Test
    fun acceptsValidSnapshotWithinBudget() {
        val result = validator.validate(
            validSnapshot(),
            SnapshotValidationContext(
                expectedRoomId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                expectedCoordinatorPeerId = coordinator,
                encodedSizeBytes = 10_000,
            ),
        )
        assertEquals(SnapshotValidationResult.Valid, result)
    }

    @Test
    fun rejectsDuplicateMembersAndQueueItems() {
        val base = validSnapshot()
        val invalid = base.copy(
            members = base.members + base.members.last(),
            queue = base.queue + base.queue.first(),
        )
        val result = validator.validate(invalid) as SnapshotValidationResult.Invalid
        assertTrue(result.issues.any { it.code == "duplicate_member" })
        assertTrue(result.issues.any { it.code == "duplicate_queue_item" })
    }

    @Test
    fun rejectsPlaybackAndPreparedReferencesOutsideQueue() {
        val missing = QueueItemId("44444444-4444-4444-4444-444444444444")
        val invalid = validSnapshot().copy(
            preparedQueueItemIds = setOf(missing),
            playback = CanonicalPlaybackState(queueItemId = missing),
        )
        val result = validator.validate(invalid) as SnapshotValidationResult.Invalid
        assertTrue(result.issues.any { it.code == "prepared_items" })
        assertTrue(result.issues.any { it.code == "playback_item" })
    }

    @Test
    fun rejectsMalformedTrackAndEndpoint() {
        val base = validSnapshot()
        val invalidItem = base.queue.first().copy(
            track = base.queue.first().track.copy(trackId = TrackId("not-a-hash"), sizeBytes = -1),
        )
        val invalidMember = base.members.last().copy(endpoint = endpoint(guest, port = 0))
        val invalid = base.copy(queue = listOf(invalidItem), members = listOf(base.members.first(), invalidMember))
        val result = validator.validate(invalid) as SnapshotValidationResult.Invalid
        assertTrue(result.issues.any { it.code == "track_id" })
        assertTrue(result.issues.any { it.code == "track_size" })
        assertTrue(result.issues.any { it.code == "endpoint_port" })
    }

    @Test
    fun rejectsOversizedPayloadAndOldTerm() {
        val result = validator.validate(
            validSnapshot(),
            SnapshotValidationContext(
                minimumTerm = 2,
                encodedSizeBytes = RoomSnapshotValidator.MAX_SNAPSHOT_PAYLOAD_BYTES + 1,
            ),
        ) as SnapshotValidationResult.Invalid
        assertTrue(result.issues.any { it.code == "old_term" })
        assertTrue(result.issues.any { it.code == "payload_budget" })
    }

    @Test
    fun rejectsInvalidShuffleMembership() {
        val invalid = validSnapshot().copy(shuffleEnabled = true, unshuffledQueueItemIds = emptyList())
        val result = validator.validate(invalid) as SnapshotValidationResult.Invalid
        assertTrue(result.issues.any { it.code == "shuffle_membership" })
    }
    @Test
    fun rejectsMissingCoordinatorAndOldSequence() {
        val base = validSnapshot()
        val invalid = base.copy(members = base.members.filterNot { it.peerId == coordinator })
        val result = validator.validate(
            invalid,
            SnapshotValidationContext(minimumTerm = 1, minimumSequence = 2),
        ) as SnapshotValidationResult.Invalid
        assertTrue(result.issues.any { it.code == "missing_coordinator" })
        assertTrue(result.issues.any { it.code == "old_sequence" })
    }

    @Test
    fun rejectsControlCharactersAndExcessMembers() {
        val base = validSnapshot()
        val extraMembers = (0 until 8).map { index ->
            val id = PeerId("peer-${index.toString().padStart(31, '0')}")
            MemberSnapshot(id, "Guest$index")
        }
        val invalid = base.copy(
            roomName = "Bad\u0000Room",
            members = base.members + extraMembers,
        )
        val result = validator.validate(invalid) as SnapshotValidationResult.Invalid
        assertTrue(result.issues.any { it.code == "room_name" })
        assertTrue(result.issues.any { it.code == "member_count" })
    }

}
