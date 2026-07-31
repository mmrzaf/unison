package com.darius.unison.app

import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.HotspotInfo
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.RoomUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomStoreTest {
    @Test
    fun resetClearsSessionButPreservesIndependentHotspot() {
        val peerId = PeerId("local-peer")
        val identity = LocalIdentity(peerId, "Listener")
        val hotspot = HotspotInfo("Unison network", "secret", 2)
        val store = RoomStore()
        store.set(
            RoomUiState(
                lifecycle = RoomLifecycleState.CONNECTED,
                localIdentity = identity,
                snapshot =
                    RoomSnapshot(
                        roomId = "room-id",
                        roomName = "Room",
                        term = CoordinatorTerm(1, peerId),
                        sequence = 0,
                    ),
                roomAddress = "192.168.43.1",
                roomPort = 41111,
                hotspot = hotspot,
                errorMessage = "old error",
            )
        )

        store.reset()

        assertEquals(RoomLifecycleState.IDLE, store.currentState().lifecycle)
        assertNull(store.currentState().snapshot)
        assertNull(store.currentState().errorMessage)
        assertEquals(identity, store.currentState().localIdentity)
        assertEquals(hotspot, store.currentState().hotspot)
        assertEquals("192.168.43.1", store.currentState().roomAddress)
        assertEquals(41111, store.currentState().roomPort)
    }

    @Test
    fun resetCanStopIndependentHotspotForEndRoom() {
        val peerId = PeerId("local-peer")
        val store = RoomStore()
        store.set(
            RoomUiState(
                localIdentity = LocalIdentity(peerId, "Listener"),
                roomAddress = "192.168.43.1",
                roomPort = 41111,
                hotspot = HotspotInfo("Unison network", "secret", 2),
            )
        )

        store.reset(preserveHotspot = false)

        assertNull(store.currentState().hotspot)
        assertNull(store.currentState().roomAddress)
        assertEquals(41111, store.currentState().roomPort)
    }
}
