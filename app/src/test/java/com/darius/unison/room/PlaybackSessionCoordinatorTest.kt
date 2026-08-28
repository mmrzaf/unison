package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.playback.AudioOutputRoute
import com.darius.unison.protocol.ProtocolBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionCoordinatorTest {
    private val coordinatorPeer = PeerId("coordinator-peer")
    private val guestPeer = PeerId("guest-peer")
    private val item =
        QueueItem.create(
            TrackDescriptor(TrackId("a".repeat(64)), 100, durationMs = 60_000),
            coordinatorPeer,
            1,
        )

    private fun coordinator() =
        PlaybackSessionCoordinator(
            playbackStatusReportIntervalNs = 1_000,
            clockQualityReportIntervalNs = 10_000,
            convergence = PlaybackConvergencePolicy(minimumRepairIntervalNs = 0),
        )

    private fun snapshot(revision: Long = 12, queueRevision: Long = 7) =
        RoomSnapshot(
            roomId = "room",
            roomName = "Room",
            term = CoordinatorTerm(1, coordinatorPeer),
            sequence = revision,
            members =
                listOf(
                    MemberSnapshot(coordinatorPeer, "Coordinator"),
                    MemberSnapshot(guestPeer, "Guest"),
                ),
            queue = listOf(item),
            playback =
                CanonicalPlaybackState(
                    queueItemId = item.queueItemId,
                    positionAtTimestampMs = 500,
                    coordinatorTimestampNs = 1_000,
                    isPlaying = true,
                    revision = revision,
                ),
            queueRevision = queueRevision,
        )

    @Test
    fun newerAcceptedSyncBecomesGuestTickCanonical() {
        val coordinator = coordinator()
        val room = snapshot()
        val sync = coordinator.playbackStateSync(room, atCoordinatorNs = 2_000)

        val decision = coordinator.evaluateIncomingSync(sync, room)

        assertTrue(decision is PlaybackSessionCoordinator.IncomingSyncDecision.Apply)
        assertEquals(sync.playback, coordinator.canonicalForTick(room, coordinator = false))
    }

    @Test
    fun staleSyncCannotReplaceNewerAcceptedReference() {
        val coordinator = coordinator()
        val newestRoom = snapshot(revision = 13)
        coordinator.seedCanonical(newestRoom.playback)
        val staleRoom = snapshot(revision = 12)
        val stale = coordinator.playbackStateSync(staleRoom, atCoordinatorNs = 2_000)

        val decision = coordinator.evaluateIncomingSync(stale, newestRoom)

        assertEquals(
            PlaybackSessionCoordinator.IncomingSyncDecision.IgnoreStale(12, 13),
            decision,
        )
        assertEquals(newestRoom.playback, coordinator.canonicalForTick(newestRoom, false))
    }

    @Test
    fun impossibleFutureRevisionRequestsSnapshot() {
        val coordinator = coordinator()
        val room = snapshot(revision = 12)
        val future =
            ProtocolBody.PlaybackStateSync(
                playback = room.playback.copy(revision = 13),
                canonicalSequence = 13,
                queueRevision = room.queueRevision,
                recovery = false,
            )

        assertEquals(
            PlaybackSessionCoordinator.IncomingSyncDecision.RequestSnapshot(12),
            coordinator.evaluateIncomingSync(future, room),
        )
    }

    @Test
    fun playbackStatusCadenceIsIndependentOfLocalSchedulerDelay() {
        val coordinator = coordinator()

        assertTrue(coordinator.shouldReportPlaybackStatus(1_000))
        assertFalse(coordinator.shouldReportPlaybackStatus(1_999))
        assertTrue(coordinator.shouldReportPlaybackStatus(2_000))

        // There is deliberately no scheduler-delay discontinuity detector here. Local playback
        // Synchronization owns its cadence outside the room actor.
        assertTrue(coordinator.shouldReportPlaybackStatus(100_000))
    }

    @Test
    fun outputRouteOnlySignalsRealChange() {
        val coordinator = coordinator()

        assertFalse(coordinator.observeOutputRoute(AudioOutputRoute.BUILT_IN_SPEAKER))
        assertFalse(coordinator.observeOutputRoute(AudioOutputRoute.BUILT_IN_SPEAKER))
        assertTrue(coordinator.observeOutputRoute(AudioOutputRoute.BLUETOOTH))
    }

    @Test
    fun resetClearsAcceptedReferenceAndCadence() {
        val coordinator = coordinator()
        val room = snapshot()
        coordinator.seedCanonical(room.playback.copy(revision = 99))
        coordinator.shouldReportPlaybackStatus(10_000)

        coordinator.resetSession()

        assertEquals(room.playback, coordinator.canonicalForTick(room, coordinator = false))
        assertTrue(coordinator.shouldReportPlaybackStatus(10_000))
    }
}
