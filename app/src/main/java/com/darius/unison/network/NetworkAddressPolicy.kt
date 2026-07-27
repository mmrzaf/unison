package com.darius.unison.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Keeps Unison traffic on private/link-local networks and chooses the address most likely
 * reachable by nearby peers. Interface names are hints only; the address scope is authoritative.
 */
object NetworkAddressPolicy {
    private val IPV4_PATTERN = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")
    private val preferredInterfacePrefixes = listOf("wlan", "wifi", "eth", "en", "ap")
    private val hotspotInterfacePrefixes = listOf("ap", "softap", "swlan", "hotspot", "wlan")
    private val rejectedInterfacePrefixes = listOf("rmnet", "ccmni", "pdp", "tun", "tap", "wg", "dummy")

    fun isAllowed(address: InetAddress): Boolean = when (address) {
        is Inet4Address -> address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress
        is Inet6Address -> address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress
        else -> false
    }

    fun requireAllowed(address: InetAddress) {
        require(isAllowed(address)) { "Only local network addresses are allowed: ${address.hostAddress}" }
    }

    fun parseAllowedIpv4(value: String, allowLoopback: Boolean = false): Inet4Address? {
        if (!IPV4_PATTERN.matches(value)) return null
        val address = runCatching { InetAddress.getByName(value) as? Inet4Address }.getOrNull() ?: return null
        if (!allowLoopback && address.isLoopbackAddress) return null
        return address.takeIf(::isAllowed)
    }

    fun bestLocalIpv4(preferHotspot: Boolean = false): Inet4Address? =
        localIpv4Candidates(preferHotspot).firstOrNull()?.address

    internal fun localIpv4Candidates(preferHotspot: Boolean = false): List<AddressCandidate> =
        NetworkInterface.getNetworkInterfaces()
            ?.toList()
            .orEmpty()
            .asSequence()
            .filter { networkInterface ->
                runCatching {
                    networkInterface.isUp &&
                        !networkInterface.isLoopback &&
                        !networkInterface.isVirtual &&
                        rejectedInterfacePrefixes.none { networkInterface.name.startsWith(it, ignoreCase = true) }
                }.getOrDefault(false)
            }
            .flatMap { networkInterface ->
                networkInterface.inetAddresses.toList().asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && (it.isSiteLocalAddress || it.isLinkLocalAddress) }
                    .map { address ->
                        AddressCandidate(
                            interfaceName = networkInterface.name,
                            address = address,
                            score = score(networkInterface.name, address, preferHotspot),
                        )
                    }
            }
            .sortedWith(compareByDescending<AddressCandidate> { it.score }.thenBy { it.interfaceName })
            .toList()

    internal fun score(interfaceName: String, address: Inet4Address, preferHotspot: Boolean): Int {
        var score = 0
        if (address.isSiteLocalAddress) score += 100
        if (address.isLinkLocalAddress) score += 20
        val preferredIndex = preferredInterfacePrefixes.indexOfFirst { interfaceName.startsWith(it, ignoreCase = true) }
        if (preferredIndex >= 0) score += 50 - preferredIndex
        if (preferHotspot) {
            val hotspotIndex = hotspotInterfacePrefixes.indexOfFirst { interfaceName.startsWith(it, ignoreCase = true) }
            if (hotspotIndex >= 0) score += 200 - hotspotIndex * 20
        }
        return score
    }

    internal data class AddressCandidate(
        val interfaceName: String,
        val address: Inet4Address,
        val score: Int,
    )
}
