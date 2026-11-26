package com.darius.unison.network

import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.HandshakeRejectionCode
import java.net.Socket

/** One authenticated control link. Reads and writes whole envelopes. */
interface EnvelopeChannel {
    /** Remote host observed on the authenticated link; never participant-announced. */
    val remoteHostAddress: String

    /** Suspends until the next envelope arrives, or returns null at end of stream. */
    suspend fun receive(): Envelope?

    suspend fun send(envelope: Envelope)

    fun close(cause: Throwable?)
}

/**
 * A control link before authentication. The handshake exchanges plain messages; [upgrade] installs
 * the per-direction frame keys and turns the link into an [EnvelopeChannel].
 */
interface HandshakeChannel {
    val remoteHostAddress: String

    suspend fun read(): HandshakeMessage

    suspend fun write(message: HandshakeMessage)

    fun upgrade(writeKey: ByteArray, readKey: ByteArray, expectedRoomId: String): EnvelopeChannel

    fun close()
}

/**
 * The room's peer transport. The Android implementation owns real sockets; a JVM harness supplies
 * in-memory links that carry the same handshake and envelope flow.
 */
interface PeerNetwork {
    interface Handler {
        suspend fun admitControl(
            hello: HandshakeMessage.ControlHello,
            remoteAddress: String,
        ): ControlAdmission

        suspend fun onControlConnected(
            connection: ControlConnection,
            admittedRoomId: String,
            admittedSessionGeneration: Long,
        )

        suspend fun onFileConnection(socket: Socket, hello: HandshakeMessage.FileClientHello)
    }

    sealed interface ControlAdmission {
        data class Accepted(
            val response: HandshakeMessage.CoordinatorHello,
            val serverWriteKey: ByteArray,
            val serverReadKey: ByteArray,
            val endpoint: PeerEndpoint,
            val roomId: String,
            val sessionGeneration: Long,
            val onEnvelope: suspend (ControlConnection, Envelope) -> Unit,
            val onClosed: suspend (ControlConnection, Throwable?) -> Unit,
        ) : ControlAdmission

        data class PinChallenge(
            val response: HandshakeMessage.PinChallenge,
            val complete: suspend (HandshakeMessage.PinResponse) -> ControlAdmission,
        ) : ControlAdmission

        data class ReconnectChallenge(
            val response: HandshakeMessage.ReconnectChallenge,
            val complete: suspend (HandshakeMessage.ReconnectResponse) -> ControlAdmission,
        ) : ControlAdmission

        data class Rejected(val reason: String, val code: HandshakeRejectionCode) : ControlAdmission
    }

    val port: Int

    /** Starts accepting inbound control and file connections. Returns the bound port. */
    fun listen(handler: Handler): Int

    suspend fun connectWithPin(
        identity: LocalIdentity,
        roomId: String,
        host: String,
        port: Int,
        listeningPort: Int,
        pin: String,
        appVersion: String,
        onEnvelope: suspend (ControlConnection, Envelope) -> Unit,
        onClosed: suspend (ControlConnection, Throwable?) -> Unit,
    ): ConnectedControl

    suspend fun reconnectWithRoomSecret(
        identity: LocalIdentity,
        roomId: String,
        host: String,
        port: Int,
        listeningPort: Int,
        roomSecret: ByteArray,
        appVersion: String,
        onEnvelope: suspend (ControlConnection, Envelope) -> Unit,
        onClosed: suspend (ControlConnection, Throwable?) -> Unit,
    ): ConnectedControl

    fun stop()

    suspend fun stopAndJoin(timeoutMs: Long = 2_000L): Boolean
}

sealed interface Credential {
    data class Pin(val value: String) : Credential

    data class RoomSecret(val value: ByteArray) : Credential
}

data class ConnectedControl(
    val connection: ControlConnection,
    val roomSecret: ByteArray,
    val term: Long,
    val coordinatorPeerId: PeerId,
)
