package com.darius.unison.network

import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.protocol.ControlCredentialMode
import com.darius.unison.protocol.Crypto
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.protocol.PinPake
import com.darius.unison.protocol.ProtocolException
import com.darius.unison.util.DiagnosticLog
import com.darius.unison.util.MonotonicClock
import java.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Protocol 1 control handshake over [HandshakeChannel].
 *
 * The transport is not part of the protocol: a socket and an in-memory test link execute the same
 * authentication transcript, reject the same transcripts, and produce the same connection.
 */
class ControlHandshake(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val clock: MonotonicClock,
    private val log: DiagnosticLog,
) {
    suspend fun connect(
        channel: HandshakeChannel,
        identity: LocalIdentity,
        roomId: String,
        host: String,
        port: Int,
        listeningPort: Int,
        credential: Credential,
        appVersion: String,
        onEnvelope: suspend (ControlConnection, Envelope) -> Unit,
        onClosed: suspend (ControlConnection, Throwable?) -> Unit,
    ): ConnectedControl =
        try {
            val nonce = Crypto.randomBase64(18)
            val pinSession =
                (credential as? Credential.Pin)?.let {
                    PinPake.ClientSession.start(roomId, identity.peerId.value, nonce, it.value)
                }
            val hello: HandshakeMessage.ControlHello =
                when (credential) {
                    is Credential.Pin ->
                        HandshakeMessage.PinClientHello(
                            peerId = identity.peerId,
                            displayName = identity.displayName,
                            appVersion = appVersion,
                            protocolVersion = PROTOCOL_VERSION,
                            listeningPort = listeningPort,
                            roomId = roomId,
                            clientNonce = nonce,
                            pinPublicValueBase64 = checkNotNull(pinSession).publicValueBase64,
                        )

                    is Credential.RoomSecret ->
                        HandshakeMessage.ReconnectClientHello(
                            peerId = identity.peerId,
                            displayName = identity.displayName,
                            appVersion = appVersion,
                            protocolVersion = PROTOCOL_VERSION,
                            listeningPort = listeningPort,
                            roomId = roomId,
                            clientNonce = nonce,
                        )
                }
            channel.write(hello)

            var pinAnswer: PinPake.ClientProof? = null
            var reconnectChallengeNonce: String? = null
            val firstResponse = channel.read()
            val finalResponse =
                when (firstResponse) {
                    is HandshakeMessage.PinChallenge -> {
                        val session =
                            pinSession ?: throw ProtocolException("Unexpected PIN challenge")
                        val answer =
                            session.answer(
                                PinPake.Challenge(
                                    saltBase64 = firstResponse.saltBase64,
                                    serverPublicValueBase64 = firstResponse.serverPublicValueBase64,
                                    serverNonce = firstResponse.serverNonce,
                                )
                            )
                        pinAnswer = answer
                        channel.write(HandshakeMessage.PinResponse(answer.proofBase64))
                        channel.read()
                    }

                    is HandshakeMessage.ReconnectChallenge -> {
                        val secret =
                            (credential as? Credential.RoomSecret)?.value
                                ?: throw ProtocolException("Unexpected reconnect challenge")
                        reconnectChallengeNonce = firstResponse.serverNonce
                        val proof =
                            Crypto.reconnectProof(
                                secret,
                                roomId,
                                identity.peerId.value,
                                nonce,
                                firstResponse.serverNonce,
                            )
                        channel.write(HandshakeMessage.ReconnectResponse(proof))
                        channel.read()
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
                        if (finalResponse.protocolVersion != PROTOCOL_VERSION) {
                            throw ProtocolException("Protocol mismatch")
                        }
                        val unwrapKey =
                            when (credential) {
                                is Credential.Pin -> {
                                    if (finalResponse.credentialMode != ControlCredentialMode.PIN) {
                                        throw ProtocolException("Unexpected credential mode")
                                    }
                                    val answer =
                                        pinAnswer
                                            ?: throw ProtocolException("Missing PIN challenge")
                                    val serverProof =
                                        finalResponse.pinServerProofBase64
                                            ?: throw ProtocolException("Missing PIN server proof")
                                    if (
                                        !PinPake.verifyServerProof(
                                            answer.expectedServerProofBase64,
                                            serverProof,
                                        )
                                    ) {
                                        throw ProtocolException("Room authentication failed")
                                    }
                                    answer.sessionKey
                                }

                                is Credential.RoomSecret -> {
                                    val challengeNonce =
                                        reconnectChallengeNonce
                                            ?: throw ProtocolException(
                                                "Missing reconnect challenge"
                                            )
                                    if (
                                        firstResponse !is HandshakeMessage.ReconnectChallenge ||
                                            pinAnswer != null ||
                                            finalResponse.serverNonce != challengeNonce
                                    ) {
                                        throw ProtocolException("Invalid reconnect transcript")
                                    }
                                    if (
                                        finalResponse.credentialMode !=
                                            ControlCredentialMode.RECONNECT
                                    ) {
                                        throw ProtocolException("Unexpected credential mode")
                                    }
                                    Crypto.deriveReconnectKey(
                                        credential.value,
                                        roomId,
                                        identity.peerId.value,
                                        nonce,
                                        challengeNonce,
                                    )
                                }
                            }
                        val roomSecret =
                            try {
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
                                try {
                                    Crypto.decryptAesGcm(
                                        key = unwrapKey,
                                        ciphertext = ciphertext,
                                        iv = iv,
                                        associatedData = associatedData,
                                    )
                                } finally {
                                    ciphertext.fill(0)
                                    iv.fill(0)
                                    associatedData.fill(0)
                                }
                            } finally {
                                unwrapKey.fill(0)
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
                        val envelopeChannel =
                            try {
                                channel.upgrade(
                                    writeKey = keys.clientToCoordinator,
                                    readKey = keys.coordinatorToClient,
                                    expectedRoomId = roomId,
                                )
                            } finally {
                                keys.clientToCoordinator.fill(0)
                                keys.coordinatorToClient.fill(0)
                            }
                        val endpoint =
                            PeerEndpoint(
                                peerId = finalResponse.coordinatorPeerId,
                                displayName = "Coordinator",
                                hostAddress = host,
                                port = port,
                                appVersion = appVersion,
                            )
                        val connection =
                            try {
                                ControlConnection(
                                    peerId = finalResponse.coordinatorPeerId,
                                    endpoint = endpoint,
                                    channel = envelopeChannel,
                                    parentScope = scope,
                                    dispatcher = dispatcher,
                                    clock = clock,
                                    log = log,
                                    onEnvelope = onEnvelope,
                                    onClosed = onClosed,
                                )
                            } catch (error: Exception) {
                                envelopeChannel.close(error)
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
            channel.close()
            throw error
        } finally {
            (credential as? Credential.RoomSecret)?.value?.fill(0)
        }

    /** Completes a server-side admission that already received [hello]. */
    suspend fun accept(
        handler: PeerNetwork.Handler,
        hello: HandshakeMessage.ControlHello,
        channel: HandshakeChannel,
    ) {
        val initial = handler.admitControl(hello, channel.remoteHostAddress)
        val admission =
            when (initial) {
                is PeerNetwork.ControlAdmission.PinChallenge -> {
                    channel.write(initial.response)
                    val response =
                        channel.read() as? HandshakeMessage.PinResponse
                            ?: throw ProtocolException("Expected PIN response")
                    initial.complete(response)
                }

                is PeerNetwork.ControlAdmission.ReconnectChallenge -> {
                    channel.write(initial.response)
                    val response =
                        channel.read() as? HandshakeMessage.ReconnectResponse
                            ?: throw ProtocolException("Expected reconnect response")
                    initial.complete(response)
                }

                else -> initial
            }
        when (admission) {
            is PeerNetwork.ControlAdmission.Rejected -> {
                channel.write(HandshakeMessage.Rejected(admission.reason, admission.code))
                channel.close()
            }

            is PeerNetwork.ControlAdmission.PinChallenge ->
                throw ProtocolException("Nested PIN challenge")
            is PeerNetwork.ControlAdmission.ReconnectChallenge ->
                throw ProtocolException("Nested reconnect challenge")

            is PeerNetwork.ControlAdmission.Accepted ->
                try {
                    channel.write(admission.response)
                    val envelopeChannel =
                        try {
                            channel.upgrade(
                                writeKey = admission.serverWriteKey,
                                readKey = admission.serverReadKey,
                                expectedRoomId = admission.roomId,
                            )
                        } finally {
                            admission.serverWriteKey.fill(0)
                            admission.serverReadKey.fill(0)
                        }
                    val connection =
                        try {
                            ControlConnection(
                                peerId = hello.peerId,
                                endpoint = admission.endpoint,
                                channel = envelopeChannel,
                                parentScope = scope,
                                dispatcher = dispatcher,
                                clock = clock,
                                log = log,
                                onEnvelope = admission.onEnvelope,
                                onClosed = admission.onClosed,
                            )
                        } catch (error: Exception) {
                            envelopeChannel.close(error)
                            throw error
                        }
                    try {
                        handler.onControlConnected(
                            connection = connection,
                            admittedRoomId = admission.roomId,
                            admittedSessionGeneration = admission.sessionGeneration,
                        )
                        connection.start()
                    } catch (error: Exception) {
                        connection.closeSilently()
                        throw error
                    }
                } catch (error: Exception) {
                    channel.close()
                    throw error
                }
        }
    }
}
