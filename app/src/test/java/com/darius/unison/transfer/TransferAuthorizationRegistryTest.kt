package com.darius.unison.transfer

import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferAuthorizationRegistryTest {
    private val track = TrackId("a".repeat(64))
    private val peer = PeerId("peer-a")

    @Test
    fun matchingAuthorizationCanBeConsumedOnlyOnce() {
        var now = 100L
        val registry = TransferAuthorizationRegistry(nowElapsedMs = { now })
        val id = registry.authorize("room", track, peer, "secret", 1_000L)
        val authorization = registry.findMatching(id, "room", track, peer)

        assertNotNull(authorization)
        assertTrue(registry.consume(id, authorization!!))
        assertFalse(registry.consume(id, authorization))
        assertNull(registry.findMatching(id, "room", track, peer))
    }

    @Test
    fun expiredOrMismatchedAuthorizationIsRejected() {
        var now = 100L
        val registry = TransferAuthorizationRegistry(nowElapsedMs = { now })
        val id = registry.authorize("room", track, peer, "secret", 200L)

        assertNull(registry.findMatching(id, "other-room", track, peer))
        assertNull(registry.findMatching(id, "room", track, PeerId("peer-b")))
        now = 200L
        assertNull(registry.findMatching(id, "room", track, peer))
        assertEquals(0, registry.size)
    }

    @Test
    fun capacityEvictsTheEarliestExpiry() {
        var evictions = 0
        val registry =
            TransferAuthorizationRegistry(
                maxEntries = 2,
                nowElapsedMs = { 0L },
                onCapacityEviction = { evictions++ },
            )
        val first = registry.authorize("room", track, peer, "first", 100L)
        val second = registry.authorize("room", track, peer, "second", 200L)
        val third = registry.authorize("room", track, peer, "third", 300L)

        assertNull(registry.findMatching(first, "room", track, peer))
        assertNotNull(registry.findMatching(second, "room", track, peer))
        assertNotNull(registry.findMatching(third, "room", track, peer))
        assertEquals(1, evictions)
    }

    @Test
    fun replacingTheSameTokenDoesNotEvictAnotherEntry() {
        var evictions = 0
        val registry =
            TransferAuthorizationRegistry(
                maxEntries = 1,
                nowElapsedMs = { 0L },
                onCapacityEviction = { evictions++ },
            )
        val first = registry.authorize("room", track, peer, "same", 100L)
        val replacement = registry.authorize("room", track, peer, "same", 500L)

        assertEquals(first, replacement)
        assertEquals(0, evictions)
        assertEquals(500L, registry.findMatching(first, "room", track, peer)?.expiresAtElapsedMs)
    }
}
