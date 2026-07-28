package com.darius.unison.room

import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.PeerId
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.ProtocolBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMessageRouterTest {
    private val local = PeerId("peer-local-123456")
    private val remote = PeerId("peer-remote-12345")
    private var nextId = 0

    private fun envelope(body: ProtocolBody, sequence: Long?) = Envelope(
        roomId = "room-1234",
        term = 1,
        coordinatorPeerId = local,
        senderPeerId = local,
        sequence = sequence,
        messageId = "message-${nextId++}",
        sentAtElapsedNs = 1,
        body = body,
    )

    @Test
    fun coordinatorHandlesLocalCoordinatorTrafficWithoutSocket() {
        val handled = mutableListOf<ProtocolBody>()
        val router = RoomMessageRouter(
            localPeerId = { local },
            isCoordinator = { true },
            coordinatorTarget = { null },
            peerTargets = { emptyMap() },
            createEnvelope = { body, sequence -> envelope(body, sequence) },
            handleCoordinatorLocal = { handled += it },
            handleLocalEnvelope = {},
            onCoordinatorUnavailable = {},
        )
        val body = ProtocolBody.Heartbeat(3)
        kotlinx.coroutines.runBlocking { router.sendToCoordinator(body) }
        assertEquals(listOf(body), handled)
    }

    @Test
    fun failedGuaranteedSendClosesOnlyThatTarget() {
        val closed = mutableListOf<Throwable>()
        val router = RoomMessageRouter(
            localPeerId = { local },
            isCoordinator = { false },
            coordinatorTarget = { null },
            peerTargets = {
                mapOf(remote to RoomSendTarget(send = { false }, close = { closed += it }))
            },
            createEnvelope = { body, sequence -> envelope(body, sequence) },
            handleCoordinatorLocal = {},
            handleLocalEnvelope = {},
            onCoordinatorUnavailable = {},
        )
        kotlinx.coroutines.runBlocking { router.send(remote, ProtocolBody.Heartbeat(1)) }
        assertEquals(1, closed.size)
        assertTrue(closed.single().message!!.contains("queue is full"))
    }

    @Test
    fun canonicalBroadcastCarriesProvidedSequence() {
        val sent = mutableListOf<Envelope>()
        val router = RoomMessageRouter(
            localPeerId = { local },
            isCoordinator = { true },
            coordinatorTarget = { null },
            peerTargets = {
                mapOf(remote to RoomSendTarget(send = { sent += it; true }, close = {}))
            },
            createEnvelope = { body, sequence -> envelope(body, sequence) },
            handleCoordinatorLocal = {},
            handleLocalEnvelope = {},
            onCoordinatorUnavailable = {},
        )
        kotlinx.coroutines.runBlocking { router.broadcastCanonical(9, ProtocolBody.Heartbeat(9)) }
        assertEquals(9L, sent.single().sequence)
    }
}
