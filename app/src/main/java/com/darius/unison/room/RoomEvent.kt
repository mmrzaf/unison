package com.darius.unison.room

import com.darius.unison.model.AppCommand
import com.darius.unison.model.HotspotInfo
import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferPriority
import com.darius.unison.model.TransportCommandPhase
import com.darius.unison.model.UserCommand
import com.darius.unison.network.ConnectedControl
import com.darius.unison.network.ControlConnection
import com.darius.unison.playback.PlayerState
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.transfer.TransferFailure
import kotlinx.coroutines.CompletableDeferred

/**
 * Inputs accepted by the single serialized room actor. Producers never mutate canonical room state.
 * [provenanceRequirement] is the exhaustive ingress inventory for authority that must survive until
 * the actor consumes asynchronous work.
 */
internal sealed interface RoomEvent {
    data class AppCommandReceived(
        val command: AppCommand,
        /** Issued at ordered command ingress so later destructive commands can invalidate it. */
        val queuePreparationTicket: QueuePreparationFence.Ticket? = null,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class LocalTransportSubmitted(
        val command: AppCommand.Transport,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class LocalTransportSuperseded(
        val command: AppCommand.Transport,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class NetworkEnvelopeReceived(
        val sourceConnection: ControlConnection,
        val envelope: Envelope,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class ControlConnected(
        val connection: ControlConnection,
        val admittedSession: RoomSessionProvenance,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class ControlClosed(
        val connection: ControlConnection,
        val cause: Throwable?,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class InitialJoinConnected(
        val generation: Long,
        val attempt: Int,
        val connected: ConnectedControl,
    ) : RoomEvent

    data class InitialJoinFailed(
        val generation: Long,
        val attempt: Int,
        val error: Throwable,
    ) : RoomEvent

    data class InitialJoinRetry(val generation: Long) : RoomEvent

    data class ReconnectSucceeded(
        val generation: Long,
        val expectedCoordinatorPeerId: PeerId,
        val connected: ConnectedControl,
        val lastSequence: Long,
        val cachedTrackIds: List<TrackId>,
    ) : RoomEvent

    data class ReconnectExhausted(
        val generation: Long,
        val lostCoordinatorPeerId: PeerId,
    ) : RoomEvent

    /** Coordinator-local network disappeared and did not return inside the bounded grace window. */
    data class LocalNetworkGraceExpired(val generation: Long) : RoomEvent

    /**
     * A disconnected participant did not reconnect before its canonical-membership grace expired.
     */
    data class PeerDisconnectGraceExpired(
        val generation: Long,
        val peerId: PeerId,
    ) : RoomEvent

    data class CoordinatorCommandReceived(
        val command: UserCommand,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class CoordinatorTransportSuperseded(
        val generation: Long,
        val command: UserCommand,
    ) : RoomEvent

    data class CanonicalMutationRequested(
        val body: ProtocolBody,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class TrackAvailabilityObserved(
        val peerId: PeerId,
        val trackId: TrackId,
        val available: Boolean,
        val priority: TransferPriority = TransferPriority.BACKGROUND,
        val neededByCoordinatorNs: Long? = null,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class TracksPrepared(
        val generation: Long,
        val fenceTicket: QueuePreparationFence.Ticket,
        val requestedCount: Int,
        val selectedCount: Int,
        val available: List<TrackDescriptor>,
        val insertAfterCurrent: Boolean,
        val error: Throwable?,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class RepositoryCommandCompleted(
        val generation: Long,
        val command: AppCommand,
        val error: Throwable?,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class LocalAddressChanged(val address: String?) : RoomEvent

    data class HotspotChanged(val value: HotspotInfo?, val address: String?) : RoomEvent

    data class PlayerTransitionObserved(val state: PlayerState) : RoomEvent

    data class TransportCommandPhaseObserved(
        val generation: Long,
        val commandId: String,
        val phase: TransportCommandPhase,
        val message: String?,
    ) : RoomEvent

    data class LocalTrackAvailabilityProbed(
        val generation: Long,
        val trackId: TrackId,
        val available: Boolean,
    ) : RoomEvent

    data class TransportWatchdogExpired(
        val generation: Long,
        val commandId: String,
        val ticket: TransportCommandTracker.Ticket,
        val reconciliationAttempted: Boolean,
    ) : RoomEvent

    data class TransportWatchdogAlignmentObserved(
        val generation: Long,
        val commandId: String,
        val ticket: TransportCommandTracker.Ticket,
        val graceAttempted: Boolean,
        val aligned: Boolean,
    ) : RoomEvent

    data class HeartbeatTick(val generation: Long) : RoomEvent

    /** Low-frequency room/network consequence of the independent local playback sync loop. */
    data class LocalPlaybackStatusDue(val generation: Long) : RoomEvent

    /** Coordinator reference broadcast requested by the independent local playback sync loop. */
    data class PlaybackReferenceBroadcastDue(val generation: Long) : RoomEvent

    data class TransferCompleted(
        val generation: Long,
        val descriptor: TrackDescriptor,
    ) : RoomEvent

    data class TransferAuthorizationTimedOut(
        val generation: Long,
        val authorizationToken: String,
    ) : RoomEvent

    data class TransferRetryDue(
        val generation: Long,
        val trackId: TrackId,
        val destinationPeerId: PeerId,
    ) : RoomEvent

    data class TransferFailed(
        val generation: Long,
        val failure: TransferFailure,
    ) : RoomEvent
}

/**
 * Provenance required at the authoritative mutation boundary for each actor ingress.
 *
 * This inventory is intentionally independent of the fields currently present on an event. In
 * particular, connection/session events identified here must not be treated as safe merely because
 * an earlier callback performed a validity check; the proof must survive until actor consumption.
 */
internal enum class RoomEventProvenanceRequirement {
    /** Ordered local work whose authority is established by actor ingress itself. */
    ACTOR_LOCAL,

    /**
     * Process/device state that intentionally spans room generations and is interpreted on consume.
     */
    DEVICE_GLOBAL,

    /** Async work produced for one room-session generation. */
    SESSION,

    /** Work whose authority is tied to one specific control-connection instance. */
    CONNECTION,

    /** Work that must prove both room/session ownership and current connection authority. */
    SESSION_AND_CONNECTION,
}

internal val RoomEvent.provenanceRequirement: RoomEventProvenanceRequirement
    get() =
        when (this) {
            is RoomEvent.AppCommandReceived,
            is RoomEvent.LocalTransportSubmitted,
            is RoomEvent.LocalTransportSuperseded,
            is RoomEvent.CoordinatorCommandReceived,
            is RoomEvent.CanonicalMutationRequested,
            is RoomEvent.TrackAvailabilityObserved -> RoomEventProvenanceRequirement.ACTOR_LOCAL

            is RoomEvent.LocalAddressChanged,
            is RoomEvent.HotspotChanged,
            is RoomEvent.PlayerTransitionObserved -> RoomEventProvenanceRequirement.DEVICE_GLOBAL

            is RoomEvent.ControlClosed -> RoomEventProvenanceRequirement.CONNECTION

            is RoomEvent.NetworkEnvelopeReceived,
            is RoomEvent.ControlConnected -> RoomEventProvenanceRequirement.SESSION_AND_CONNECTION

            is RoomEvent.InitialJoinConnected,
            is RoomEvent.InitialJoinFailed,
            is RoomEvent.InitialJoinRetry,
            is RoomEvent.ReconnectSucceeded,
            is RoomEvent.ReconnectExhausted,
            is RoomEvent.LocalNetworkGraceExpired,
            is RoomEvent.PeerDisconnectGraceExpired,
            is RoomEvent.CoordinatorTransportSuperseded,
            is RoomEvent.TracksPrepared,
            is RoomEvent.RepositoryCommandCompleted,
            is RoomEvent.TransportCommandPhaseObserved,
            is RoomEvent.LocalTrackAvailabilityProbed,
            is RoomEvent.TransportWatchdogExpired,
            is RoomEvent.TransportWatchdogAlignmentObserved,
            is RoomEvent.HeartbeatTick,
            is RoomEvent.LocalPlaybackStatusDue,
            is RoomEvent.PlaybackReferenceBroadcastDue,
            is RoomEvent.TransferCompleted,
            is RoomEvent.TransferAuthorizationTimedOut,
            is RoomEvent.TransferRetryDue,
            is RoomEvent.TransferFailed -> RoomEventProvenanceRequirement.SESSION
        }

internal fun RoomEvent.completionOrNull(): CompletableDeferred<Unit>? =
    when (this) {
        is RoomEvent.AppCommandReceived -> completion
        is RoomEvent.LocalTransportSubmitted -> completion
        is RoomEvent.LocalTransportSuperseded -> completion
        is RoomEvent.NetworkEnvelopeReceived -> completion
        is RoomEvent.ControlConnected -> completion
        is RoomEvent.ControlClosed -> completion
        is RoomEvent.CoordinatorCommandReceived -> completion
        is RoomEvent.CanonicalMutationRequested -> completion
        is RoomEvent.TrackAvailabilityObserved -> completion
        is RoomEvent.TracksPrepared -> completion
        is RoomEvent.RepositoryCommandCompleted -> completion
        else -> null
    }
