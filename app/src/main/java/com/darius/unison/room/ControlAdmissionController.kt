package com.darius.unison.room

import android.os.SystemClock
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.network.ControlConnection
import com.darius.unison.network.PeerServer
import com.darius.unison.protocol.ControlCredentialMode
import com.darius.unison.protocol.Crypto
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.PROTOCOL_VERSION
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Base64

/** Authentication, admission limits, and directional session-key creation for control sockets. */
internal class ControlAdmissionController(
    private val snapshot: suspend () -> RoomSnapshot?,
    private val isCoordinator: () -> Boolean,
    private val localIdentity: () -> LocalIdentity,
    private val roomPin: () -> String?,
    private val roomSecret: () -> ByteArray?,
    private val onWarning: (String) -> Unit,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
    private val onEnvelope: suspend (com.darius.unison.model.PeerId, Envelope) -> Unit,
    private val onClosed: suspend (ControlConnection, Throwable?) -> Unit,
) {
    private data class CredentialResult(val mode: ControlCredentialMode, val unwrapKey: ByteArray)

    private val admissionGuard = AdmissionGuard()
    private val authenticationSlots = Semaphore(MAX_CONCURRENT_AUTHENTICATIONS)

    suspend fun admit(
        hello: HandshakeMessage.ClientHello,
        remoteAddress: String,
    ): PeerServer.ControlAdmission {
        val current = snapshot() ?: return rejected("Room is not active")
        if (!isCoordinator()) return rejected("Coordinator moved")
        if (hello.roomId != current.roomId) return rejected("Wrong room")
        if (PROTOCOL_VERSION !in hello.protocolVersions) return rejected("App versions are incompatible")
        val identity = localIdentity()
        if (hello.peerId == identity.peerId) {
            onWarning(
                "Rejected duplicated coordinator identity " +
                    "peer=${hello.peerId.value.take(8)} remote=$remoteAddress"
            )
            return rejected(IDENTITY_COLLISION_REASON)
        }
        validateHello(hello)?.let { return rejected(it) }

        val nowElapsedMs = elapsedRealtimeMs()
        val nonceKey = "${current.roomId}:${hello.peerId.value}:${hello.clientNonce}"
        admissionGuard.checkAndReserve(remoteAddress, nonceKey, nowElapsedMs)?.let {
            return rejected(it)
        }

        val credential = authenticate(current, hello)
        if (credential == null) {
            admissionGuard.recordFailure(remoteAddress, nowElapsedMs)
            return rejected("Room authentication failed")
        }
        admissionGuard.recordSuccess(remoteAddress)

        val isKnownPeer = current.members.any { it.peerId == hello.peerId }
        if (!isKnownPeer && current.members.count(MemberSnapshot::connected) >= MAX_ROOM_MEMBERS) {
            return rejected("Room is full")
        }

        val secret = roomSecret() ?: return rejected("Room is restarting")
        val serverNonce = Crypto.randomBase64(18)
        val endpoint = PeerEndpoint(
            peerId = hello.peerId,
            displayName = hello.displayName.trim().take(40).ifBlank { "Friend" },
            hostAddress = remoteAddress,
            port = hello.listeningPort,
            appVersion = hello.appVersion,
            lastSeenElapsedMs = elapsedRealtimeMs(),
        )
        val encryptedSecret = Crypto.encryptAesGcm(
            key = credential.unwrapKey,
            plaintext = secret,
            associatedData = "${current.roomId}:${hello.peerId.value}".encodeToByteArray(),
        )
        val response = HandshakeMessage.CoordinatorHello(
            acceptedVersion = PROTOCOL_VERSION,
            term = current.term.number,
            coordinatorPeerId = identity.peerId,
            serverNonce = serverNonce,
            encryptedRoomSecretBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(encryptedSecret.ciphertext),
            roomSecretIvBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(encryptedSecret.iv),
            credentialMode = credential.mode,
            snapshotSequence = current.sequence,
        )
        val keys = Crypto.deriveControlSessionKeys(secret, hello.clientNonce, serverNonce)
        return PeerServer.ControlAdmission.Accepted(
            response = response,
            serverWriteKey = keys.coordinatorToClient,
            serverReadKey = keys.clientToCoordinator,
            endpoint = endpoint,
            roomId = current.roomId,
            onEnvelope = onEnvelope,
            onClosed = onClosed,
        )
    }

    fun reset() {
        admissionGuard.reset()
    }

    private fun validateHello(hello: HandshakeMessage.ClientHello): String? = when {
        hello.peerId.value.length !in 16..128 || !HELLO_TOKEN_PATTERN.matches(hello.peerId.value) ->
            "Invalid peer identity"
        hello.appVersion.length !in 1..64 || hello.displayName.length > 160 ->
            "Invalid client metadata"
        hello.clientNonce.length !in 16..128 || !HELLO_TOKEN_PATTERN.matches(hello.clientNonce) ->
            "Invalid connection request"
        (hello.pinProof == null) == (hello.reconnectProof == null) ->
            "Exactly one control credential is required"
        hello.listeningPort !in 1..65535 -> "Invalid peer port"
        else -> null
    }

    private suspend fun authenticate(
        snapshot: RoomSnapshot,
        hello: HandshakeMessage.ClientHello,
    ): CredentialResult? = withTimeoutOrNull(AUTHENTICATION_TIMEOUT_MS) {
        authenticationSlots.withPermit {
            when {
                hello.pinProof != null -> {
                    val pin = roomPin() ?: return@withPermit null
                    val expected = Crypto.pinProof(snapshot.roomId, pin, hello.clientNonce)
                    if (!Crypto.constantTimeEquals(expected.encodeToByteArray(), hello.pinProof.encodeToByteArray())) {
                        null
                    } else {
                        CredentialResult(
                            ControlCredentialMode.PIN,
                            Crypto.derivePinKey(snapshot.roomId, pin, hello.clientNonce),
                        )
                    }
                }
                else -> {
                    val secret = roomSecret() ?: return@withPermit null
                    val expected = Crypto.reconnectProof(
                        secret,
                        snapshot.roomId,
                        hello.peerId.value,
                        hello.clientNonce,
                    )
                    if (!Crypto.constantTimeEquals(expected.encodeToByteArray(), hello.reconnectProof!!.encodeToByteArray())) {
                        null
                    } else {
                        CredentialResult(
                            ControlCredentialMode.RECONNECT,
                            Crypto.deriveReconnectKey(secret, snapshot.roomId, hello.peerId.value, hello.clientNonce),
                        )
                    }
                }
            }
        }
    }

    private fun rejected(reason: String) = PeerServer.ControlAdmission.Rejected(reason)

    private companion object {
        const val IDENTITY_COLLISION_REASON = "Cannot join yourself"
        const val MAX_ROOM_MEMBERS = 8
        const val MAX_CONCURRENT_AUTHENTICATIONS = 3
        const val AUTHENTICATION_TIMEOUT_MS = 8_000L
        val HELLO_TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]+$")
    }
}
