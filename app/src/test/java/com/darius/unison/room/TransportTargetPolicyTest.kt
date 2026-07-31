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
    fun nextToUnreadyTrackBecomesPendingWithoutChangingCurrentItem() {
        val snapshot = snapshot(prepared = setOf(items[0].queueItemId))
        val result =
            TransportTargetPolicy.resolve(
                UserCommand.SkipNext("next", peer),
                snapshot,
                coordinatorNowNs = 0,
            )

        assertEquals(items[1].queueItemId, result.pendingTarget)
        assertEquals(true, result.pendingResumePlayback)
        assertNull(result.command)
        assertEquals(items[0].queueItemId, snapshot.playback.queueItemId)
    }

    @Test
    fun repeatedNextAdvancesFromPendingAbsoluteTarget() {
        val snapshot = snapshot(prepared = setOf(items[0].queueItemId, items[2].queueItemId))
        val result =
            TransportTargetPolicy.resolve(
                UserCommand.SkipNext("next-2", peer),
                snapshot,
                coordinatorNowNs = 0,
                pendingTarget = items[1].queueItemId,
            )

        val resolved = result.command as UserCommand.PlayQueueItem
        assertEquals(items[2].queueItemId, resolved.queueItemId)
        assertEquals("next-2", resolved.commandId)
    }

    @Test
    fun pausedNextPreservesPausedIntentWhilePreparing() {
        val snapshot =
            snapshot(
                prepared = setOf(items[0].queueItemId),
                isPlaying = false,
            )
        val result =
            TransportTargetPolicy.resolve(
                UserCommand.SkipNext("next-paused", peer),
                snapshot,
                coordinatorNowNs = 0,
            )

        assertEquals(items[1].queueItemId, result.pendingTarget)
        assertEquals(false, result.pendingResumePlayback)
    }

    @Test
    fun readyNextPreservesPausedIntent() {
        val snapshot =
            snapshot(
                prepared = items.mapTo(hashSetOf()) { it.queueItemId },
                isPlaying = false,
            )
        val result =
            TransportTargetPolicy.resolve(
                UserCommand.SkipNext("next-paused-ready", peer),
                snapshot,
                coordinatorNowNs = 0,
            )

        val command = result.command as UserCommand.PlayQueueItem
        assertEquals(false, command.resumePlayback)
    }

    @Test
    fun explicitPendingSelectionPreservesRequestedResumeIntent() {
        val snapshot = snapshot(prepared = setOf(items[0].queueItemId))
        val result =
            TransportTargetPolicy.resolve(
                UserCommand.PlayQueueItem(
                    commandId = "select-paused",
                    requestedBy = peer,
                    queueItemId = items[1].queueItemId,
                    resumePlayback = false,
                ),
                snapshot,
                coordinatorNowNs = 0,
            )

        assertEquals(items[1].queueItemId, result.pendingTarget)
        assertEquals(false, result.pendingResumePlayback)
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
            TransportTargetPolicy.resolve(
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
        assertNull(result.pendingTarget)
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
            TransportTargetPolicy.resolve(
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
            TransportTargetPolicy.resolve(
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
            TransportTargetPolicy.resolve(
                UserCommand.SkipNext("next", peer),
                snapshot,
                coordinatorNowNs = 0,
            )
        val command = result.command as UserCommand.PlayQueueItem
        assertEquals(items[1].queueItemId, command.queueItemId)
        assertTrue(result.rejection == null)
    }

    private fun snapshot(
        prepared: Set<com.darius.unison.model.QueueItemId>,
        positionMs: Long = 0L,
        isPlaying: Boolean = true,
    ) =
        RoomSnapshot(
            roomId = "room",
            roomName = "Room",
            term = CoordinatorTerm(1, peer),
            sequence = 0,
            members = listOf(MemberSnapshot(peer, "Friend")),
            queue = items,
            preparedQueueItemIds = prepared,
            playback =
                CanonicalPlaybackState(
                    queueItemId = items[0].queueItemId,
                    positionAtTimestampMs = positionMs,
                    coordinatorTimestampNs = 0,
                    isPlaying = isPlaying,
                ),
            options = RoomOptions(waitAtTrackBoundary = true),
        )
}
