package com.darius.unison.room

import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.network.PeerServer
import com.darius.unison.protocol.Crypto
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.protocol.PinPake
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ControlAdmissionControllerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var log: DiagnosticLog
    private val local = LocalIdentity(PeerId("peer-local-123456"), "Coordinator")
    private val guest = PeerId("peer-guest-123456")
    private val roomId = "room-1234"
    private val pin = "1234"
    private val secret = ByteArray(32) { it.toByte() }
    private var sessionGeneration = 7L
    private val snapshot =
        RoomSnapshot(
            roomId = roomId,
            roomName = "Room",
            term = CoordinatorTerm(1, local.peerId),
            sequence = 4,
            members = listOf(MemberSnapshot(local.peerId, local.displayName)),
        )

    @Before
    fun setUp() {
        sessionGeneration = 7L
        log = DiagnosticLog(temporaryFolder.newFile("diagnostics.log"))
    }

    @After
    fun tearDown() {
        log.close()
    }

    private fun controller() =
        ControlAdmissionController(
            snapshot = { snapshot },
            isCoordinator = { true },
            localIdentity = { local },
            roomPin = { pin },
            roomSecret = { secret },
            sessionGeneration = { sessionGeneration },
            log = log,
            onEnvelope = { _, _ -> },
            onClosed = { _, _ -> },
            elapsedRealtimeMs = { 1_000L },
        )

    @Test
    fun validPinExchangeCreatesDirectionalSessionKeys() = runBlocking {
        val nonce = Crypto.randomBase64(18)
        val clientSession = PinPake.ClientSession.start(roomId, guest.value, nonce, pin)
        val hello =
            HandshakeMessage.PinClientHello(
                peerId = guest,
                displayName = "Guest",
                appVersion = "1.0.0",
                protocolVersion = PROTOCOL_VERSION,
                listeningPort = 4321,
                roomId = roomId,
                clientNonce = nonce,
                pinPublicValueBase64 = clientSession.publicValueBase64,
            )
        val challengeAdmission =
            controller().admit(hello, "192.168.1.2") as PeerServer.ControlAdmission.PinChallenge
        val challenge = challengeAdmission.response
        val answer =
            clientSession.answer(
                PinPake.Challenge(
                    challenge.saltBase64,
                    challenge.serverPublicValueBase64,
                    challenge.serverNonce,
                )
            )
        val result =
            challengeAdmission.complete(HandshakeMessage.PinResponse(answer.proofBase64))
                as PeerServer.ControlAdmission.Accepted
        assertEquals(guest, result.endpoint.peerId)
        assertEquals(roomId, result.roomId)
        assertEquals(7L, result.sessionGeneration)
        assertTrue(!result.serverWriteKey.contentEquals(result.serverReadKey))
        assertTrue(
            PinPake.verifyServerProof(
                answer.expectedServerProofBase64,
                checkNotNull(result.response.pinServerProofBase64),
            )
        )
        answer.sessionKey.fill(0)
    }

    @Test
    fun wrongPinIsRejectedAfterChallenge() = runBlocking {
        val nonce = Crypto.randomBase64(18)
        val clientSession = PinPake.ClientSession.start(roomId, guest.value, nonce, "9999")
        val hello =
            HandshakeMessage.PinClientHello(
                peerId = guest,
                displayName = "Guest",
                appVersion = "1.0.0",
                protocolVersion = PROTOCOL_VERSION,
                listeningPort = 4321,
                roomId = roomId,
                clientNonce = nonce,
                pinPublicValueBase64 = clientSession.publicValueBase64,
            )
        val challengeAdmission =
            controller().admit(hello, "192.168.1.2") as PeerServer.ControlAdmission.PinChallenge
        val challenge = challengeAdmission.response
        val answer =
            clientSession.answer(
                PinPake.Challenge(
                    challenge.saltBase64,
                    challenge.serverPublicValueBase64,
                    challenge.serverNonce,
                )
            )
        val result = challengeAdmission.complete(HandshakeMessage.PinResponse(answer.proofBase64))
        assertTrue(result is PeerServer.ControlAdmission.Rejected)
        answer.sessionKey.fill(0)
    }

    @Test
    fun malformedHelloIsRejectedBeforeAuthentication() = runBlocking {
        val hello =
            HandshakeMessage.PinClientHello(
                peerId = PeerId("short"),
                displayName = "Guest",
                appVersion = "1.0.0",
                protocolVersion = PROTOCOL_VERSION,
                listeningPort = 4321,
                roomId = roomId,
                clientNonce = Crypto.randomBase64(18),
                pinPublicValueBase64 = "invalid",
            )
        val result =
            controller().admit(hello, "192.168.1.2") as PeerServer.ControlAdmission.Rejected
        assertEquals("Invalid peer identity", result.reason)
    }

    @Test
    fun reconnectRequiresFreshServerChallenge() = runBlocking {
        val nonce = Crypto.randomBase64(18)
        val hello =
            HandshakeMessage.ReconnectClientHello(
                peerId = guest,
                displayName = "Guest",
                appVersion = "1.0.0",
                protocolVersion = PROTOCOL_VERSION,
                listeningPort = 4321,
                roomId = roomId,
                clientNonce = nonce,
            )
        val first =
            controller().admit(hello, "192.168.1.2")
                as PeerServer.ControlAdmission.ReconnectChallenge
        val capturedProof =
            Crypto.reconnectProof(
                secret,
                roomId,
                guest.value,
                nonce,
                first.response.serverNonce,
            )
        val accepted =
            first.complete(HandshakeMessage.ReconnectResponse(capturedProof))
                as PeerServer.ControlAdmission.Accepted
        assertEquals(first.response.serverNonce, accepted.response.serverNonce)

        // Even after replay bookkeeping is gone, a captured response cannot answer a new challenge.
        val replay =
            controller().admit(hello, "192.168.1.2")
                as PeerServer.ControlAdmission.ReconnectChallenge
        val rejected = replay.complete(HandshakeMessage.ReconnectResponse(capturedProof))
        assertTrue(rejected is PeerServer.ControlAdmission.Rejected)
        accepted.serverWriteKey.fill(0)
        accepted.serverReadKey.fill(0)
    }

    @Test
    fun oldProtocolIsRejectedBeforeAuthentication() = runBlocking {
        val hello =
            HandshakeMessage.ReconnectClientHello(
                peerId = guest,
                displayName = "Guest",
                appVersion = "1.0.0",
                protocolVersion = PROTOCOL_VERSION - 1,
                listeningPort = 4321,
                roomId = roomId,
                clientNonce = Crypto.randomBase64(18),
            )

        val result =
            controller().admit(hello, "192.168.1.2") as PeerServer.ControlAdmission.Rejected
        assertEquals(
            com.darius.unison.protocol.HandshakeRejectionCode.PROTOCOL_MISMATCH,
            result.code,
        )
    }

    @Test
    fun mismatchedProtocolIsRejectedBeforeAuthentication() = runBlocking {
        val hello =
            HandshakeMessage.ReconnectClientHello(
                peerId = guest,
                displayName = "Guest",
                appVersion = "1.0.0",
                protocolVersion = PROTOCOL_VERSION + 1,
                listeningPort = 4321,
                roomId = roomId,
                clientNonce = Crypto.randomBase64(18),
            )

        val result =
            controller().admit(hello, "192.168.1.2") as PeerServer.ControlAdmission.Rejected
        assertEquals(
            com.darius.unison.protocol.HandshakeRejectionCode.PROTOCOL_MISMATCH,
            result.code,
        )
    }

    @Test
    fun acceptedAdmissionCapturesGenerationAtFinalAcceptance() = runBlocking {
        val nonce = Crypto.randomBase64(18)
        val clientSession = PinPake.ClientSession.start(roomId, guest.value, nonce, pin)
        val hello =
            HandshakeMessage.PinClientHello(
                peerId = guest,
                displayName = "Guest",
                appVersion = "1.0.0",
                protocolVersion = PROTOCOL_VERSION,
                listeningPort = 4321,
                roomId = roomId,
                clientNonce = nonce,
                pinPublicValueBase64 = clientSession.publicValueBase64,
            )
        val challengeAdmission =
            controller().admit(hello, "192.168.1.2") as PeerServer.ControlAdmission.PinChallenge
        val challenge = challengeAdmission.response
        val answer =
            clientSession.answer(
                PinPake.Challenge(
                    challenge.saltBase64,
                    challenge.serverPublicValueBase64,
                    challenge.serverNonce,
                )
            )

        sessionGeneration = 8L
        val accepted =
            challengeAdmission.complete(HandshakeMessage.PinResponse(answer.proofBase64))
                as PeerServer.ControlAdmission.Accepted

        assertEquals(roomId, accepted.roomId)
        assertEquals(8L, accepted.sessionGeneration)
        answer.sessionKey.fill(0)
        accepted.serverWriteKey.fill(0)
        accepted.serverReadKey.fill(0)
    }
}
