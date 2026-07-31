package com.darius.unison.room

import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.UserCommand
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomEngineValidationTest {
    private val peer = PeerId("11111111-1111-1111-1111-111111111111")

    private fun snapshot() =
        RoomSnapshot(
            roomId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            roomName = "Room",
            term = CoordinatorTerm(1, peer),
            sequence = 0,
            members = listOf(MemberSnapshot(peer, "Device")),
        )

    @Test
    fun rejectedSnapshotGateDoesNotCommitDecision() = runBlocking {
        val engine = RoomEngine(snapshot())
        val command =
            UserCommand.QueueAdd(
                requestedBy = peer,
                tracks = listOf(TrackDescriptor(TrackId("a".repeat(64)), sizeBytes = 100)),
            )
        val decision = engine.decide(command, 1_000) { false }
        assertTrue(decision is RoomReducer.Decision.Rejected)
        assertEquals(0, engine.snapshot().queue.size)
        assertEquals(0L, engine.snapshot().sequence)
    }

    @Test
    fun applyValidatedLeavesStateUntouchedOnFailure() = runBlocking {
        val engine = RoomEngine(snapshot())
        val member = MemberSnapshot(PeerId("22222222-2222-2222-2222-222222222222"), "Guest")
        val result =
            engine.applyValidated(1, com.darius.unison.protocol.ProtocolBody.PeerJoined(member)) {
                false
            }
        assertEquals(null, result)
        assertEquals(1, engine.snapshot().members.size)
        assertEquals(0L, engine.snapshot().sequence)
    }

    @Test
    fun bulkAddCommitsLargestSingleBatchThatFitsSnapshotBudget() = runBlocking {
        val engine = RoomEngine(snapshot())
        val tracks =
            (0 until 20).map { index ->
                TrackDescriptor(TrackId(index.toString(16).padStart(64, '0')), sizeBytes = 100)
            }
        val decision =
            engine.decide(
                UserCommand.QueueAdd(requestedBy = peer, tracks = tracks),
                coordinatorNowNs = 1_000,
            ) { candidate ->
                candidate.queue.size <= 7
            }

        assertTrue(decision is RoomReducer.Decision.Accepted)
        val accepted = decision as RoomReducer.Decision.Accepted
        assertEquals(1, accepted.mutations.size)
        assertEquals(7, accepted.mutations.single().snapshot.queue.size)
        assertEquals(7, engine.snapshot().queue.size)
        assertEquals(1L, engine.snapshot().sequence)
    }

    @Test
    fun concurrentCommandsCommitWithoutLostUpdates() = runBlocking {
        val engine = RoomEngine(snapshot())
        coroutineScope {
            repeat(200) { index ->
                launch {
                    val hash = index.toString(16).padStart(64, '0')
                    val command =
                        UserCommand.QueueAdd(
                            requestedBy = peer,
                            tracks = listOf(TrackDescriptor(TrackId(hash), sizeBytes = 100)),
                        )
                    val result = engine.decide(command, 1_000L + index)
                    assertTrue(result is RoomReducer.Decision.Accepted)
                }
            }
        }
        assertEquals(200, engine.snapshot().queue.size)
        assertEquals(200L, engine.snapshot().sequence)
    }

    @Test
    fun oldTermReplacementCannotOverwriteNewerState() = runBlocking {
        val engine = RoomEngine(snapshot().copy(term = CoordinatorTerm(4, peer), sequence = 10))
        engine.replace(snapshot().copy(term = CoordinatorTerm(3, peer), sequence = 999))
        assertEquals(4L, engine.snapshot().term.number)
        assertEquals(10L, engine.snapshot().sequence)
    }
}
