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
import com.darius.unison.protocol.HandshakeRejectionCode
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.protocol.PinPake
import com.darius.unison.util.DiagnosticLog
import java.util.Base64
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Authentication, admission limits, and directional session-key creation for control sockets. */
internal class ControlAdmissionController(
    private val snapshot: suspend () -> RoomSnapshot?,
    private val isCoordinator: () -> Boolean,
    private val localIdentity: () -> LocalIdentity,
    private val roomPin: () -> String?,
    private val roomSecret: () -> ByteArray?,
    private val log: DiagnosticLog,
    private val onEnvelope: suspend (com.darius.unison.model.PeerId, Envelope) -> Unit,
    private val onClosed: suspend (ControlConnection, Throwable?) -> Unit,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private data class CredentialResult(
        val mode: ControlCredentialMode,
        val unwrapKey: ByteArray,
        val pinServerProofBase64: String? = null,
        val serverNonce: String? = null,
    )

    private sealed interface ValidationResult {
        data class Valid(val snapshot: RoomSnapshot) : ValidationResult

        data class Invalid(val rejection: PeerServer.ControlAdmission.Rejected) : ValidationResult
    }

    private val admissionGuard = AdmissionGuard()
    private val authenticationSlots = Semaphore(MAX_CONCURRENT_AUTHENTICATIONS)

    suspend fun admit(
        hello: HandshakeMessage.ControlHello,
        remoteAddress: String,
    ): PeerServer.ControlAdmission {
        val current =
            when (val validation = validateRoomAndHello(hello, remoteAddress)) {
                is ValidationResult.Valid -> validation.snapshot
                is ValidationResult.Invalid -> return validation.rejection
            }
        val nowElapsedMs = elapsedRealtimeMs()
        val nonceKey = "${current.roomId}:${hello.peerId.value}:${hello.clientNonce}"
        admissionGuard.checkAndReserve(remoteAddress, nonceKey, nowElapsedMs)?.let {
            return rejected(HandshakeRejectionCode.RATE_LIMITED, it)
        }

        return when (hello) {
            is HandshakeMessage.PinClientHello ->
                beginPinAdmission(current, hello, remoteAddress, nowElapsedMs)
            is HandshakeMessage.ReconnectClientHello ->
                beginReconnectAdmission(current, hello, remoteAddress)
        }
    }

    fun reset() {
        admissionGuard.reset()
    }

    private suspend fun validateRoomAndHello(
        hello: HandshakeMessage.ControlHello,
        remoteAddress: String,
    ): ValidationResult {
        val current =
            snapshot()
                ?: return ValidationResult.Invalid(
                    rejected(HandshakeRejectionCode.ROOM_INACTIVE, "Room is not active")
                )
        if (!isCoordinator()) {
            return ValidationResult.Invalid(
                rejected(HandshakeRejectionCode.COORDINATOR_MOVED, "Coordinator moved")
            )
        }
        if (hello.roomId != current.roomId) {
            return ValidationResult.Invalid(
                rejected(HandshakeRejectionCode.WRONG_ROOM, "Wrong room")
            )
        }
        if (hello.protocolVersion != PROTOCOL_VERSION) {
            return ValidationResult.Invalid(
                rejected(HandshakeRejectionCode.PROTOCOL_MISMATCH, "App versions are incompatible")
            )
        }
        val identity = localIdentity()
        if (hello.peerId == identity.peerId) {
            log.w(
                TAG,
                "Rejected duplicated coordinator identity peer=${hello.peerId.value.take(8)} remote=$remoteAddress",
            )
            return ValidationResult.Invalid(
                rejected(HandshakeRejectionCode.IDENTITY_COLLISION, IDENTITY_COLLISION_REASON)
            )
        }
        validateHello(hello)?.let { reason ->
            return ValidationResult.Invalid(
                rejected(HandshakeRejectionCode.INVALID_REQUEST, reason)
            )
        }
        return ValidationResult.Valid(current)
    }

    private suspend fun beginPinAdmission(
        current: RoomSnapshot,
        hello: HandshakeMessage.PinClientHello,
        remoteAddress: String,
        startedAtElapsedMs: Long,
    ): PeerServer.ControlAdmission {
        val pin =
            roomPin() ?: return rejected(HandshakeRejectionCode.ROOM_INACTIVE, "Room is restarting")
        val serverSession =
            withTimeoutOrNull(AUTHENTICATION_TIMEOUT_MS) {
                authenticationSlots.withPermit {
                    runCatching {
                            PinPake.ServerSession.start(
                                roomId = current.roomId,
                                peerId = hello.peerId.value,
                                clientNonce = hello.clientNonce,
                                pin = pin,
                                clientPublicValueBase64 = hello.pinPublicValueBase64,
                            )
                        }
                        .getOrNull()
                }
            }
                ?: run {
                    admissionGuard.recordFailure(remoteAddress, startedAtElapsedMs)
                    return rejected(
                        HandshakeRejectionCode.AUTHENTICATION_FAILED,
                        AUTHENTICATION_FAILURE_REASON,
                    )
                }

        return PeerServer.ControlAdmission.PinChallenge(
            response =
                HandshakeMessage.PinChallenge(
                    saltBase64 = serverSession.challenge.saltBase64,
                    serverPublicValueBase64 = serverSession.challenge.serverPublicValueBase64,
                    serverNonce = serverSession.challenge.serverNonce,
                ),
            complete = { response ->
                val proof =
                    withTimeoutOrNull(AUTHENTICATION_TIMEOUT_MS) {
                        authenticationSlots.withPermit {
                            serverSession.verify(response.proofBase64)
                        }
                    }
                if (proof == null) {
                    admissionGuard.recordFailure(remoteAddress, elapsedRealtimeMs())
                    rejected(
                        HandshakeRejectionCode.AUTHENTICATION_FAILED,
                        AUTHENTICATION_FAILURE_REASON,
                    )
                } else {
                    admissionGuard.recordSuccess(remoteAddress)
                    buildAccepted(
                        hello = hello,
                        remoteAddress = remoteAddress,
                        credential =
                            CredentialResult(
                                mode = ControlCredentialMode.PIN,
                                unwrapKey = proof.sessionKey,
                                pinServerProofBase64 = proof.proofBase64,
                            ),
                    )
                }
            },
        )
    }

    private suspend fun beginReconnectAdmission(
        current: RoomSnapshot,
        hello: HandshakeMessage.ReconnectClientHello,
        remoteAddress: String,
    ): PeerServer.ControlAdmission {
        val secret =
            roomSecret()
                ?: return rejected(HandshakeRejectionCode.ROOM_INACTIVE, "Room is restarting")
        val serverNonce = Crypto.randomBase64(18)
        return PeerServer.ControlAdmission.ReconnectChallenge(
            response = HandshakeMessage.ReconnectChallenge(serverNonce),
            complete = { response ->
                val credential =
                    withTimeoutOrNull(AUTHENTICATION_TIMEOUT_MS) {
                        authenticationSlots.withPermit {
                            val activeSecret = roomSecret()
                            if (
                                activeSecret == null ||
                                    !Crypto.constantTimeEquals(secret, activeSecret)
                            ) {
                                return@withPermit null
                            }
                            val expected =
                                Crypto.reconnectProof(
                                    secret,
                                    current.roomId,
                                    hello.peerId.value,
                                    hello.clientNonce,
                                    serverNonce,
                                )
                            if (!constantTimeStringEquals(expected, response.proofBase64)) {
                                null
                            } else {
                                CredentialResult(
                                    mode = ControlCredentialMode.RECONNECT,
                                    unwrapKey =
                                        Crypto.deriveReconnectKey(
                                            secret,
                                            current.roomId,
                                            hello.peerId.value,
                                            hello.clientNonce,
                                            serverNonce,
                                        ),
                                    serverNonce = serverNonce,
                                )
                            }
                        }
                    }
                if (credential == null) {
                    admissionGuard.recordFailure(remoteAddress, elapsedRealtimeMs())
                    rejected(
                        HandshakeRejectionCode.AUTHENTICATION_FAILED,
                        AUTHENTICATION_FAILURE_REASON,
                    )
                } else {
                    admissionGuard.recordSuccess(remoteAddress)
                    buildAccepted(hello, remoteAddress, credential)
                }
            },
        )
    }

    private suspend fun buildAccepted(
        hello: HandshakeMessage.ControlHello,
        remoteAddress: String,
        credential: CredentialResult,
    ): PeerServer.ControlAdmission {
        try {
            val current =
                snapshot()
                    ?: return rejected(HandshakeRejectionCode.ROOM_INACTIVE, "Room is not active")
            if (!isCoordinator())
                return rejected(HandshakeRejectionCode.COORDINATOR_MOVED, "Coordinator moved")
            if (hello.roomId != current.roomId)
                return rejected(HandshakeRejectionCode.WRONG_ROOM, "Wrong room")
            val identity = localIdentity()
            val isKnownPeer = current.members.any { it.peerId == hello.peerId }
            if (
                !isKnownPeer && current.members.count(MemberSnapshot::connected) >= MAX_ROOM_MEMBERS
            ) {
                return rejected(HandshakeRejectionCode.ROOM_FULL, "Room is full")
            }

            val secret =
                roomSecret()
                    ?: return rejected(HandshakeRejectionCode.ROOM_INACTIVE, "Room is restarting")
            val serverNonce = credential.serverNonce ?: Crypto.randomBase64(18)
            val endpoint =
                PeerEndpoint(
                    peerId = hello.peerId,
                    displayName = hello.displayName.trim().take(40).ifBlank { "Friend" },
                    hostAddress = remoteAddress,
                    port = hello.listeningPort,
                    appVersion = hello.appVersion,
                    lastSeenElapsedMs = elapsedRealtimeMs(),
                )
            val associatedData = "${current.roomId}:${hello.peerId.value}".encodeToByteArray()
            val encryptedSecret =
                try {
                    Crypto.encryptAesGcm(
                        key = credential.unwrapKey,
                        plaintext = secret,
                        associatedData = associatedData,
                    )
                } finally {
                    associatedData.fill(0)
                }
            val encryptedRoomSecretBase64 =
                Base64.getUrlEncoder().withoutPadding().encodeToString(encryptedSecret.ciphertext)
            val roomSecretIvBase64 =
                Base64.getUrlEncoder().withoutPadding().encodeToString(encryptedSecret.iv)
            encryptedSecret.ciphertext.fill(0)
            encryptedSecret.iv.fill(0)
            val response =
                HandshakeMessage.CoordinatorHello(
                    protocolVersion = PROTOCOL_VERSION,
                    term = current.term.number,
                    coordinatorPeerId = identity.peerId,
                    serverNonce = serverNonce,
                    encryptedRoomSecretBase64 = encryptedRoomSecretBase64,
                    roomSecretIvBase64 = roomSecretIvBase64,
                    credentialMode = credential.mode,
                    pinServerProofBase64 = credential.pinServerProofBase64,
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
        } finally {
            credential.unwrapKey.fill(0)
        }
    }

    private fun validateHello(hello: HandshakeMessage.ControlHello): String? =
        when {
            hello.peerId.value.length !in 16..128 ||
                !HELLO_TOKEN_PATTERN.matches(hello.peerId.value) -> "Invalid peer identity"
            hello.appVersion.length !in 1..64 || hello.displayName.length > 160 ->
                "Invalid client metadata"
            hello.clientNonce.length !in 16..128 ||
                !HELLO_TOKEN_PATTERN.matches(hello.clientNonce) -> "Invalid connection request"
            hello.listeningPort !in 1..65535 -> "Invalid peer port"
            else -> null
        }

    private fun constantTimeStringEquals(expected: String, actual: String): Boolean {
        val expectedBytes = expected.encodeToByteArray()
        val actualBytes = actual.encodeToByteArray()
        return try {
            Crypto.constantTimeEquals(expectedBytes, actualBytes)
        } finally {
            expectedBytes.fill(0)
            actualBytes.fill(0)
        }
    }

    private fun rejected(code: HandshakeRejectionCode, reason: String) =
        PeerServer.ControlAdmission.Rejected(reason, code)

    private companion object {
        const val TAG = "ControlAdmission"
        const val IDENTITY_COLLISION_REASON = "Cannot join yourself"
        const val AUTHENTICATION_FAILURE_REASON = "Room authentication failed"
        const val MAX_ROOM_MEMBERS = 8
        const val MAX_CONCURRENT_AUTHENTICATIONS = 3
        const val AUTHENTICATION_TIMEOUT_MS = 8_000L
        val HELLO_TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]+$")
    }
}
