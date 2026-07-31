package com.darius.unison.app

import com.darius.unison.model.RoomPlaybackTelemetry
import com.darius.unison.model.RoomStructureState
import com.darius.unison.model.RoomTransferTelemetry
import com.darius.unison.model.RoomUiState
import com.darius.unison.model.toPlaybackTelemetry
import com.darius.unison.model.toStructureState
import com.darius.unison.model.toTransferTelemetry
import com.darius.unison.model.toUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Publishes independent structural, playback, and transfer flows.
 *
 * Playback position changes several times per second, so this store deliberately does not maintain
 * a second aggregate [RoomUiState] flow. Rebuilding the full room snapshot on every position tick
 * caused avoidable allocations and lock contention. Call [currentState] only for rare atomic reads;
 * presentation code should combine the dedicated flows.
 */
class RoomStore {
    private val lock = Any()
    private val _structure = MutableStateFlow(RoomStructureState())
    private val _playback = MutableStateFlow(RoomPlaybackTelemetry())
    private val _transfers = MutableStateFlow(RoomTransferTelemetry())

    val structure: StateFlow<RoomStructureState> = _structure.asStateFlow()
    val playback: StateFlow<RoomPlaybackTelemetry> = _playback.asStateFlow()
    val transfers: StateFlow<RoomTransferTelemetry> = _transfers.asStateFlow()

    fun currentState(): RoomUiState = synchronized(lock) { currentStateLocked() }

    fun set(state: RoomUiState) =
        synchronized(lock) {
            publishComponents(
                state.toStructureState(),
                state.toPlaybackTelemetry(),
                state.toTransferTelemetry(),
            )
        }

    fun update(block: (RoomUiState) -> RoomUiState) =
        synchronized(lock) {
            val next = block(currentStateLocked())
            publishComponents(
                next.toStructureState(),
                next.toPlaybackTelemetry(),
                next.toTransferTelemetry(),
            )
        }

    fun updateStructure(block: (RoomStructureState) -> RoomStructureState) =
        synchronized(lock) {
            val next = block(_structure.value)
            if (_structure.value != next) _structure.value = next
        }

    fun updatePlayback(block: (RoomPlaybackTelemetry) -> RoomPlaybackTelemetry) =
        synchronized(lock) {
            val next = block(_playback.value)
            if (_playback.value != next) _playback.value = next
        }

    fun updateTransfers(block: (RoomTransferTelemetry) -> RoomTransferTelemetry) =
        synchronized(lock) {
            val next = block(_transfers.value)
            if (_transfers.value != next) _transfers.value = next
        }

    /**
     * Clears session state. Callers may preserve a separately-created hotspot while switching
     * rooms.
     */
    fun reset(preserveHotspot: Boolean = true) =
        synchronized(lock) {
            val current = _structure.value
            publishComponents(
                RoomStructureState(
                    localIdentity = current.localIdentity,
                    roomAddress = current.roomAddress.takeIf { preserveHotspot },
                    roomPort = current.roomPort,
                    hotspot = current.hotspot.takeIf { preserveHotspot },
                ),
                RoomPlaybackTelemetry(),
                RoomTransferTelemetry(),
            )
        }

    private fun currentStateLocked(): RoomUiState =
        _structure.value.toUiState(_playback.value, _transfers.value)

    private fun publishComponents(
        structure: RoomStructureState,
        playback: RoomPlaybackTelemetry,
        transfers: RoomTransferTelemetry,
    ) {
        if (_structure.value != structure) _structure.value = structure
        if (_playback.value != playback) _playback.value = playback
        if (_transfers.value != transfers) _transfers.value = transfers
    }
}
