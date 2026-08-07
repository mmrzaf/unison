package com.darius.unison.room

import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerRegistryTest {
    private val peer = PeerId("peer-123456789012")
    private val track = TrackId("a".repeat(64))

    @Test
    fun availabilityAndWaitingIndexesStayExclusive() {
        val registry = PeerRegistry<String>()
        registry.markNeeded(peer, track)
        assertTrue(peer in registry.waitingForSource[track].orEmpty())
        registry.markAvailable(peer, track)
        assertTrue(peer in registry.availability[track].orEmpty())
        assertFalse(peer in registry.waitingForSource[track].orEmpty())
    }

    @Test
    fun removingPeerCleansAllPeerIndexes() {
        val registry = PeerRegistry<String>()
        registry.connections[peer] = "connection"
        registry.endpoints[peer] = PeerEndpoint(peer, "Phone", "192.168.1.2", 1234, "1")
        registry.lastSeenElapsedMs[peer] = 10
        registry.markAvailable(peer, track)
        registry.removePeer(peer)
        assertFalse(registry.endpoints.containsKey(peer))
        assertFalse(registry.lastSeenElapsedMs.containsKey(peer))
        assertFalse(peer in registry.availability[track].orEmpty())
    }

    @Test
    fun clearSessionClosesAndClearsEverything() {
        val registry = PeerRegistry<String>()
        registry.connections[peer] = "connection"
        registry.markNeeded(peer, track)
        val closed = mutableListOf<String>()
        registry.clearSession(closed::add)
        assertEquals(listOf("connection"), closed)
        assertTrue(registry.connections.isEmpty())
        assertTrue(registry.waitingForSource.isEmpty())
    }
}
