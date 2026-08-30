package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalReplayPolicyTest {
    private val peer = PeerId("peer")
    private val item =
        QueueItem.create(
            TrackDescriptor(TrackId("a".repeat(64)), sizeBytes = 1, durationMs = 10_000),
            peer,
        )

    private fun terminalSnapshot() =
        RoomSnapshot(
            roomId = "room",
            roomName = "Room",
            term = CoordinatorTerm(1, peer),
            sequence = 9,
            members = listOf(MemberSnapshot(peer, "Peer")),
            queue = listOf(item),
            queueRevision = 4,
            playback =
                CanonicalPlaybackState(
                    queueItemId = item.queueItemId,
                    positionAtTimestampMs = 10_000,
                    coordinatorTimestampNs = 1_000,
                    isPlaying = false,
                    revision = 9,
                ),
        )

    @Test
    fun finalNaturalPauseCanProduceCanonicalReplayOverride() {
        val snapshot = terminalSnapshot()
        val marker = TerminalReplayPolicy.capture(snapshot, item.queueItemId)

        assertEquals(0L, TerminalReplayPolicy.playPositionOverrideMs(snapshot, marker))
        assertTrue(TerminalReplayPolicy.isStillValid(snapshot, marker))
    }

    @Test
    fun playbackRevisionChangeInvalidatesMarker() {
        val snapshot = terminalSnapshot()
        val marker = TerminalReplayPolicy.capture(snapshot, item.queueItemId)

        val sought = snapshot.copy(playback = snapshot.playback.copy(revision = 10))
        assertNull(TerminalReplayPolicy.playPositionOverrideMs(sought, marker))
        assertFalse(TerminalReplayPolicy.isStillValid(sought, marker))
    }

    @Test
    fun queueRevisionChangeInvalidatesMarker() {
        val snapshot = terminalSnapshot()
        val marker = TerminalReplayPolicy.capture(snapshot, item.queueItemId)

        assertNull(
            TerminalReplayPolicy.playPositionOverrideMs(
                snapshot.copy(queueRevision = snapshot.queueRevision + 1),
                marker,
            )
        )
    }

    @Test
    fun repeatModeThatCreatesSuccessorInvalidatesTerminalMeaning() {
        val snapshot = terminalSnapshot()
        val marker = TerminalReplayPolicy.capture(snapshot, item.queueItemId)

        assertNull(
            TerminalReplayPolicy.playPositionOverrideMs(
                snapshot.copy(repeatMode = RepeatMode.ONE),
                marker,
            )
        )
    }

    @Test
    fun repeatAllThatWrapsToFinalItemInvalidatesTerminalMeaning() {
        val snapshot = terminalSnapshot()
        val marker = TerminalReplayPolicy.capture(snapshot, item.queueItemId)

        assertNull(
            TerminalReplayPolicy.playPositionOverrideMs(
                snapshot.copy(repeatMode = RepeatMode.ALL),
                marker,
            )
        )
    }

    @Test
    fun currentItemChangeInvalidatesMarker() {
        val snapshot = terminalSnapshot()
        val marker = TerminalReplayPolicy.capture(snapshot, item.queueItemId)

        assertNull(
            TerminalReplayPolicy.playPositionOverrideMs(
                snapshot.copy(
                    playback = CanonicalPlaybackState(revision = snapshot.playback.revision)
                ),
                marker,
            )
        )
    }

    @Test
    fun playingSnapshotCannotRetainTerminalPauseMarker() {
        val snapshot = terminalSnapshot()
        val marker = TerminalReplayPolicy.capture(snapshot, item.queueItemId)

        assertNull(
            TerminalReplayPolicy.playPositionOverrideMs(
                snapshot.copy(playback = snapshot.playback.copy(isPlaying = true)),
                marker,
            )
        )
    }
}
