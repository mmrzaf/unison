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
                snapshot = RoomSnapshot(
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

        assertEquals(RoomLifecycleState.IDLE, store.state.value.lifecycle)
        assertNull(store.state.value.snapshot)
        assertNull(store.state.value.errorMessage)
        assertEquals(identity, store.state.value.localIdentity)
        assertEquals(hotspot, store.state.value.hotspot)
        assertEquals("192.168.43.1", store.state.value.roomAddress)
        assertEquals(41111, store.state.value.roomPort)
    }
}
