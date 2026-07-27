package com.darius.unison.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class NetworkAddressPolicyTest {
    @Test
    fun acceptsPrivateAndLinkLocalAddresses() {
        assertTrue(NetworkAddressPolicy.isAllowed(InetAddress.getByName("192.168.1.20")))
        assertTrue(NetworkAddressPolicy.isAllowed(InetAddress.getByName("10.0.0.5")))
        assertTrue(NetworkAddressPolicy.isAllowed(InetAddress.getByName("169.254.10.4")))
    }

    @Test
    fun rejectsPublicAddresses() {
        assertFalse(NetworkAddressPolicy.isAllowed(InetAddress.getByName("8.8.8.8")))
        assertFalse(NetworkAddressPolicy.isAllowed(InetAddress.getByName("1.1.1.1")))
    }

    @Test
    fun hotspotInterfaceWinsWhenRequested() {
        val address = InetAddress.getByName("192.168.43.1") as java.net.Inet4Address
        val hotspot = NetworkAddressPolicy.score("ap0", address, preferHotspot = true)
        val regularWifi = NetworkAddressPolicy.score("wlan0", address, preferHotspot = true)
        assertTrue(hotspot > regularWifi)
    }

    @Test
    fun announcedEndpointsMustBePrivateIpv4AndNotLoopback() {
        assertNotNull(NetworkAddressPolicy.parseAllowedIpv4("192.168.1.25"))
        assertNull(NetworkAddressPolicy.parseAllowedIpv4("127.0.0.1"))
        assertNull(NetworkAddressPolicy.parseAllowedIpv4("8.8.8.8"))
        assertNull(NetworkAddressPolicy.parseAllowedIpv4("not-an-address"))
    }
}
