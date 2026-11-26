package com.darius.unison.app

import com.darius.unison.model.RoomPlaybackTelemetry
import com.darius.unison.model.RoomStructureState
import com.darius.unison.model.RoomTransferTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Publishes independent structural, playback, and transfer flows.
 *
 * Playback position changes several times per second, so the three concerns never share one
 * aggregate: a position tick must not invalidate every room-screen collector. Each flow is updated
 * by compare-and-set, so writers on different threads never need a lock.
 */
class RoomStore {
    private val _structure = MutableStateFlow(RoomStructureState())
    private val _playback = MutableStateFlow(RoomPlaybackTelemetry())
    private val _transfers = MutableStateFlow(RoomTransferTelemetry())

    val structure: StateFlow<RoomStructureState> = _structure.asStateFlow()
    val playback: StateFlow<RoomPlaybackTelemetry> = _playback.asStateFlow()
    val transfers: StateFlow<RoomTransferTelemetry> = _transfers.asStateFlow()

    fun updateStructure(block: (RoomStructureState) -> RoomStructureState) =
        _structure.update(block)

    fun updatePlayback(block: (RoomPlaybackTelemetry) -> RoomPlaybackTelemetry) =
        _playback.update(block)

    fun updateTransfers(block: (RoomTransferTelemetry) -> RoomTransferTelemetry) =
        _transfers.update(block)

    /**
     * Clears session state. Callers may preserve a separately-created hotspot while switching
     * rooms.
     */
    fun reset(preserveHotspot: Boolean = true) {
        _structure.update { current ->
            RoomStructureState(
                localIdentity = current.localIdentity,
                roomAddress = current.roomAddress.takeIf { preserveHotspot },
                roomPort = current.roomPort,
                hotspot = current.hotspot.takeIf { preserveHotspot },
            )
        }
        _playback.value = RoomPlaybackTelemetry()
        _transfers.value = RoomTransferTelemetry()
    }
}
