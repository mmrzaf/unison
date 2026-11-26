package com.darius.unison.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomStructureStateTest {
    @Test
    fun idleStateRequiresNoServiceWork() {
        val state = RoomStructureState()
        assertFalse(state.operationActive)
        assertFalse(state.sessionActive)
    }

    @Test
    fun manualDiscoveryIsAnOperationButNotARoomSession() {
        val state = RoomStructureState(lifecycle = RoomLifecycleState.DISCOVERING)
        assertTrue(state.operationActive)
        assertFalse(state.sessionActive)
    }

    @Test
    fun joiningRequiresForegroundRoomSession() {
        val state = RoomStructureState(lifecycle = RoomLifecycleState.JOINING)
        assertTrue(state.operationActive)
        assertTrue(state.sessionActive)
    }

    @Test
    fun failedStateDoesNotKeepServiceAliveFromAStaleSnapshot() {
        val snapshot =
            RoomSnapshot(
                roomId = "room-1234",
                roomName = "Room",
                term = CoordinatorTerm(1, PeerId("peer-123456789012")),
                sequence = 1,
            )
        val state = RoomStructureState(lifecycle = RoomLifecycleState.FAILED, snapshot = snapshot)
        assertFalse(state.operationActive)
        assertFalse(state.sessionActive)
    }
}
