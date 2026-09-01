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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportTargetPolicyTest {
    private val peer = PeerId("peer-000000000001")
    private val items =
        (0..3).map { index ->
            QueueItem.create(
                TrackDescriptor(
                    trackId = TrackId(index.toString().padStart(64, 'a')),
                    sizeBytes = 100,
                    mimeType = "audio/mpeg",
                    durationMs = 60_000,
                    title = "Song $index",
                ),
                peer,
            )
        }

    @Test
    fun nextToUnreadyTrackWaitsForThatExactSuccessor() {
        val snapshot = snapshot(prepared = setOf(items[0].queueItemId))
        val result =
            resolve(
                UserCommand.SkipNext("next", peer),
                snapshot,
                coordinatorNowNs = 0,
            )

        assertNull(result.rejection)
        assertNull(result.command)
        assertEquals(items[1].queueItemId, result.waitForPreparationQueueItemId)
        assertTrue(result.resumeWhenReady)
        assertEquals(items[0].queueItemId, snapshot.playback.queueItemId)
    }

    @Test
    fun unreadyNextDoesNotSkipAheadToALaterReadyTrack() {
        val snapshot = snapshot(prepared = setOf(items[0].queueItemId, items[2].queueItemId))
        val result =
            resolve(
                UserCommand.SkipNext("next-2", peer),
                snapshot,
                coordinatorNowNs = 0,
            )

        assertNull(result.rejection)
        assertNull(result.command)
        assertEquals(items[1].queueItemId, result.waitForPreparationQueueItemId)
    }

    @Test
    fun pausedNextWaitsWithoutInventingResumeIntent() {
        val snapshot =
            snapshot(
                prepared = setOf(items[0].queueItemId),
                isPlaying = false,
            )
        val result =
            resolve(
                UserCommand.SkipNext("next-paused", peer),
                snapshot,
                coordinatorNowNs = 0,
            )

        assertNull(result.rejection)
        assertNull(result.command)
        assertEquals(items[1].queueItemId, result.waitForPreparationQueueItemId)
        assertEquals(false, result.resumeWhenReady)
    }

    @Test
    fun readyNextPreservesPausedIntent() {
        val snapshot =
            snapshot(
                prepared = items.mapTo(hashSetOf()) { it.queueItemId },
                isPlaying = false,
            )
        val result =
            resolve(
                UserCommand.SkipNext("next-paused-ready", peer),
                snapshot,
                coordinatorNowNs = 0,
            )

        val command = result.command as UserCommand.PlayQueueItem
        assertEquals(false, command.resumePlayback)
    }

    @Test
    fun explicitSelectionOfUnreadyTrackRequiresPreparation() {
        val snapshot = snapshot(prepared = setOf(items[0].queueItemId))
        val result =
            resolve(
                UserCommand.PlayQueueItem(
                    commandId = "select-paused",
                    requestedBy = peer,
                    queueItemId = items[1].queueItemId,
                    resumePlayback = false,
                ),
                snapshot,
                coordinatorNowNs = 0,
            )

        assertEquals("Prepare this song before playing it", result.rejection)
        assertNull(result.command)
    }

    @Test
    fun selectingAlreadyPlayingCanonicalItemSettlesWithoutCanonicalMutation() {
        val snapshot =
            snapshot(
                prepared = items.mapTo(hashSetOf()) { it.queueItemId },
                positionMs = 12_000L,
                isPlaying = true,
            )

        val result =
            resolve(
                UserCommand.PlayQueueItem(
                    commandId = "same-playing",
                    requestedBy = peer,
                    queueItemId = items[0].queueItemId,
                ),
                snapshot,
                coordinatorNowNs = 0L,
            )

        assertTrue(result.alreadyAligned)
        assertNull(result.command)
    }

    @Test
    fun selectingPausedCanonicalItemResumesWithoutRestarting() {
        val snapshot =
            snapshot(
                prepared = items.mapTo(hashSetOf()) { it.queueItemId },
                positionMs = 12_000L,
                isPlaying = false,
            )

        val result =
            resolve(
                UserCommand.PlayQueueItem(
                    commandId = "same-paused",
                    requestedBy = peer,
                    queueItemId = items[0].queueItemId,
                ),
                snapshot,
                coordinatorNowNs = 0L,
            )

        val play = result.command as UserCommand.Play
        assertEquals("same-paused", play.commandId)
        assertEquals(12_000L, snapshot.playback.positionAtTimestampMs)
    }

    @Test
    fun previousAfterFourSecondsRestartsCurrentTrack() {
        val snapshot =
            snapshot(
                prepared = items.mapTo(hashSetOf()) { it.queueItemId },
                positionMs = 5_000,
            )
        val result =
            resolve(
                UserCommand.SkipPrevious("previous", peer),
                snapshot,
                coordinatorNowNs = 0,
            )
        val seek = result.command as UserCommand.Seek
        assertEquals(0L, seek.positionMs)
    }

    @Test
    fun readyNextResolvesToStableAbsoluteTarget() {
        val snapshot = snapshot(prepared = items.mapTo(hashSetOf()) { it.queueItemId })
        val result =
            resolve(
                UserCommand.SkipNext("next", peer),
                snapshot,
                coordinatorNowNs = 0,
            )
        val command = result.command as UserCommand.PlayQueueItem
        assertEquals(items[1].queueItemId, command.queueItemId)
        assertTrue(result.rejection == null)
    }

    private val readiness =
        java.util.IdentityHashMap<RoomSnapshot, Set<com.darius.unison.model.QueueItemId>>()

    private fun resolve(
        command: UserCommand,
        snapshot: RoomSnapshot,
        coordinatorNowNs: Long,
    ) =
        TransportTargetPolicy.resolve(
            command,
            snapshot,
            coordinatorNowNs,
            readiness[snapshot].orEmpty(),
        )

    private fun snapshot(
        prepared: Set<com.darius.unison.model.QueueItemId>,
        positionMs: Long = 0L,
        isPlaying: Boolean = true,
    ): RoomSnapshot {
        val snapshot =
            RoomSnapshot(
                roomId = "room",
                roomName = "Room",
                term = CoordinatorTerm(1, peer),
                sequence = 0,
                members = listOf(MemberSnapshot(peer, "Alex")),
                queue = items,
                playback =
                    CanonicalPlaybackState(
                        queueItemId = items[0].queueItemId,
                        positionAtTimestampMs = positionMs,
                        coordinatorTimestampNs = 0,
                        isPlaying = isPlaying,
                    ),
                options = RoomOptions(waitAtTrackBoundary = true),
            )
        readiness[snapshot] = prepared
        return snapshot
    }
}
