package com.darius.unison.network

import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.protocol.ChannelType
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
    suspend fun connect(
        identity: LocalIdentity,
        roomId: String,
        host: String,
        port: Int,
        listeningPort: Int,
        pin: String,
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
                pinProof = Crypto.pinProof(roomId, pin, nonce),
            )
            HandshakeCodec.write(socket.getOutputStream(), hello)
            when (val response = HandshakeCodec.read(socket.getInputStream())) {
                is HandshakeMessage.Rejected -> throw ProtocolException(response.reason)
                is HandshakeMessage.CoordinatorHello -> {
                    if (response.acceptedVersion != PROTOCOL_VERSION) throw ProtocolException("Protocol mismatch")
                    val pinKey = Crypto.derivePinKey(roomId, pin, nonce)
                    val roomSecret = Crypto.decryptAesGcm(
                        key = pinKey,
                        ciphertext = Base64.getUrlDecoder().decode(response.encryptedRoomSecretBase64),
                        iv = Base64.getUrlDecoder().decode(response.roomSecretIvBase64),
                        associatedData = "$roomId:${identity.peerId.value}".encodeToByteArray(),
                    )
                    val sessionKey = Crypto.deriveSessionKey(roomSecret, nonce, response.serverNonce)
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
                        codec = FrameCodec(sessionKey, roomId),
                        parentScope = scope,
                        log = log,
                        onEnvelope = onEnvelope,
                        onClosed = onClosed,
                    )
                    ConnectedControl(connection, roomSecret, response.term, response.coordinatorPeerId)
                }

                else -> throw ProtocolException("Unexpected handshake response")
            }
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
    }
}

data class ConnectedControl(
    val connection: ControlConnection,
    val roomSecret: ByteArray,
    val term: Long,
    val coordinatorPeerId: PeerId,
)
