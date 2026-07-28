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
 * Publishes structural state, playback telemetry, and transfer telemetry independently. The
 * compatibility [state] flow remains available while callers migrate, but high-frequency UI
 * collectors should use the dedicated flows.
 */
class RoomStore {
    private val lock = Any()
    private val _state = MutableStateFlow(RoomUiState())
    private val _structure = MutableStateFlow(RoomStructureState())
    private val _playback = MutableStateFlow(RoomPlaybackTelemetry())
    private val _transfers = MutableStateFlow(RoomTransferTelemetry())

    val state: StateFlow<RoomUiState> = _state.asStateFlow()
    val structure: StateFlow<RoomStructureState> = _structure.asStateFlow()
    val playback: StateFlow<RoomPlaybackTelemetry> = _playback.asStateFlow()
    val transfers: StateFlow<RoomTransferTelemetry> = _transfers.asStateFlow()

    fun set(state: RoomUiState) = synchronized(lock) { publish(state) }

    fun update(block: (RoomUiState) -> RoomUiState) = synchronized(lock) {
        publish(block(_state.value))
    }

    fun updateStructure(block: (RoomStructureState) -> RoomStructureState) = synchronized(lock) {
        publishComponents(block(_structure.value), _playback.value, _transfers.value)
    }

    fun updatePlayback(block: (RoomPlaybackTelemetry) -> RoomPlaybackTelemetry) = synchronized(lock) {
        publishComponents(_structure.value, block(_playback.value), _transfers.value)
    }

    fun updateTransfers(block: (RoomTransferTelemetry) -> RoomTransferTelemetry) = synchronized(lock) {
        publishComponents(_structure.value, _playback.value, block(_transfers.value))
    }

    /** Clears session state without hiding an explicitly created local-only hotspot. */
    fun reset() = synchronized(lock) {
        val current = _structure.value
        publishComponents(
            RoomStructureState(
                localIdentity = current.localIdentity,
                roomAddress = current.roomAddress,
                roomPort = current.roomPort,
                hotspot = current.hotspot,
            ),
            RoomPlaybackTelemetry(),
            RoomTransferTelemetry(),
        )
    }

    private fun publish(state: RoomUiState) {
        publishComponents(
            state.toStructureState(),
            state.toPlaybackTelemetry(),
            state.toTransferTelemetry(),
        )
    }

    private fun publishComponents(
        structure: RoomStructureState,
        playback: RoomPlaybackTelemetry,
        transfers: RoomTransferTelemetry,
    ) {
        if (_structure.value != structure) _structure.value = structure
        if (_playback.value != playback) _playback.value = playback
        if (_transfers.value != transfers) _transfers.value = transfers
        val state = structure.toUiState(playback, transfers)
        if (_state.value != state) _state.value = state
    }
}
