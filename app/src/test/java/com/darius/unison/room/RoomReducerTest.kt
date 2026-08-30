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
    private val track =
        TrackDescriptor(TrackId("a".repeat(64)), 1024, durationMs = 180_000, title = "Song")

    private fun snapshot(queue: List<QueueItem> = emptyList()) =
        RoomSnapshot(
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
            RoomReducer.decide(
                snapshot(),
                UserCommand.QueueAdd(requestedBy = peer, tracks = listOf(track)),
                1_000,
            )
        assertTrue(result is RoomReducer.Decision.Accepted)
        val mutation = (result as RoomReducer.Decision.Accepted).mutations.single()
        assertEquals(1, mutation.sequence)
        assertEquals(track.trackId, mutation.snapshot.queue.single().track.trackId)
        assertEquals(mutation.sequence, mutation.snapshot.queueRevision)
        assertEquals(mutation.sequence, mutation.snapshot.playback.revision)
        assertTrue(mutation.body is ProtocolBody.QueueItemsAdded)
        assertEquals(1, (mutation.body as ProtocolBody.QueueItemsAdded).items.size)
    }

    @Test
    fun playIsScheduledInFuture() {
        val item = QueueItem.create(track, peer)
        val now = 5_000_000_000L
        val ready = snapshot(listOf(item))
        val result =
            RoomReducer.decide(
                ready,
                UserCommand.Play(requestedBy = peer),
                now,
                preparedQueueItemIds = setOf(item.queueItemId),
            ) as RoomReducer.Decision.Accepted
        val body = result.mutations.single().body as ProtocolBody.PlayScheduled
        assertEquals(item.queueItemId, body.queueItemId)
        assertTrue(body.executeAtCoordinatorNs > now)
        assertTrue(result.mutations.single().snapshot.playback.isPlaying)
        assertEquals(
            result.mutations.single().sequence,
            result.mutations.single().snapshot.playback.revision,
        )
    }

    @Test
    fun terminalReplayOverrideRestartsCanonicalPlayAtZero() {
        val item = QueueItem.create(track, peer)
        val room =
            snapshot(listOf(item))
                .copy(
                    playback =
                        CanonicalPlaybackState(
                            queueItemId = item.queueItemId,
                            positionAtTimestampMs = track.durationMs,
                            coordinatorTimestampNs = 1_000,
                            isPlaying = false,
                            revision = 8,
                        )
                )

        val result =
            RoomReducer.decide(
                room,
                UserCommand.Play(requestedBy = peer),
                coordinatorNowNs = 5_000_000_000L,
                preparedQueueItemIds = setOf(item.queueItemId),
                playPositionOverrideMs = 0L,
            ) as RoomReducer.Decision.Accepted

        val body = result.mutations.single().body as ProtocolBody.PlayScheduled
        assertEquals(0L, body.positionMs)
        assertEquals(0L, result.mutations.single().snapshot.playback.positionAtTimestampMs)
    }

    @Test
    fun pausedAtDurationWithoutTerminalOverrideRetainsExistingPlaySemantics() {
        val item = QueueItem.create(track, peer)
        val room =
            snapshot(listOf(item))
                .copy(
                    playback =
                        CanonicalPlaybackState(
                            queueItemId = item.queueItemId,
                            positionAtTimestampMs = track.durationMs,
                            coordinatorTimestampNs = 1_000,
                            isPlaying = false,
                            revision = 8,
                        )
                )

        val result =
            RoomReducer.decide(
                room,
                UserCommand.Play(requestedBy = peer),
                coordinatorNowNs = 5_000_000_000L,
                preparedQueueItemIds = setOf(item.queueItemId),
            ) as RoomReducer.Decision.Accepted

        val body = result.mutations.single().body as ProtocolBody.PlayScheduled
        assertEquals(track.durationMs, body.positionMs)
    }

    @Test
    fun oldCanonicalMessagesAreIgnored() {
        val item = QueueItem.create(track, peer)
        val initial = snapshot(listOf(item)).copy(sequence = 5)
        val body = ProtocolBody.QueueItemsRemoved(listOf(item.queueItemId))
        assertEquals(initial, RoomReducer.applyCanonical(initial, 4, body))
    }

    @Test
    fun everyConnectedPeerCanControlPlayback() {
        val guest = PeerId("peer-b")
        val item = QueueItem.create(track, peer)
        val room =
            snapshot(listOf(item))
                .copy(members = listOf(MemberSnapshot(peer, "A"), MemberSnapshot(guest, "B")))
        val result =
            RoomReducer.decide(
                room,
                UserCommand.Play(requestedBy = guest),
                0,
                preparedQueueItemIds = setOf(item.queueItemId),
            )
        assertTrue(result is RoomReducer.Decision.Accepted)
    }

    @Test
    fun roomOptionsContainOnlyEnforcedBehavior() {
        val item = QueueItem.create(track, peer)
        val requested = RoomOptions(waitAtTrackBoundary = false, preloadCount = 50)
        val result =
            RoomReducer.decide(
                snapshot(listOf(item)),
                UserCommand.OptionsChange(requestedBy = peer, options = requested),
                0,
            ) as RoomReducer.Decision.Accepted
        assertFalse(result.mutations.single().snapshot.options.waitAtTrackBoundary)
        assertEquals(3, result.mutations.single().snapshot.options.preloadCount)
    }

    @Test
    fun removingCurrentTrackSelectsNextAndKeepsPlaybackIntent() {
        val first = QueueItem.create(track, peer)
        val secondTrack = track.copy(trackId = TrackId("b".repeat(64)), title = "Second")
        val second = QueueItem.create(secondTrack, peer)
        val room =
            snapshot(listOf(first, second))
                .copy(
                    playback =
                        CanonicalPlaybackState(first.queueItemId, 30_000, 1_000, isPlaying = true)
                )
        val result =
            RoomReducer.decide(
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
    fun overlappingPlaybackCommandsUseLatestIntent() {
        val item = QueueItem.create(track, peer)
        val room =
            snapshot(listOf(item))
                .copy(
                    playback =
                        CanonicalPlaybackState(
                            queueItemId = item.queueItemId,
                            positionAtTimestampMs = 0,
                            coordinatorTimestampNs = 2_000_000_000L,
                            isPlaying = true,
                        )
                )

        val early =
            RoomReducer.decide(room, UserCommand.Pause(requestedBy = peer), 1_000_000_000L)
                as RoomReducer.Decision.Accepted
        val pause = early.mutations.single().body as ProtocolBody.PauseScheduled
        assertEquals(item.queueItemId, pause.queueItemId)
        assertFalse(early.mutations.single().snapshot.playback.isPlaying)
    }

    @Test
    fun queueEditsRemainAvailableDuringScheduledPlaybackChange() {
        val item = QueueItem.create(track, peer)
        val room =
            snapshot(listOf(item))
                .copy(
                    playback =
                        CanonicalPlaybackState(
                            queueItemId = item.queueItemId,
                            positionAtTimestampMs = 0,
                            coordinatorTimestampNs = 2_000_000_000L,
                            isPlaying = true,
                        )
                )
        val another = track.copy(trackId = TrackId("c".repeat(64)), title = "Another")

        val result =
            RoomReducer.decide(
                room,
                UserCommand.QueueAdd(requestedBy = peer, tracks = listOf(another)),
                1_000_000_000L,
            )
        assertTrue(result is RoomReducer.Decision.Accepted)
    }

    @Test
    fun shuffleKeepsCurrentTrackAndIsDeterministic() {
        val queue =
            (0 until 8).map { index ->
                QueueItem.create(
                    track.copy(
                        trackId = TrackId(index.toString(16).padStart(64, '0')),
                        title = "Track $index",
                    ),
                    peer,
                    index.toLong(),
                )
            }
        val room =
            snapshot(queue)
                .copy(playback = CanonicalPlaybackState(queue[2].queueItemId, 0, 1, true))
        val command = UserCommand.QueueShuffle(requestedBy = peer, shuffleSeed = 42L)
        val firstResult = RoomReducer.decide(room, command, 2) as RoomReducer.Decision.Accepted
        val secondResult =
            RoomReducer.decide(room, command.copy(commandId = "other"), 2)
                as RoomReducer.Decision.Accepted
        val firstQueue = firstResult.mutations.single().snapshot.queue
        val secondQueue = secondResult.mutations.single().snapshot.queue
        assertEquals(
            queue.take(3).map { it.queueItemId },
            firstQueue.take(3).map { it.queueItemId },
        )
        assertEquals(firstQueue.map { it.queueItemId }, secondQueue.map { it.queueItemId })
        assertEquals(RepeatMode.OFF, firstResult.mutations.single().snapshot.repeatMode)
    }

    @Test
    fun queueShuffleIsAOneShotCanonicalReorder() {
        val queue =
            (0 until 8).map { index ->
                QueueItem.create(
                    track.copy(
                        trackId = TrackId(index.toString(16).padStart(64, '0')),
                        title = "Track $index",
                    ),
                    peer,
                    index.toLong(),
                )
            }
        val room =
            snapshot(queue)
                .copy(
                    playback = CanonicalPlaybackState(queue[2].queueItemId, 0, 1, true),
                    repeatMode = RepeatMode.ALL,
                )
        val first =
            RoomReducer.decide(
                room,
                UserCommand.QueueShuffle(requestedBy = peer, shuffleSeed = 42L),
                2,
            ) as RoomReducer.Decision.Accepted
        val firstShuffled = first.mutations.single().snapshot

        assertEquals(
            queue.take(3).map { it.queueItemId },
            firstShuffled.queue.take(3).map { it.queueItemId },
        )
        assertEquals(RepeatMode.ALL, firstShuffled.repeatMode)
        assertTrue(
            firstShuffled.queue.drop(3).map { it.queueItemId } !=
                queue.drop(3).map { it.queueItemId }
        )

        val second =
            RoomReducer.decide(
                firstShuffled,
                UserCommand.QueueShuffle(
                    requestedBy = peer,
                    shuffleSeed = 99L,
                    commandId = "shuffle-again",
                ),
                3,
            ) as RoomReducer.Decision.Accepted
        val secondShuffled = second.mutations.single().snapshot
        assertEquals(
            queue.take(3).map { it.queueItemId },
            secondShuffled.queue.take(3).map { it.queueItemId },
        )
        assertTrue(
            secondShuffled.queue.drop(3).map { it.queueItemId } !=
                firstShuffled.queue.drop(3).map { it.queueItemId }
        )
    }

    @Test
    fun repeatModeChangesWithoutTouchingQueueOrder() {
        val queue =
            (0 until 6).map { index ->
                QueueItem.create(
                    track.copy(
                        trackId = TrackId(index.toString(16).padStart(64, '0')),
                        title = "Track $index",
                    ),
                    peer,
                    index.toLong(),
                )
            }
        val result =
            RoomReducer.decide(
                snapshot(queue),
                UserCommand.RepeatModeChange(
                    requestedBy = peer,
                    repeatMode = RepeatMode.ALL,
                ),
                2,
            ) as RoomReducer.Decision.Accepted
        val changed = result.mutations.single().snapshot
        assertEquals(RepeatMode.ALL, changed.repeatMode)
        assertEquals(queue.map { it.queueItemId }, changed.queue.map { it.queueItemId })
    }

    @Test
    fun queueCanBeReorderedAfterShuffle() {
        val queue =
            (0 until 4).map { index ->
                QueueItem.create(
                    track.copy(
                        trackId = TrackId(index.toString(16).padStart(64, '0')),
                        title = "Track $index",
                    ),
                    peer,
                    index.toLong(),
                )
            }
        val room =
            snapshot(queue)
                .copy(playback = CanonicalPlaybackState(queue.first().queueItemId, 0, 1, false))
        val shuffled =
            (RoomReducer.decide(
                    room,
                    UserCommand.QueueShuffle(requestedBy = peer, shuffleSeed = 1L),
                    1,
                ) as RoomReducer.Decision.Accepted)
                .mutations
                .single()
                .snapshot
        val movedItem = shuffled.queue.last()
        val result =
            RoomReducer.decide(
                shuffled,
                UserCommand.QueueMove(
                    requestedBy = peer,
                    queueItemId = movedItem.queueItemId,
                    newIndex = 1,
                ),
                0,
            )
        assertTrue(result is RoomReducer.Decision.Accepted)
    }

    @Test
    fun playNextInsertsAfterCurrentWithoutChangingCurrent() {
        val first = QueueItem.create(track, peer)
        val second =
            QueueItem.create(
                track.copy(trackId = TrackId("b".repeat(64)), title = "Second"),
                peer,
            )
        val nextTrack = track.copy(trackId = TrackId("c".repeat(64)), title = "Next")
        val room =
            snapshot(listOf(first, second))
                .copy(playback = CanonicalPlaybackState(first.queueItemId, 10_000, 1, true))
        val result =
            RoomReducer.decide(
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
            updated.queue.map { it.track.trackId },
        )
        assertEquals(first.queueItemId, updated.playback.queueItemId)
    }

    @Test
    fun existingQueueItemCanMoveDirectlyAfterCurrent() {
        val queue =
            (0 until 5).map { index ->
                QueueItem.create(
                    track.copy(
                        trackId = TrackId(index.toString(16).padStart(64, '0')),
                        title = "Track $index",
                    ),
                    peer,
                    index.toLong(),
                )
            }
        val room =
            snapshot(queue)
                .copy(playback = CanonicalPlaybackState(queue[1].queueItemId, 5_000, 1, true))
        val result =
            RoomReducer.decide(
                room,
                UserCommand.QueueMoveAfterCurrent(
                    requestedBy = peer,
                    queueItemId = queue[4].queueItemId,
                ),
                2,
            ) as RoomReducer.Decision.Accepted

        assertEquals(
            listOf(queue[0], queue[1], queue[4], queue[2], queue[3]).map { it.queueItemId },
            result.mutations.single().snapshot.queue.map { it.queueItemId },
        )
    }

    @Test
    fun selectingQueueItemStartsItWithoutReorderingQueue() {
        val queue =
            (0 until 3).map { index ->
                QueueItem.create(
                    track.copy(
                        trackId = TrackId(index.toString(16).padStart(64, '0')),
                        title = "Track $index",
                    ),
                    peer,
                    index.toLong(),
                )
            }
        val room =
            snapshot(queue)
                .copy(playback = CanonicalPlaybackState(queue.first().queueItemId, 8_000, 1, true))
        val result =
            RoomReducer.decide(
                room,
                UserCommand.PlayQueueItem(
                    requestedBy = peer,
                    queueItemId = queue.last().queueItemId,
                ),
                2,
                preparedQueueItemIds = queue.mapTo(mutableSetOf()) { it.queueItemId },
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
    fun selectingPreparedQueueItemCanArrivePaused() {
        val first = QueueItem.create(track, peer)
        val target =
            QueueItem.create(
                track.copy(trackId = TrackId("c".repeat(64)), title = "Paused target"),
                peer,
            )
        val room =
            snapshot(listOf(first, target))
                .copy(playback = CanonicalPlaybackState(first.queueItemId, 8_000, 1, true))
        val result =
            RoomReducer.decide(
                room,
                UserCommand.PlayQueueItem(
                    requestedBy = peer,
                    queueItemId = target.queueItemId,
                    resumePlayback = false,
                ),
                2,
                preparedQueueItemIds = setOf(first.queueItemId, target.queueItemId),
            ) as RoomReducer.Decision.Accepted
        val body = result.mutations.single().body as ProtocolBody.CurrentItemChanged

        assertEquals(target.queueItemId, body.queueItemId)
        assertFalse(body.resumePlayback)
        assertFalse(result.mutations.single().snapshot.playback.isPlaying)
    }

    @Test
    fun selectingUnpreparedQueueItemIsRejectedWithoutChangingPlayback() {
        val first = QueueItem.create(track, peer)
        val target =
            QueueItem.create(
                track.copy(trackId = TrackId("b".repeat(64)), title = "Target"),
                peer,
            )
        val room =
            snapshot(listOf(first, target))
                .copy(playback = CanonicalPlaybackState(first.queueItemId, 8_000, 1, true))
        val result =
            RoomReducer.decide(
                room,
                UserCommand.PlayQueueItem(requestedBy = peer, queueItemId = target.queueItemId),
                2,
                preparedQueueItemIds = setOf(first.queueItemId),
            ) as RoomReducer.Decision.Rejected

        assertEquals("The selected song is still preparing", result.reason)
        assertEquals(first.queueItemId, room.playback.queueItemId)
        assertTrue(room.playback.isPlaying)
    }

    @Test
    fun clearPlayedRemovesOnlyHistory() {
        val queue =
            (0 until 4).map { index ->
                QueueItem.create(
                    track.copy(
                        trackId = TrackId(index.toString(16).padStart(64, '0')),
                        title = "Track $index",
                    ),
                    peer,
                    index.toLong(),
                )
            }
        val room =
            snapshot(queue)
                .copy(playback = CanonicalPlaybackState(queue[2].queueItemId, 5_000, 1, true))
        val result =
            RoomReducer.decide(
                room,
                UserCommand.QueueClearPlayed(requestedBy = peer),
                2,
            ) as RoomReducer.Decision.Accepted
        assertEquals(
            listOf(queue[2].queueItemId, queue[3].queueItemId),
            result.mutations.last().snapshot.queue.map { it.queueItemId },
        )
        assertEquals(queue[2].queueItemId, result.mutations.last().snapshot.playback.queueItemId)
    }

    @Test
    fun connectedMemberCanClearQueueAtomically() {
        val member = PeerId("peer-b")
        val queue =
            (0 until 4).map { index ->
                QueueItem.create(
                    track.copy(
                        trackId = TrackId(index.toString(16).padStart(64, '0')),
                        title = "Track $index",
                    ),
                    peer,
                    index.toLong(),
                )
            }
        val room =
            snapshot(queue)
                .copy(
                    members = listOf(MemberSnapshot(peer, "A"), MemberSnapshot(member, "B")),
                    playback = CanonicalPlaybackState(queue[1].queueItemId, 5_000, 1, true),
                    repeatMode = RepeatMode.ALL,
                )

        val result =
            RoomReducer.decide(
                room,
                UserCommand.QueueClear(requestedBy = member),
                coordinatorNowNs = 10_000,
            ) as RoomReducer.Decision.Accepted

        assertEquals(1, result.mutations.size)
        assertTrue(result.mutations.single().body is ProtocolBody.QueueCleared)
        val cleared = result.mutations.single().snapshot
        assertTrue(cleared.queue.isEmpty())
        assertEquals(RepeatMode.OFF, cleared.repeatMode)
        assertEquals(null, cleared.playback.queueItemId)
        assertFalse(cleared.playback.isPlaying)
    }

    @Test
    fun queueCapacityIsBoundedAndBulkAddsAreTrimmed() {
        val queue =
            (0 until RoomReducer.MAX_QUEUE_ITEMS - 1).map { index ->
                QueueItem.create(
                    track.copy(
                        trackId = TrackId(index.toString(16).padStart(64, '0')),
                        title = "Track $index",
                    ),
                    peer,
                    index.toLong(),
                )
            }
        val additions =
            listOf('b', 'c', 'd').map { marker ->
                track.copy(trackId = TrackId(marker.toString().repeat(64)), title = "Added $marker")
            }
        val result =
            RoomReducer.decide(
                snapshot(queue),
                UserCommand.QueueAdd(requestedBy = peer, tracks = additions),
                0,
            ) as RoomReducer.Decision.Accepted

        assertEquals(1, result.mutations.size)
        assertEquals(RoomReducer.MAX_QUEUE_ITEMS, result.mutations.single().snapshot.queue.size)
        assertEquals(1, (result.mutations.single().body as ProtocolBody.QueueItemsAdded).items.size)
        val full =
            RoomReducer.decide(
                result.mutations.single().snapshot,
                UserCommand.QueueAdd(
                    requestedBy = peer,
                    tracks = listOf(additions.first()),
                    commandId = "full",
                ),
                0,
            )
        assertTrue(full is RoomReducer.Decision.Rejected)
    }

    @Test
    fun addingAfterShuffleKeepsTheVisibleCanonicalOrder() {
        val queue =
            (0 until 5).map { index ->
                QueueItem.create(
                    track.copy(
                        trackId = TrackId(index.toString(16).padStart(64, '0')),
                        title = "Track $index",
                    ),
                    peer,
                    index.toLong(),
                )
            }
        val shuffled =
            (RoomReducer.decide(
                    snapshot(queue),
                    UserCommand.QueueShuffle(requestedBy = peer, shuffleSeed = 11),
                    0,
                ) as RoomReducer.Decision.Accepted)
                .mutations
                .single()
                .snapshot
        val shuffledIds = shuffled.queue.map { it.queueItemId }
        val addedTrack = track.copy(trackId = TrackId("f".repeat(64)), title = "Added")
        val withAdded =
            (RoomReducer.decide(
                    shuffled,
                    UserCommand.QueueAdd(
                        requestedBy = peer,
                        tracks = listOf(addedTrack),
                        commandId = "add-after-shuffle",
                    ),
                    1,
                ) as RoomReducer.Decision.Accepted)
                .mutations
                .single()
                .snapshot

        assertEquals(shuffledIds, withAdded.queue.dropLast(1).map { it.queueItemId })
        assertEquals(addedTrack.trackId, withAdded.queue.last().track.trackId)
    }

    @Test
    fun oversizedBulkAddIsRejected() {
        val additions =
            List(RoomReducer.MAX_TRACKS_PER_COMMAND + 1) { index ->
                track.copy(trackId = TrackId(index.toString(16).padStart(64, '0')))
            }
        val result =
            RoomReducer.decide(
                snapshot(),
                UserCommand.QueueAdd(requestedBy = peer, tracks = additions),
                0,
            )
        assertTrue(result is RoomReducer.Decision.Rejected)
    }

    @Test
    fun malformedTrackDescriptorIsRejected() {
        val malformed = track.copy(trackId = TrackId("not-a-sha256"))
        val result =
            RoomReducer.decide(
                snapshot(),
                UserCommand.QueueAdd(requestedBy = peer, tracks = listOf(malformed)),
                0,
            )
        assertTrue(result is RoomReducer.Decision.Rejected)
    }

    @Test
    fun clearQueueAdvancesBothQueueAndPlaybackRevisions() {
        val item = QueueItem.create(track, peer)
        val room =
            snapshot(listOf(item))
                .copy(
                    sequence = 8,
                    queueRevision = 6,
                    playback =
                        CanonicalPlaybackState(
                            queueItemId = item.queueItemId,
                            isPlaying = true,
                            revision = 7,
                        ),
                )
        val result =
            RoomReducer.decide(room, UserCommand.QueueClear(requestedBy = peer), 0)
                as RoomReducer.Decision.Accepted
        val mutation = result.mutations.single()

        assertEquals(9L, mutation.snapshot.queueRevision)
        assertEquals(9L, mutation.snapshot.playback.revision)
        assertTrue(mutation.snapshot.queue.isEmpty())
        assertFalse(mutation.snapshot.playback.isPlaying)
    }

    @Test
    fun roomOptionCannotBypassReadinessForSelectedPlayback() {
        val first = QueueItem.create(track, peer)
        val target =
            QueueItem.create(
                track.copy(trackId = TrackId("d".repeat(64)), title = "Needs preparation"),
                peer,
            )
        val room =
            snapshot(listOf(first, target))
                .copy(
                    options = RoomOptions(waitAtTrackBoundary = false),
                    playback = CanonicalPlaybackState(first.queueItemId, 0, 0, false),
                )

        val result =
            RoomReducer.decide(
                room,
                UserCommand.PlayQueueItem(
                    commandId = "select-unready-nonblocking",
                    requestedBy = peer,
                    queueItemId = target.queueItemId,
                ),
                coordinatorNowNs = 0,
                preparedQueueItemIds = setOf(first.queueItemId),
            )

        assertTrue(result is RoomReducer.Decision.Rejected)
    }
}
