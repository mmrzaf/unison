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
import com.darius.unison.protocol.ProtocolException
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64

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
    ): ConnectedControl = connect(
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
    ): ConnectedControl = connect(
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
    ): ConnectedControl = withContext(Dispatchers.IO) {
        val address = java.net.InetAddress.getByName(host)
        NetworkAddressPolicy.requireAllowed(address)
        val socket = Socket().apply {
            tcpNoDelay = true
            keepAlive = true
            connect(InetSocketAddress(address, port), 10_000)
            soTimeout = 15_000
        }
        try {
            val nonce = Crypto.randomBase64(18)
            val hello = HandshakeMessage.ClientHello(
                channel = ChannelType.CONTROL,
                peerId = identity.peerId,
                displayName = identity.displayName,
                appVersion = appVersion,
                protocolVersions = listOf(PROTOCOL_VERSION),
                listeningPort = listeningPort,
                roomId = roomId,
                clientNonce = nonce,
                pinProof = (credential as? Credential.Pin)?.let {
                    Crypto.pinProof(roomId, it.value, nonce)
                },
                reconnectProof = (credential as? Credential.RoomSecret)?.let {
                    Crypto.reconnectProof(it.value, roomId, identity.peerId.value, nonce)
                },
            )
            HandshakeCodec.write(socket.getOutputStream(), hello)
            when (val response = HandshakeCodec.read(socket.getInputStream())) {
                is HandshakeMessage.Rejected -> throw ProtocolException(response.reason)
                is HandshakeMessage.CoordinatorHello -> {
                    if (response.acceptedVersion != PROTOCOL_VERSION) throw ProtocolException("Protocol mismatch")
                    val unwrapKey = when (credential) {
                        is Credential.Pin -> {
                            if (response.credentialMode != ControlCredentialMode.PIN) {
                                throw ProtocolException("Unexpected credential mode")
                            }
                            Crypto.derivePinKey(roomId, credential.value, nonce)
                        }

                        is Credential.RoomSecret -> {
                            if (response.credentialMode != ControlCredentialMode.RECONNECT) {
                                throw ProtocolException("Unexpected credential mode")
                            }
                            Crypto.deriveReconnectKey(
                                credential.value,
                                roomId,
                                identity.peerId.value,
                                nonce,
                            )
                        }
                    }
                    val roomSecret = Crypto.decryptAesGcm(
                        key = unwrapKey,
                        ciphertext = Base64.getUrlDecoder().decode(response.encryptedRoomSecretBase64),
                        iv = Base64.getUrlDecoder().decode(response.roomSecretIvBase64),
                        associatedData = "$roomId:${identity.peerId.value}".encodeToByteArray(),
                    )
                    if (credential is Credential.RoomSecret && !Crypto.constantTimeEquals(roomSecret, credential.value)) {
                        throw ProtocolException("Reconnect credential changed")
                    }
                    val keys = Crypto.deriveControlSessionKeys(roomSecret, nonce, response.serverNonce)
                    socket.soTimeout = 0
                    val endpoint = PeerEndpoint(
                        peerId = response.coordinatorPeerId,
                        displayName = "Coordinator",
                        hostAddress = host,
                        port = port,
                        appVersion = appVersion,
                    )
                    val connection = ControlConnection(
                        peerId = response.coordinatorPeerId,
                        endpoint = endpoint,
                        socket = socket,
                        codec = FrameCodec(
                            writeKey = keys.clientToCoordinator,
                            readKey = keys.coordinatorToClient,
                            expectedRoomId = roomId,
                        ),
                        parentScope = scope,
                        log = log,
                        onEnvelope = onEnvelope,
                        onClosed = onClosed,
                    )
                    ConnectedControl(connection, roomSecret, response.term, response.coordinatorPeerId)
                }

                else -> throw ProtocolException("Unexpected handshake response")
            }
        } catch (error: Exception) {
            runCatching { socket.close() }
            throw error
        }
    }

    private sealed interface Credential {
        data class Pin(val value: String) : Credential
        data class RoomSecret(val value: ByteArray) : Credential
    }
}

data class ConnectedControl(
    val connection: ControlConnection,
    val roomSecret: ByteArray,
    val term: Long,
    val coordinatorPeerId: PeerId,
)
