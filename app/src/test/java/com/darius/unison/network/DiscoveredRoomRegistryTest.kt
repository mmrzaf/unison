package com.darius.unison.network

import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.protocol.PROTOCOL_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveredRoomRegistryTest {
    private fun room(
        service: String = "Unison-a",
        id: String = "aaaaaaaa",
        name: String = "Room",
    ) =
        DiscoveredRoom(
            serviceName = service,
            roomId = id,
            roomName = name,
            hostAddress = "192.168.1.2",
            port = 4102,
            protocolVersion = PROTOCOL_VERSION,
            term = 1,
        )

    @Test
    fun identicalRediscoveryDoesNotChangeVisibleList() {
        val registry = DiscoveredRoomRegistry()

        assertTrue(registry.found(room()))
        assertFalse(registry.found(room()))
        assertEquals(1, registry.rooms().size)
    }

    @Test
    fun updatedServiceDetailsReplacePreviousEntry() {
        val registry = DiscoveredRoomRegistry()
        registry.found(room())

        assertTrue(registry.found(room().copy(port = 4200)))
        assertEquals(4200, registry.rooms().single().port)
    }

    @Test
    fun sameRoomReRegistrationReplacesOldService() {
        val registry = DiscoveredRoomRegistry()
        registry.found(room(service = "Unison-a"))
        registry.found(room(service = "Unison-a-2"))

        assertEquals(listOf("Unison-a-2"), registry.rooms().map { it.serviceName })
    }

    @Test
    fun roomsAreSortedByNameThenService() {
        val registry = DiscoveredRoomRegistry()
        registry.found(room(service = "Unison-z", id = "z", name = "Beta"))
        registry.found(room(service = "Unison-b", id = "b", name = "alpha"))
        registry.found(room(service = "Unison-a", id = "a", name = "Alpha"))

        assertEquals(
            listOf("Unison-a", "Unison-b", "Unison-z"),
            registry.rooms().map { it.serviceName },
        )
    }

    @Test
    fun clearRemovesPreviousScanResults() {
        val registry = DiscoveredRoomRegistry()
        registry.found(room())

        registry.clear()

        assertEquals(emptyList<DiscoveredRoom>(), registry.rooms())
    }
}
