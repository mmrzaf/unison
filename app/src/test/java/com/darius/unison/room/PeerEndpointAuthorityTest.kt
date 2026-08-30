package com.darius.unison.room

import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerEndpointAuthorityTest {
    private val peerId = PeerId("peer-guest-123456")

    private fun endpoint(host: String, port: Int = 4321) =
        PeerEndpoint(
            peerId = peerId,
            displayName = "  Guest  ",
            hostAddress = host,
            port = port,
            appVersion = "1.2.0",
        )

    @Test
    fun unrelatedPrivateHostCannotReplaceAuthenticatedHost() {
        val update =
            requireNotNull(
                PeerEndpointAuthority.normalizeAnnouncement(
                    peerId = peerId,
                    authenticatedHostAddress = "192.168.1.20",
                    announced = endpoint("192.168.1.99", port = 5000),
                    lastSeenElapsedMs = 123L,
                )
            )

        assertEquals("192.168.1.20", update.endpoint.hostAddress)
        assertEquals(5000, update.endpoint.port)
        assertEquals("Guest", update.endpoint.displayName)
        assertEquals(123L, update.endpoint.lastSeenElapsedMs)
        assertFalse(update.announcedHostMatchesAuthenticatedHost)
    }

    @Test
    fun equivalentIpv6TextStillMatchesAuthenticatedHost() {
        val update =
            requireNotNull(
                PeerEndpointAuthority.normalizeAnnouncement(
                    peerId = peerId,
                    authenticatedHostAddress = "fd00::1",
                    announced = endpoint("fd00:0:0:0:0:0:0:1"),
                    lastSeenElapsedMs = 1L,
                )
            )

        assertTrue(update.announcedHostMatchesAuthenticatedHost)
        assertEquals("fd00:0:0:0:0:0:0:1", update.endpoint.hostAddress)
    }

    @Test
    fun invalidOrPublicAnnouncementIsRejected() {
        assertNull(
            PeerEndpointAuthority.normalizeAnnouncement(
                peerId = peerId,
                authenticatedHostAddress = "192.168.1.20",
                announced = endpoint("8.8.8.8"),
                lastSeenElapsedMs = 1L,
            )
        )
    }

    @Test
    fun wrongPeerIdentityIsRejected() {
        val announced = endpoint("192.168.1.20").copy(peerId = PeerId("peer-other-123456"))

        assertNull(
            PeerEndpointAuthority.normalizeAnnouncement(
                peerId = peerId,
                authenticatedHostAddress = "192.168.1.20",
                announced = announced,
                lastSeenElapsedMs = 1L,
            )
        )
    }
}
