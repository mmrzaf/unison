package com.darius.unison.network

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAddressPolicyTest {
    @Test
    fun acceptsPrivateLinkLocalAndUniqueLocalAddresses() {
        assertTrue(NetworkAddressPolicy.isAllowed(InetAddress.getByName("192.168.1.20")))
        assertTrue(NetworkAddressPolicy.isAllowed(InetAddress.getByName("10.0.0.5")))
        assertTrue(NetworkAddressPolicy.isAllowed(InetAddress.getByName("169.254.10.4")))
        assertTrue(NetworkAddressPolicy.isAllowed(InetAddress.getByName("fd12:3456:789a::20")))
        assertTrue(NetworkAddressPolicy.isAllowed(InetAddress.getByName("fe80::20")))
    }

    @Test
    fun rejectsPublicAddresses() {
        assertFalse(NetworkAddressPolicy.isAllowed(InetAddress.getByName("8.8.8.8")))
        assertFalse(NetworkAddressPolicy.isAllowed(InetAddress.getByName("1.1.1.1")))
        assertFalse(NetworkAddressPolicy.isAllowed(InetAddress.getByName("2001:4860:4860::8888")))
    }

    @Test
    fun hotspotInterfaceWinsWhenRequested() {
        val address = InetAddress.getByName("192.168.43.1")
        val hotspot = NetworkAddressPolicy.score("ap0", address, preferHotspot = true)
        val regularWifi = NetworkAddressPolicy.score("wlan0", address, preferHotspot = true)
        assertTrue(hotspot > regularWifi)
    }

    @Test
    fun announcedEndpointsMustBeNumericLocalAndNotLoopback() {
        assertNotNull(NetworkAddressPolicy.parseAllowedAddress("192.168.1.25"))
        assertNotNull(NetworkAddressPolicy.parseAllowedAddress("fd12:3456:789a::25"))
        assertNotNull(NetworkAddressPolicy.parseAllowedAddress("[fd12:3456:789a::25]"))
        assertNotNull(NetworkAddressPolicy.parseAllowedAddress("fe80::25%1"))
        assertNull(NetworkAddressPolicy.parseAllowedAddress("127.0.0.1"))
        assertNull(NetworkAddressPolicy.parseAllowedAddress("::1"))
        assertNull(NetworkAddressPolicy.parseAllowedAddress("8.8.8.8"))
        assertNull(NetworkAddressPolicy.parseAllowedAddress("2001:4860:4860::8888"))
        assertNull(NetworkAddressPolicy.parseAllowedAddress("example.com"))
        assertNull(NetworkAddressPolicy.parseAllowedAddress("not-an-address"))
    }
    @Test
    fun remoteSelectionPrefersPrivateIpv4OverLinkLocalAndIpv6() {
        val selected =
            NetworkAddressPolicy.chooseRemoteAddress(
                listOf(
                    InetAddress.getByName("fe80::25"),
                    InetAddress.getByName("fd12:3456:789a::25"),
                    InetAddress.getByName("192.168.1.25"),
                )
            )
        assertTrue(selected?.hostAddress == "192.168.1.25")
    }

    @Test
    fun remoteSelectionRejectsLoopbackOnlyCandidates() {
        val selected =
            NetworkAddressPolicy.chooseRemoteAddress(
                listOf(
                    InetAddress.getByName("127.0.0.1"),
                    InetAddress.getByName("::1"),
                )
            )
        assertNull(selected)
    }

}
