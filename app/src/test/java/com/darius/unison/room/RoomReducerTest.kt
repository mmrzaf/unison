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
import com.darius.unison.model.UserCommand
import com.darius.unison.protocol.ProtocolBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomReducerTest {
    private val peer = PeerId("peer-a")
    private val track = TrackDescriptor(TrackId("a".repeat(64)), 1024, durationMs = 180_000, title = "Song")

    private fun snapshot(queue: List<QueueItem> = emptyList()) = RoomSnapshot(
        roomId = "room",
        roomName = "Test",
        term = CoordinatorTerm(1, peer),
        sequence = 0,
        members = listOf(MemberSnapshot(peer, "A")),
        queue = queue,
    )

    @Test
    fun addTrackCreatesCanonicalQueueItem() {
        val result =
            RoomReducer.decide(snapshot(), UserCommand.QueueAdd(requestedBy = peer, tracks = listOf(track)), 1_000)
        assertTrue(result is RoomReducer.Decision.Accepted)
        val mutation = (result as RoomReducer.Decision.Accepted).mutations.single()
        assertEquals(1, mutation.sequence)
        assertEquals(track.trackId, mutation.snapshot.queue.single().track.trackId)
        assertTrue(mutation.body is ProtocolBody.QueueItemAdded)
    }

    @Test
    fun playIsScheduledInFuture() {
        val item = QueueItem.create(track, peer)
        val now = 5_000_000_000L
        val ready = snapshot(listOf(item)).copy(preparedQueueItemIds = setOf(item.queueItemId))
        val result = RoomReducer.decide(ready, UserCommand.Play(requestedBy = peer), now)
            as RoomReducer.Decision.Accepted
        val body = result.mutations.single().body as ProtocolBody.PlayScheduled
        assertEquals(item.queueItemId, body.queueItemId)
        assertTrue(body.executeAtCoordinatorNs > now)
        assertTrue(result.mutations.single().snapshot.playback.isPlaying)
    }

    @Test
    fun oldCanonicalMessagesAreIgnored() {
        val item = QueueItem.create(track, peer)
        val initial = snapshot(listOf(item)).copy(sequence = 5)
        val body = ProtocolBody.QueueItemRemoved(item.queueItemId)
        assertEquals(initial, RoomReducer.applyCanonical(initial, 4, body))
    }

    @Test
    fun everyConnectedPeerCanControlPlayback() {
        val guest = PeerId("peer-b")
        val item = QueueItem.create(track, peer)
        val room = snapshot(listOf(item)).copy(
            options = RoomOptions(everyoneCanControl = false),
            members = listOf(MemberSnapshot(peer, "A"), MemberSnapshot(guest, "B")),
            preparedQueueItemIds = setOf(item.queueItemId),
        )
        val result = RoomReducer.decide(room, UserCommand.Play(requestedBy = guest), 0)
        assertTrue(result is RoomReducer.Decision.Accepted)
    }

    @Test
    fun optionsCannotCreateAnAdminOnlyRoom() {
        val item = QueueItem.create(track, peer)
        val requested = RoomOptions(everyoneCanAdd = false, everyoneCanControl = false)
        val result = RoomReducer.decide(
            snapshot(listOf(item)),
            UserCommand.OptionsChange(requestedBy = peer, options = requested),
            0,
        ) as RoomReducer.Decision.Accepted
        assertTrue(result.mutations.single().snapshot.options.everyoneCanAdd)
        assertTrue(result.mutations.single().snapshot.options.everyoneCanControl)
    }

    @Test
    fun removingCurrentTrackSelectsNextAndKeepsPlaybackIntent() {
        val first = QueueItem.create(track, peer)
        val secondTrack = track.copy(trackId = TrackId("b".repeat(64)), title = "Second")
        val second = QueueItem.create(secondTrack, peer)
        val room = snapshot(listOf(first, second)).copy(
            playback = CanonicalPlaybackState(first.queueItemId, 30_000, 1_000, isPlaying = true),
            preparedQueueItemIds = setOf(first.queueItemId, second.queueItemId),
        )
        val result = RoomReducer.decide(
            room,
            UserCommand.QueueRemove(requestedBy = peer, queueItemId = first.queueItemId),
            coordinatorNowNs = 5_000_000_000L,
        ) as RoomReducer.Decision.Accepted

        assertEquals(2, result.mutations.size)
        val removalOnly = result.mutations.first().snapshot
        assertEquals(first.queueItemId, removalOnly.playback.queueItemId)
        assertTrue(removalOnly.playback.isPlaying)
        assertFalse(removalOnly.queue.any { it.queueItemId == first.queueItemId })

        val final = result.mutations.last().snapshot
        assertEquals(second.queueItemId, final.playback.queueItemId)
        assertTrue(final.playback.isPlaying)
        assertTrue(final.playback.coordinatorTimestampNs > 5_000_000_000L)
    }


    @Test
    fun overlappingPlaybackCommandsAreRejectedUntilScheduledTime() {
        val item = QueueItem.create(track, peer)
        val room = snapshot(listOf(item)).copy(
            preparedQueueItemIds = setOf(item.queueItemId),
            playback = CanonicalPlaybackState(
                queueItemId = item.queueItemId,
                positionAtTimestampMs = 0,
                coordinatorTimestampNs = 2_000_000_000L,
                isPlaying = true,
            ),
        )

        val early = RoomReducer.decide(room, UserCommand.Pause(requestedBy = peer), 1_000_000_000L)
        assertTrue(early is RoomReducer.Decision.Rejected)

        val afterExecution = RoomReducer.decide(room, UserCommand.Pause(requestedBy = peer), 2_000_000_001L)
        assertTrue(afterExecution is RoomReducer.Decision.Accepted)
    }

    @Test
    fun queueEditsRemainAvailableDuringScheduledPlaybackChange() {
        val item = QueueItem.create(track, peer)
        val room = snapshot(listOf(item)).copy(
            playback = CanonicalPlaybackState(
                queueItemId = item.queueItemId,
                positionAtTimestampMs = 0,
                coordinatorTimestampNs = 2_000_000_000L,
                isPlaying = true,
            ),
        )
        val another = track.copy(trackId = TrackId("c".repeat(64)), title = "Another")

        val result = RoomReducer.decide(
            room,
            UserCommand.QueueAdd(requestedBy = peer, tracks = listOf(another)),
            1_000_000_000L,
        )
        assertTrue(result is RoomReducer.Decision.Accepted)
    }


}
