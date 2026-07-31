package com.darius.unison.network

import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.protocol.ChannelType
import com.darius.unison.protocol.ControlCredentialMode
import com.darius.unison.protocol.Crypto
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.FrameCodec
import com.darius.unison.protocol.HandshakeCodec
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.protocol.PinPake
import com.darius.unison.protocol.ProtocolException
import com.darius.unison.util.DiagnosticLog
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

class ControlClient(
    private val scope: CoroutineScope,
    private val log: DiagnosticLog,
) {
    suspend fun connectWithPin(
        identity: LocalIdentity,
        roomId: String,
        host: String,
        port: Int,
        listeningPort: Int,
        pin: String,
        appVersion: String,
        onEnvelope: suspend (PeerId, Envelope) -> Unit,
        onClosed: suspend (ControlConnection, Throwable?) -> Unit,
    ): ConnectedControl =
        connect(
            identity = identity,
            roomId = roomId,
            host = host,
            port = port,
            listeningPort = listeningPort,
            credential = Credential.Pin(pin),
            appVersion = appVersion,
            onEnvelope = onEnvelope,
            onClosed = onClosed,
        )

    suspend fun reconnectWithRoomSecret(
        identity: LocalIdentity,
        roomId: String,
        host: String,
        port: Int,
        listeningPort: Int,
        roomSecret: ByteArray,
        appVersion: String,
        onEnvelope: suspend (PeerId, Envelope) -> Unit,
        onClosed: suspend (ControlConnection, Throwable?) -> Unit,
    ): ConnectedControl =
        connect(
            identity = identity,
            roomId = roomId,
            host = host,
            port = port,
            listeningPort = listeningPort,
            credential = Credential.RoomSecret(roomSecret.copyOf()),
            appVersion = appVersion,
            onEnvelope = onEnvelope,
            onClosed = onClosed,
        )

    private suspend fun connect(
        identity: LocalIdentity,
        roomId: String,
        host: String,
        port: Int,
        listeningPort: Int,
        credential: Credential,
        appVersion: String,
        onEnvelope: suspend (PeerId, Envelope) -> Unit,
        onClosed: suspend (ControlConnection, Throwable?) -> Unit,
    ): ConnectedControl =
        try {
            runInterruptible(Dispatchers.IO) {
                val address = java.net.InetAddress.getByName(host)
                NetworkAddressPolicy.requireAllowed(address)
                val socket =
                    Socket().apply {
                        tcpNoDelay = true
                        keepAlive = true
                        connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                        soTimeout = HANDSHAKE_TIMEOUT_MS
                    }
                try {
                    val nonce = Crypto.randomBase64(18)
                    val pinSession =
                        (credential as? Credential.Pin)?.let {
                            PinPake.ClientSession.start(
                                roomId,
                                identity.peerId.value,
                                nonce,
                                it.value,
                            )
                        }
                    val hello =
                        HandshakeMessage.ClientHello(
                            channel = ChannelType.CONTROL,
                            peerId = identity.peerId,
                            displayName = identity.displayName,
                            appVersion = appVersion,
                            protocolVersions = listOf(PROTOCOL_VERSION),
                            listeningPort = listeningPort,
                            roomId = roomId,
                            clientNonce = nonce,
                            pinPublicValueBase64 = pinSession?.publicValueBase64,
                            reconnectProof =
                                (credential as? Credential.RoomSecret)?.let {
                                    Crypto.reconnectProof(
                                        it.value,
                                        roomId,
                                        identity.peerId.value,
                                        nonce,
                                    )
                                },
                        )
                    HandshakeCodec.write(socket.getOutputStream(), hello)

                    var pinAnswer: PinPake.ClientProof? = null
                    val firstResponse = HandshakeCodec.read(socket.getInputStream())
                    val finalResponse =
                        when (firstResponse) {
                            is HandshakeMessage.PinChallenge -> {
                                val session =
                                    pinSession
                                        ?: throw ProtocolException("Unexpected PIN challenge")
                                val answer =
                                    session.answer(
                                        PinPake.Challenge(
                                            saltBase64 = firstResponse.saltBase64,
                                            serverPublicValueBase64 =
                                                firstResponse.serverPublicValueBase64,
                                            serverNonce = firstResponse.serverNonce,
                                        )
                                    )
                                pinAnswer = answer
                                HandshakeCodec.write(
                                    socket.getOutputStream(),
                                    HandshakeMessage.PinResponse(answer.proofBase64),
                                )
                                HandshakeCodec.read(socket.getInputStream())
                            }

                            else -> firstResponse
                        }

                    try {
                        when (finalResponse) {
                            is HandshakeMessage.Rejected ->
                                throw ProtocolException(
                                    finalResponse.reason,
                                    rejectionCode = finalResponse.code,
                                )

                            is HandshakeMessage.CoordinatorHello -> {
                                if (finalResponse.acceptedVersion != PROTOCOL_VERSION) {
                                    throw ProtocolException("Protocol mismatch")
                                }
                                val unwrapKey =
                                    when (credential) {
                                        is Credential.Pin -> {
                                            if (
                                                finalResponse.credentialMode !=
                                                    ControlCredentialMode.PIN
                                            ) {
                                                throw ProtocolException(
                                                    "Unexpected credential mode"
                                                )
                                            }
                                            val answer =
                                                pinAnswer
                                                    ?: throw ProtocolException(
                                                        "Missing PIN challenge"
                                                    )
                                            val serverProof =
                                                finalResponse.pinServerProofBase64
                                                    ?: throw ProtocolException(
                                                        "Missing PIN server proof"
                                                    )
                                            if (
                                                !PinPake.verifyServerProof(
                                                    answer.expectedServerProofBase64,
                                                    serverProof,
                                                )
                                            ) {
                                                throw ProtocolException(
                                                    "Room authentication failed"
                                                )
                                            }
                                            answer.sessionKey
                                        }

                                        is Credential.RoomSecret -> {
                                            if (
                                                firstResponse is HandshakeMessage.PinChallenge ||
                                                    pinAnswer != null
                                            ) {
                                                throw ProtocolException("Unexpected PIN exchange")
                                            }
                                            if (
                                                finalResponse.credentialMode !=
                                                    ControlCredentialMode.RECONNECT
                                            ) {
                                                throw ProtocolException(
                                                    "Unexpected credential mode"
                                                )
                                            }
                                            Crypto.deriveReconnectKey(
                                                credential.value,
                                                roomId,
                                                identity.peerId.value,
                                                nonce,
                                            )
                                        }
                                    }
                                val ciphertext =
                                    runCatching {
                                            Base64.getUrlDecoder()
                                                .decode(finalResponse.encryptedRoomSecretBase64)
                                        }
                                        .getOrElse {
                                            throw ProtocolException("Invalid room secret")
                                        }
                                val iv =
                                    runCatching {
                                            Base64.getUrlDecoder()
                                                .decode(finalResponse.roomSecretIvBase64)
                                        }
                                        .getOrElse {
                                            ciphertext.fill(0)
                                            throw ProtocolException("Invalid room secret")
                                        }
                                val associatedData =
                                    "$roomId:${identity.peerId.value}".encodeToByteArray()
                                val roomSecret =
                                    try {
                                        Crypto.decryptAesGcm(
                                            key = unwrapKey,
                                            ciphertext = ciphertext,
                                            iv = iv,
                                            associatedData = associatedData,
                                        )
                                    } finally {
                                        unwrapKey.fill(0)
                                        ciphertext.fill(0)
                                        iv.fill(0)
                                        associatedData.fill(0)
                                    }
                                if (
                                    credential is Credential.RoomSecret &&
                                        !Crypto.constantTimeEquals(roomSecret, credential.value)
                                ) {
                                    roomSecret.fill(0)
                                    throw ProtocolException("Reconnect credential changed")
                                }
                                val keys =
                                    Crypto.deriveControlSessionKeys(
                                        roomSecret,
                                        nonce,
                                        finalResponse.serverNonce,
                                    )
                                socket.soTimeout = 0
                                val endpoint =
                                    PeerEndpoint(
                                        peerId = finalResponse.coordinatorPeerId,
                                        displayName = "Coordinator",
                                        hostAddress = host,
                                        port = port,
                                        appVersion = appVersion,
                                    )
                                val codec =
                                    try {
                                        FrameCodec(
                                            writeKey = keys.clientToCoordinator,
                                            readKey = keys.coordinatorToClient,
                                            expectedRoomId = roomId,
                                        )
                                    } finally {
                                        keys.clientToCoordinator.fill(0)
                                        keys.coordinatorToClient.fill(0)
                                    }
                                val connection =
                                    try {
                                        ControlConnection(
                                            peerId = finalResponse.coordinatorPeerId,
                                            endpoint = endpoint,
                                            socket = socket,
                                            codec = codec,
                                            parentScope = scope,
                                            log = log,
                                            onEnvelope = onEnvelope,
                                            onClosed = onClosed,
                                        )
                                    } catch (error: Exception) {
                                        codec.close()
                                        throw error
                                    }
                                ConnectedControl(
                                    connection = connection,
                                    roomSecret = roomSecret,
                                    term = finalResponse.term,
                                    coordinatorPeerId = finalResponse.coordinatorPeerId,
                                )
                            }

                            else -> throw ProtocolException("Unexpected handshake response")
                        }
                    } finally {
                        pinAnswer?.sessionKey?.fill(0)
                    }
                } catch (error: Exception) {
                    runCatching { socket.close() }
                    throw error
                }
            }
        } finally {
            (credential as? Credential.RoomSecret)?.value?.fill(0)
        }

    private sealed interface Credential {
        data class Pin(val value: String) : Credential

        data class RoomSecret(val value: ByteArray) : Credential
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 4_500
        const val HANDSHAKE_TIMEOUT_MS = 10_000
    }
}

data class ConnectedControl(
    val connection: ControlConnection,
    val roomSecret: ByteArray,
    val term: Long,
    val coordinatorPeerId: PeerId,
)
