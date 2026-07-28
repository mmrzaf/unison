package com.darius.unison.room

import com.darius.unison.model.AppCommand
import com.darius.unison.model.HotspotInfo
import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.UserCommand
import com.darius.unison.network.ControlConnection
import com.darius.unison.playback.PlayerState
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.ProtocolBody
import kotlinx.coroutines.CompletableDeferred

/** Inputs accepted by the single serialized room actor. Producers never mutate room state. */
internal sealed interface RoomEvent {
    data class AppCommandReceived(
        val command: AppCommand,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class NetworkEnvelopeReceived(
        val peerId: PeerId,
        val envelope: Envelope,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class ControlConnected(
        val connection: ControlConnection,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class ControlClosed(
        val connection: ControlConnection,
        val cause: Throwable?,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class CoordinatorCommandReceived(
        val command: UserCommand,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class CanonicalMutationRequested(
        val body: ProtocolBody,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class TrackAvailabilityObserved(
        val peerId: PeerId,
        val trackId: TrackId,
        val available: Boolean,
        val completion: CompletableDeferred<Unit>,
    ) : RoomEvent

    data class LocalAddressChanged(val address: String?) : RoomEvent
    data class HotspotChanged(val value: HotspotInfo?, val address: String?) : RoomEvent
    data class PlayerStateChanged(val state: PlayerState) : RoomEvent
    data object HeartbeatTick : RoomEvent
    data object ClockSyncTick : RoomEvent
    data object PlaybackSyncTick : RoomEvent
    data class TransferCompleted(val descriptor: TrackDescriptor) : RoomEvent
    data class TransferFailed(
        val trackId: TrackId,
        val sourcePeerId: PeerId?,
        val reason: String,
    ) : RoomEvent
}

internal fun RoomEvent.completionOrNull(): CompletableDeferred<Unit>? = when (this) {
    is RoomEvent.AppCommandReceived -> completion
    is RoomEvent.NetworkEnvelopeReceived -> completion
    is RoomEvent.ControlConnected -> completion
    is RoomEvent.ControlClosed -> completion
    is RoomEvent.CoordinatorCommandReceived -> completion
    is RoomEvent.CanonicalMutationRequested -> completion
    is RoomEvent.TrackAvailabilityObserved -> completion
    else -> null
}
