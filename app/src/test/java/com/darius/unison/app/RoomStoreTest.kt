package com.darius.unison.app

import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.HotspotInfo
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.RoomStructureState
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
        store.updateStructure {
            RoomStructureState(
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
        }

        store.reset()

        assertEquals(RoomLifecycleState.IDLE, store.structure.value.lifecycle)
        assertNull(store.structure.value.snapshot)
        assertNull(store.structure.value.errorMessage)
        assertEquals(identity, store.structure.value.localIdentity)
        assertEquals(hotspot, store.structure.value.hotspot)
        assertEquals("192.168.43.1", store.structure.value.roomAddress)
        assertEquals(41111, store.structure.value.roomPort)
    }

    @Test
    fun resetCanStopIndependentHotspotForEndRoom() {
        val peerId = PeerId("local-peer")
        val store = RoomStore()
        store.updateStructure {
            RoomStructureState(
                localIdentity = LocalIdentity(peerId, "Listener"),
                roomAddress = "192.168.43.1",
                roomPort = 41111,
                hotspot = HotspotInfo("Unison network", "secret", 2),
            )
        }

        store.reset(preserveHotspot = false)

        assertNull(store.structure.value.hotspot)
        assertNull(store.structure.value.roomAddress)
        assertEquals(41111, store.structure.value.roomPort)
    }
}
