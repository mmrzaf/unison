package com.darius.unison.protocol

import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class EnvelopeReplayProtectorTest {
    private val peer = PeerId("11111111-1111-1111-1111-111111111111")

    private fun envelope(
        sequence: Long? = null,
        term: Long = 3,
        messageId: String = UUID.randomUUID().toString(),
        body: ProtocolBody = ProtocolBody.Heartbeat(0),
    ) = Envelope(
        roomId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        term = term,
        coordinatorPeerId = peer,
        senderPeerId = peer,
        sequence = sequence,
        messageId = messageId,
        sentAtElapsedNs = 1_000,
        body = body,
    )

    @Test
    fun rejectsDuplicateMessageIds() {
        val guard = EnvelopeReplayProtector()
        val id = UUID.randomUUID().toString()
        assertEquals(EnvelopeAcceptance.Accepted, guard.evaluate(peer, envelope(messageId = id), 3, 4))
        assertEquals(EnvelopeAcceptance.Duplicate, guard.evaluate(peer, envelope(messageId = id), 3, 4))
    }

    @Test
    fun reportsOrderedSequenceGap() {
        val guard = EnvelopeReplayProtector()
        val result = guard.evaluate(peer, envelope(sequence = 7), acceptedTerm = 3, lastAppliedSequence = 5)
        assertEquals(EnvelopeAcceptance.SequenceGap(6, 7), result)
    }

    @Test
    fun rejectsOldTermAndUnexpectedHigherTermMutation() {
        val guard = EnvelopeReplayProtector()
        assertTrue(guard.evaluate(peer, envelope(term = 2), 3, 0) is EnvelopeAcceptance.Rejected)
        assertTrue(guard.evaluate(peer, envelope(term = 4), 3, 0) is EnvelopeAcceptance.Rejected)
    }

    @Test
    fun allowsHigherTermSnapshot() {
        val guard = EnvelopeReplayProtector()
        val snapshot = TestSnapshots.basic(term = 4)
        val result = guard.evaluate(
            peer,
            envelope(term = 4, body = ProtocolBody.Snapshot(snapshot)),
            acceptedTerm = 3,
            lastAppliedSequence = 0,
        )
        assertEquals(EnvelopeAcceptance.Accepted, result)
    }

    @Test
    fun rejectsExpiredScheduledCommand() {
        val guard = EnvelopeReplayProtector(scheduledCommandExpiryNs = 100)
        val body = ProtocolBody.PlayScheduled(
            QueueItemId("33333333-3333-3333-3333-333333333333"),
            positionMs = 0,
            executeAtCoordinatorNs = 1_000,
        )
        val result = guard.evaluate(
            peer,
            envelope(sequence = 1, body = body),
            acceptedTerm = 3,
            lastAppliedSequence = 0,
            coordinatorNowNs = 1_101,
        )
        assertTrue(result is EnvelopeAcceptance.Rejected)
    }
}

private object TestSnapshots {
    fun basic(term: Long) = com.darius.unison.model.RoomSnapshot(
        roomId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        roomName = "Room",
        term = com.darius.unison.model.CoordinatorTerm(
            term,
            PeerId("11111111-1111-1111-1111-111111111111"),
        ),
        sequence = 0,
        members = listOf(
            com.darius.unison.model.MemberSnapshot(
                PeerId("11111111-1111-1111-1111-111111111111"),
                "Coordinator",
            )
        ),
    )
}
