package com.darius.unison.room

import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.network.PeerServer
import com.darius.unison.protocol.ChannelType
import com.darius.unison.protocol.Crypto
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.PROTOCOL_VERSION
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlAdmissionControllerTest {
    private val local = LocalIdentity(PeerId("peer-local-123456"), "Coordinator")
    private val guest = PeerId("peer-guest-123456")
    private val roomId = "room-1234"
    private val pin = "123456"
    private val secret = ByteArray(32) { it.toByte() }
    private val snapshot = RoomSnapshot(
        roomId = roomId,
        roomName = "Room",
        term = CoordinatorTerm(1, local.peerId),
        sequence = 4,
        members = listOf(MemberSnapshot(local.peerId, local.displayName)),
    )

    private fun controller() = ControlAdmissionController(
        snapshot = { snapshot },
        isCoordinator = { true },
        localIdentity = { local },
        roomPin = { pin },
        roomSecret = { secret },
        onWarning = {},
        elapsedRealtimeMs = { 1_000L },
        onEnvelope = { _, _ -> },
        onClosed = { _, _ -> },
    )

    @Test
    fun validPinProofCreatesDirectionalSessionKeys() = runBlocking {
        val nonce = Crypto.randomBase64(18)
        val hello = HandshakeMessage.ClientHello(
            channel = ChannelType.CONTROL,
            peerId = guest,
            displayName = "Guest",
            appVersion = "1.0.0",
            protocolVersions = listOf(PROTOCOL_VERSION),
            listeningPort = 4321,
            roomId = roomId,
            clientNonce = nonce,
            pinProof = Crypto.pinProof(roomId, pin, nonce),
        )
        val result = controller().admit(hello, "192.168.1.2") as PeerServer.ControlAdmission.Accepted
        assertEquals(guest, result.endpoint.peerId)
        assertTrue(!result.serverWriteKey.contentEquals(result.serverReadKey))
    }

    @Test
    fun malformedHelloIsRejectedBeforeAuthentication() = runBlocking {
        val hello = HandshakeMessage.ClientHello(
            channel = ChannelType.CONTROL,
            peerId = PeerId("short"),
            displayName = "Guest",
            appVersion = "1.0.0",
            protocolVersions = listOf(PROTOCOL_VERSION),
            listeningPort = 4321,
            roomId = roomId,
            clientNonce = Crypto.randomBase64(18),
            pinProof = "invalid",
        )
        val result = controller().admit(hello, "192.168.1.2") as PeerServer.ControlAdmission.Rejected
        assertEquals("Invalid peer identity", result.reason)
    }
}
