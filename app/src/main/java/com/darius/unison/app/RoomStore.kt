package com.darius.unison.app

import com.darius.unison.model.RoomUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RoomStore {
    private val _state = MutableStateFlow(RoomUiState())
    val state: StateFlow<RoomUiState> = _state.asStateFlow()

    fun set(state: RoomUiState) {
        _state.value = state
    }

    fun update(block: (RoomUiState) -> RoomUiState) = _state.update(block)

    /** Clears session state without hiding an explicitly created local-only hotspot. The hotspot
     * is an independent user action and remains active until the user stops it. */
    fun reset() {
        val current = _state.value
        _state.value = RoomUiState(
            localIdentity = current.localIdentity,
            roomAddress = current.roomAddress,
            roomPort = current.roomPort,
            hotspot = current.hotspot,
        )
    }
}
