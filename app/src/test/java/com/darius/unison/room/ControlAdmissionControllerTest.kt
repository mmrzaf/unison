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
            HandshakeMessage.ClientHello(
                channel = ChannelType.CONTROL,
                peerId = guest,
                displayName = "Guest",
                appVersion = "1.0.0",
                protocolVersions = listOf(PROTOCOL_VERSION),
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
            HandshakeMessage.ClientHello(
                channel = ChannelType.CONTROL,
                peerId = guest,
                displayName = "Guest",
                appVersion = "1.0.0",
                protocolVersions = listOf(PROTOCOL_VERSION),
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
            HandshakeMessage.ClientHello(
                channel = ChannelType.CONTROL,
                peerId = PeerId("short"),
                displayName = "Guest",
                appVersion = "1.0.0",
                protocolVersions = listOf(PROTOCOL_VERSION),
                listeningPort = 4321,
                roomId = roomId,
                clientNonce = Crypto.randomBase64(18),
                pinPublicValueBase64 = "invalid",
            )
        val result =
            controller().admit(hello, "192.168.1.2") as PeerServer.ControlAdmission.Rejected
        assertEquals("Invalid peer identity", result.reason)
    }
}
