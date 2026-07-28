package com.darius.unison.protocol

import com.darius.unison.model.PeerId
import java.util.UUID

/**
 * Per-peer replay, term, sequence, and scheduled-command acceptance state.
 * RoomRuntime calls this from its serialized event loop, but methods remain synchronized so tests
 * and future callers cannot accidentally race the bounded windows.
 */
class EnvelopeReplayProtector(
    private val maxRecentMessageIdsPerPeer: Int = 512,
    private val scheduledCommandExpiryNs: Long = 30_000_000_000L,
) {
    private data class PeerAcceptanceState(
        var highestObservedTerm: Long = 0,
        var highestOrderedSequence: Long = -1,
        val recentMessageIds: LinkedHashSet<String> = LinkedHashSet(),
    )

    private val peers = mutableMapOf<PeerId, PeerAcceptanceState>()

    @Synchronized
    fun evaluate(
        socketPeerId: PeerId,
        envelope: Envelope,
        acceptedTerm: Long?,
        lastAppliedSequence: Long?,
        coordinatorNowNs: Long? = null,
    ): EnvelopeAcceptance {
        if (envelope.senderPeerId != socketPeerId) {
            return EnvelopeAcceptance.Rejected("Authenticated socket identity does not match envelope sender")
        }
        if (runCatching { UUID.fromString(envelope.messageId) }.isFailure) {
            return EnvelopeAcceptance.Rejected("Invalid message identifier")
        }
        if (envelope.sentAtElapsedNs <= 0) return EnvelopeAcceptance.Rejected("Invalid monotonic timestamp")

        val state = peers.getOrPut(socketPeerId) { PeerAcceptanceState() }
        if (envelope.messageId in state.recentMessageIds) return EnvelopeAcceptance.Duplicate

        if (acceptedTerm != null) {
            if (envelope.term < acceptedTerm) return EnvelopeAcceptance.Rejected("Envelope belongs to an older term")
            if (envelope.term > acceptedTerm && envelope.body !is ProtocolBody.JoinAccepted && envelope.body !is ProtocolBody.Snapshot) {
                return EnvelopeAcceptance.Rejected("Higher-term state must arrive as an authenticated snapshot")
            }
        }
        if (envelope.term < state.highestObservedTerm) {
            return EnvelopeAcceptance.Rejected("Envelope term regressed for this peer")
        }

        val sequence = envelope.sequence
        if (sequence != null) {
            if (sequence < 0) return EnvelopeAcceptance.Rejected("Invalid ordered sequence")
            val applied = lastAppliedSequence ?: state.highestOrderedSequence
            if (sequence <= applied || sequence <= state.highestOrderedSequence) {
                return EnvelopeAcceptance.Duplicate
            }
            val expected = applied + 1
            if (applied >= 0 && sequence != expected) {
                return EnvelopeAcceptance.SequenceGap(expected, sequence)
            }
        }

        coordinatorNowNs?.let { now ->
            val executeAt = envelope.body.scheduledExecutionNs()
            if (executeAt != null && executeAt + scheduledCommandExpiryNs < now) {
                return EnvelopeAcceptance.Rejected("Scheduled command has expired")
            }
        }

        state.highestObservedTerm = maxOf(state.highestObservedTerm, envelope.term)
        if (sequence != null) state.highestOrderedSequence = maxOf(state.highestOrderedSequence, sequence)
        state.recentMessageIds += envelope.messageId
        while (state.recentMessageIds.size > maxRecentMessageIdsPerPeer) {
            state.recentMessageIds.remove(state.recentMessageIds.first())
        }
        return EnvelopeAcceptance.Accepted
    }

    @Synchronized
    fun resetPeer(peerId: PeerId) {
        peers.remove(peerId)
    }

    @Synchronized
    fun reset() {
        peers.clear()
    }

    private fun ProtocolBody.scheduledExecutionNs(): Long? = when (this) {
        is ProtocolBody.PlayScheduled -> executeAtCoordinatorNs
        is ProtocolBody.PauseScheduled -> executeAtCoordinatorNs
        is ProtocolBody.SeekScheduled -> executeAtCoordinatorNs
        is ProtocolBody.CurrentItemChanged -> executeAtCoordinatorNs
        else -> null
    }
}

sealed interface EnvelopeAcceptance {
    data object Accepted : EnvelopeAcceptance
    data object Duplicate : EnvelopeAcceptance
    data class SequenceGap(val expected: Long, val actual: Long) : EnvelopeAcceptance
    data class Rejected(val reason: String) : EnvelopeAcceptance
}
