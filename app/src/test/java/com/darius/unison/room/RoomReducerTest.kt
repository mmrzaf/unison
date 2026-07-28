package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.RepeatMode
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
            members = listOf(MemberSnapshot(peer, "A"), MemberSnapshot(guest, "B")),
            preparedQueueItemIds = setOf(item.queueItemId),
        )
        val result = RoomReducer.decide(room, UserCommand.Play(requestedBy = guest), 0)
        assertTrue(result is RoomReducer.Decision.Accepted)
    }

    @Test
    fun roomOptionsContainOnlyEnforcedBehavior() {
        val item = QueueItem.create(track, peer)
        val requested = RoomOptions(waitAtTrackBoundary = false, preloadCount = 50)
        val result = RoomReducer.decide(
            snapshot(listOf(item)),
            UserCommand.OptionsChange(requestedBy = peer, options = requested),
            0,
        ) as RoomReducer.Decision.Accepted
        assertFalse(result.mutations.single().snapshot.options.waitAtTrackBoundary)
        assertEquals(10, result.mutations.single().snapshot.options.preloadCount)
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

    @Test
    fun shuffleKeepsCurrentTrackAndIsDeterministic() {
        val queue = (0 until 8).map { index ->
            QueueItem.create(
                track.copy(
                    trackId = TrackId(index.toString(16).padStart(64, '0')),
                    title = "Track $index",
                ),
                peer,
                index.toLong(),
            )
        }
        val room = snapshot(queue).copy(
            playback = CanonicalPlaybackState(queue[2].queueItemId, 0, 1, true),
        )
        val command = UserCommand.PlaybackModeChange(
            requestedBy = peer,
            shuffleEnabled = true,
            repeatMode = RepeatMode.OFF,
            shuffleSeed = 42L,
        )
        val firstResult = RoomReducer.decide(room, command, 2) as RoomReducer.Decision.Accepted
        val secondResult =
            RoomReducer.decide(room, command.copy(commandId = "other"), 2) as RoomReducer.Decision.Accepted
        val firstQueue = firstResult.mutations.single().snapshot.queue
        val secondQueue = secondResult.mutations.single().snapshot.queue
        assertEquals(queue.take(3).map { it.queueItemId }, firstQueue.take(3).map { it.queueItemId })
        assertEquals(firstQueue.map { it.queueItemId }, secondQueue.map { it.queueItemId })
        assertTrue(firstResult.mutations.single().snapshot.shuffleEnabled)
    }

    @Test
    fun oneShotQueueShuffleReordersWithoutLeavingAModeEnabled() {
        val queue = (0 until 8).map { index ->
            QueueItem.create(
                track.copy(
                    trackId = TrackId(index.toString(16).padStart(64, '0')),
                    title = "Track $index",
                ),
                peer,
                index.toLong(),
            )
        }
        val room = snapshot(queue).copy(
            playback = CanonicalPlaybackState(queue[2].queueItemId, 0, 1, true),
            repeatMode = RepeatMode.ALL,
        )
        val result = RoomReducer.decide(
            room,
            UserCommand.QueueShuffle(requestedBy = peer, shuffleSeed = 42L),
            2,
        ) as RoomReducer.Decision.Accepted
        val shuffled = result.mutations.single().snapshot

        assertEquals(queue.take(3).map { it.queueItemId }, shuffled.queue.take(3).map { it.queueItemId })
        assertFalse(shuffled.shuffleEnabled)
        assertEquals(RepeatMode.ALL, shuffled.repeatMode)
        assertTrue(shuffled.queue.drop(3).map { it.queueItemId } != queue.drop(3).map { it.queueItemId })
    }

    @Test
    fun disablingShuffleRestoresOriginalOrder() {
        val queue = (0 until 6).map { index ->
            QueueItem.create(
                track.copy(trackId = TrackId(index.toString(16).padStart(64, '0')), title = "Track $index"),
                peer,
                index.toLong(),
            )
        }
        val enabled = RoomReducer.decide(
            snapshot(queue),
            UserCommand.PlaybackModeChange(
                requestedBy = peer,
                shuffleEnabled = true,
                repeatMode = RepeatMode.ALL,
                shuffleSeed = 7L,
            ),
            2,
        ) as RoomReducer.Decision.Accepted
        val shuffled = enabled.mutations.single().snapshot
        val disabled = RoomReducer.decide(
            shuffled,
            UserCommand.PlaybackModeChange(
                requestedBy = peer,
                shuffleEnabled = false,
                repeatMode = RepeatMode.ALL,
                shuffleSeed = 9L,
            ),
            3,
        ) as RoomReducer.Decision.Accepted
        assertEquals(queue.map { it.queueItemId }, disabled.mutations.single().snapshot.queue.map { it.queueItemId })
    }

    @Test
    fun queueCannotBeReorderedWhileShuffleIsOn() {
        val item = QueueItem.create(track, peer)
        val room = snapshot(listOf(item)).copy(shuffleEnabled = true)
        val result = RoomReducer.decide(
            room,
            UserCommand.QueueMove(requestedBy = peer, queueItemId = item.queueItemId, newIndex = 0),
            0,
        )
        assertTrue(result is RoomReducer.Decision.Rejected)
    }

    @Test
    fun playNextInsertsAfterCurrentWithoutChangingCurrent() {
        val first = QueueItem.create(track, peer)
        val second = QueueItem.create(
            track.copy(trackId = TrackId("b".repeat(64)), title = "Second"),
            peer,
        )
        val nextTrack = track.copy(trackId = TrackId("c".repeat(64)), title = "Next")
        val room = snapshot(listOf(first, second)).copy(
            playback = CanonicalPlaybackState(first.queueItemId, 10_000, 1, true),
        )
        val result = RoomReducer.decide(
            room,
            UserCommand.QueueAdd(
                requestedBy = peer,
                tracks = listOf(nextTrack),
                insertAfterCurrent = true,
            ),
            2,
        ) as RoomReducer.Decision.Accepted
        val updated = result.mutations.single().snapshot
        assertEquals(
            listOf(first.track.trackId, nextTrack.trackId, second.track.trackId),
            updated.queue.map { it.track.trackId })
        assertEquals(first.queueItemId, updated.playback.queueItemId)
    }

    @Test
    fun selectingQueueItemStartsItWithoutReorderingQueue() {
        val queue = (0 until 3).map { index ->
            QueueItem.create(
                track.copy(
                    trackId = TrackId(index.toString(16).padStart(64, '0')),
                    title = "Track $index",
                ),
                peer,
                index.toLong(),
            )
        }
        val room = snapshot(queue).copy(
            playback = CanonicalPlaybackState(queue.first().queueItemId, 8_000, 1, true),
            preparedQueueItemIds = queue.mapTo(mutableSetOf()) { it.queueItemId },
        )
        val result = RoomReducer.decide(
            room,
            UserCommand.PlayQueueItem(requestedBy = peer, queueItemId = queue.last().queueItemId),
            2,
        ) as RoomReducer.Decision.Accepted
        val mutation = result.mutations.single()
        val body = mutation.body as ProtocolBody.CurrentItemChanged

        assertEquals(queue.map { it.queueItemId }, mutation.snapshot.queue.map { it.queueItemId })
        assertEquals(queue.last().queueItemId, body.queueItemId)
        assertEquals(0, body.positionMs)
        assertTrue(body.resumePlayback)
        assertEquals(queue.last().queueItemId, mutation.snapshot.playback.queueItemId)
    }

    @Test
    fun selectingUnpreparedQueueItemSwitchesThenWaitsForPreparation() {
        val first = QueueItem.create(track, peer)
        val target = QueueItem.create(
            track.copy(trackId = TrackId("b".repeat(64)), title = "Target"),
            peer,
        )
        val room = snapshot(listOf(first, target)).copy(
            playback = CanonicalPlaybackState(first.queueItemId, 8_000, 1, true),
            preparedQueueItemIds = setOf(first.queueItemId),
        )
        val result = RoomReducer.decide(
            room,
            UserCommand.PlayQueueItem(requestedBy = peer, queueItemId = target.queueItemId),
            2,
        ) as RoomReducer.Decision.Accepted
        val body = result.mutations.single().body as ProtocolBody.CurrentItemChanged

        assertEquals(target.queueItemId, body.queueItemId)
        assertFalse(body.resumePlayback)
        assertFalse(result.mutations.single().snapshot.playback.isPlaying)
    }

    @Test
    fun clearPlayedRemovesOnlyHistory() {
        val queue = (0 until 4).map { index ->
            QueueItem.create(
                track.copy(trackId = TrackId(index.toString(16).padStart(64, '0')), title = "Track $index"),
                peer,
                index.toLong(),
            )
        }
        val room = snapshot(queue).copy(
            playback = CanonicalPlaybackState(queue[2].queueItemId, 5_000, 1, true),
        )
        val result = RoomReducer.decide(
            room,
            UserCommand.QueueClearPlayed(requestedBy = peer),
            2,
        ) as RoomReducer.Decision.Accepted
        assertEquals(
            listOf(queue[2].queueItemId, queue[3].queueItemId),
            result.mutations.last().snapshot.queue.map { it.queueItemId })
        assertEquals(queue[2].queueItemId, result.mutations.last().snapshot.playback.queueItemId)
    }

    @Test
    fun queueCapacityIsBoundedAndBulkAddsAreTrimmed() {
        val queue = (0 until RoomReducer.MAX_QUEUE_ITEMS - 1).map { index ->
            QueueItem.create(
                track.copy(
                    trackId = TrackId(index.toString(16).padStart(64, '0')),
                    title = "Track $index",
                ),
                peer,
                index.toLong(),
            )
        }
        val additions = listOf('b', 'c', 'd').map { marker ->
            track.copy(trackId = TrackId(marker.toString().repeat(64)), title = "Added $marker")
        }
        val result = RoomReducer.decide(
            snapshot(queue),
            UserCommand.QueueAdd(requestedBy = peer, tracks = additions),
            0,
        ) as RoomReducer.Decision.Accepted

        assertEquals(1, result.mutations.size)
        assertEquals(RoomReducer.MAX_QUEUE_ITEMS, result.mutations.single().snapshot.queue.size)
        val full = RoomReducer.decide(
            result.mutations.single().snapshot,
            UserCommand.QueueAdd(requestedBy = peer, tracks = listOf(additions.first()), commandId = "full"),
            0,
        )
        assertTrue(full is RoomReducer.Decision.Rejected)
    }

    @Test
    fun addingWhileShuffledStillRestoresAStableOriginalOrder() {
        val queue = (0 until 5).map { index ->
            QueueItem.create(
                track.copy(trackId = TrackId(index.toString(16).padStart(64, '0')), title = "Track $index"),
                peer,
                index.toLong(),
            )
        }
        val shuffled = (RoomReducer.decide(
            snapshot(queue),
            UserCommand.PlaybackModeChange(
                requestedBy = peer,
                shuffleEnabled = true,
                repeatMode = RepeatMode.OFF,
                shuffleSeed = 11,
            ),
            0,
        ) as RoomReducer.Decision.Accepted).mutations.single().snapshot
        val addedTrack = track.copy(trackId = TrackId("f".repeat(64)), title = "Added")
        val withAdded = (RoomReducer.decide(
            shuffled,
            UserCommand.QueueAdd(requestedBy = peer, tracks = listOf(addedTrack), commandId = "add-shuffled"),
            1,
        ) as RoomReducer.Decision.Accepted).mutations.single().snapshot
        val restored = (RoomReducer.decide(
            withAdded,
            UserCommand.PlaybackModeChange(
                requestedBy = peer,
                shuffleEnabled = false,
                repeatMode = RepeatMode.OFF,
                shuffleSeed = 0,
                commandId = "restore",
            ),
            2,
        ) as RoomReducer.Decision.Accepted).mutations.single().snapshot

        assertEquals(queue.map { it.track.trackId } + addedTrack.trackId, restored.queue.map { it.track.trackId })
    }

    @Test
    fun oversizedBulkAddIsRejected() {
        val additions = List(RoomReducer.MAX_TRACKS_PER_COMMAND + 1) { index ->
            track.copy(trackId = TrackId(index.toString(16).padStart(64, '0')))
        }
        val result = RoomReducer.decide(
            snapshot(),
            UserCommand.QueueAdd(requestedBy = peer, tracks = additions),
            0,
        )
        assertTrue(result is RoomReducer.Decision.Rejected)
    }

    @Test
    fun malformedTrackDescriptorIsRejected() {
        val malformed = track.copy(trackId = TrackId("not-a-sha256"))
        val result = RoomReducer.decide(
            snapshot(),
            UserCommand.QueueAdd(requestedBy = peer, tracks = listOf(malformed)),
            0,
        )
        assertTrue(result is RoomReducer.Decision.Rejected)
    }
}
