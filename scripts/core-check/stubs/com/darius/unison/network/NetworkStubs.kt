package com.darius.unison.network

import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.HandshakeRejectionCode

class ControlConnection

class PeerServer {
    sealed interface ControlAdmission {
        data class Accepted(
            val response: HandshakeMessage.CoordinatorHello,
            val serverWriteKey: ByteArray,
            val serverReadKey: ByteArray,
            val endpoint: PeerEndpoint,
            val roomId: String,
            val onEnvelope: suspend (PeerId, Envelope) -> Unit,
            val onClosed: suspend (ControlConnection, Throwable?) -> Unit,
        ) : ControlAdmission

        data class PinChallenge(
            val response: HandshakeMessage.PinChallenge,
            val complete: suspend (HandshakeMessage.PinResponse) -> ControlAdmission,
        ) : ControlAdmission

        data class Rejected(val reason: String, val code: HandshakeRejectionCode) : ControlAdmission
    }
}
