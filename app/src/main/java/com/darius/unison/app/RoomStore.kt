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
    fun reset() {
        _state.value = RoomUiState(localIdentity = _state.value.localIdentity)
    }
}
