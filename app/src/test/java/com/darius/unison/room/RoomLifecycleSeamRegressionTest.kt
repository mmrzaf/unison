package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic cross-seam regressions for the lifecycle failures fixed in Milestones 1-4. */
class RoomLifecycleSeamRegressionTest {
    @Test
    fun obsoleteRoomAdmissionAndSupersededSocketAreRejectedAtConsumeTime() {
        val admittedForA = RoomSessionProvenance(roomId = "room-a", generation = 7L)
        val socketA = Any()
        val socketB = Any()

        assertFalse(
            RoomIngressAuthority.acceptsSession(
                provenance = admittedForA,
                currentRoomId = "room-b",
                currentGeneration = 8L,
                coordinatorIsAuthoritative = true,
            )
        )
        assertFalse(RoomIngressAuthority.isCurrentConnection(socketB, socketA))
        assertTrue(RoomIngressAuthority.isCurrentConnection(socketB, socketB))
    }

    @Test
    fun oldSessionTransferProgressAndHeartbeatConsequencesCannotMutateAfterReset() = runBlocking {
        val parent = SupervisorJob()
        val registry = SessionJobRegistry(CoroutineScope(parent + Dispatchers.Default))
        val generationN = registry.generation
        var transferProgressMutations = 0
        var heartbeatConsequences = 0

        registry.advanceAndCancel(1_000)

        assertFalse(registry.runIfCurrent(generationN) { transferProgressMutations += 1 })
        if (registry.isCurrent(generationN)) heartbeatConsequences += 1

        assertEquals(0, transferProgressMutations)
        assertEquals(0, heartbeatConsequences)
        parent.cancel()
    }

    @Test
    fun terminalNaturalPauseKeepsReplayMeaningOnlyWhileCanonicalRevisionsMatch() {
        val peer = PeerId("peer")
        val item =
            QueueItem.create(
                TrackDescriptor(
                    trackId = TrackId("c".repeat(64)),
                    sizeBytes = 1L,
                    durationMs = 10_000L,
                ),
                peer,
            )
        val snapshot =
            RoomSnapshot(
                roomId = "room",
                roomName = "Room",
                term = CoordinatorTerm(1L, peer),
                sequence = 3L,
                members = listOf(MemberSnapshot(peer, "Peer")),
                queue = listOf(item),
                queueRevision = 2L,
                playback =
                    CanonicalPlaybackState(
                        queueItemId = item.queueItemId,
                        positionAtTimestampMs = 10_000L,
                        coordinatorTimestampNs = 100L,
                        isPlaying = false,
                        revision = 5L,
                    ),
            )
        val marker = checkNotNull(TerminalReplayPolicy.capture(snapshot, item.queueItemId))

        assertEquals(0L, TerminalReplayPolicy.playPositionOverrideMs(snapshot, marker))
        assertEquals(
            null,
            TerminalReplayPolicy.playPositionOverrideMs(
                snapshot.copy(playback = snapshot.playback.copy(revision = 6L)),
                marker,
            ),
        )
    }
}
