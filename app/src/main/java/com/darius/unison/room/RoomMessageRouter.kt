package com.darius.unison.room

import com.darius.unison.model.PeerId
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.ProtocolBody

internal data class RoomSendTarget(
    val send: (Envelope) -> Boolean,
    val close: (Throwable) -> Unit,
)

/** Routes control traffic without owning canonical state or connection lifecycle. */
internal class RoomMessageRouter(
    private val localPeerId: () -> PeerId,
    private val isCoordinator: () -> Boolean,
    private val coordinatorTarget: () -> RoomSendTarget?,
    private val peerTargets: () -> Map<PeerId, RoomSendTarget>,
    private val createEnvelope: suspend (ProtocolBody, Long?) -> Envelope,
    private val handleCoordinatorLocal: suspend (ProtocolBody) -> Unit,
    private val handleLocalEnvelope: suspend (Envelope) -> Unit,
    private val onCoordinatorUnavailable: (String) -> Unit,
) {
    suspend fun sendToCoordinator(body: ProtocolBody) {
        if (isCoordinator()) {
            handleCoordinatorLocal(body)
            return
        }
        val target = coordinatorTarget()
        if (target == null) {
            onCoordinatorUnavailable("Room connection is unavailable")
            return
        }
        sendGuaranteed(target, createEnvelope(body, null), "Guaranteed control queue is full")
    }

    suspend fun send(peerId: PeerId, body: ProtocolBody) {
        val envelope = createEnvelope(body, null)
        if (peerId == localPeerId()) {
            handleLocalEnvelope(envelope)
            return
        }
        peerTargets()[peerId]?.let { target ->
            sendGuaranteed(target, envelope, "Control queue is full")
        }
    }

    suspend fun broadcast(body: ProtocolBody, except: PeerId? = null) {
        broadcastEnvelope(createEnvelope(body, null), except, "Control queue is full")
    }

    suspend fun broadcastCanonical(sequence: Long, body: ProtocolBody, except: PeerId? = null) {
        broadcastEnvelope(
            createEnvelope(body, sequence),
            except,
            "Canonical control queue is full",
        )
    }

    private fun broadcastEnvelope(envelope: Envelope, except: PeerId?, failure: String) {
        peerTargets().forEach { (peerId, target) ->
            if (peerId != except) sendGuaranteed(target, envelope, failure)
        }
    }

    private fun sendGuaranteed(target: RoomSendTarget, envelope: Envelope, failure: String) {
        if (!target.send(envelope)) target.close(IllegalStateException(failure))
    }
}
