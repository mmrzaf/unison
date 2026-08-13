package com.darius.unison.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Keeps Unison traffic on private/link-local networks and chooses the address most likely reachable
 * by nearby peers. Interface names are hints only; the address scope is authoritative.
 */
object NetworkAddressPolicy {
    private val IPV4_PATTERN = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")
    private val IPV6_LITERAL_PATTERN = Regex("^[0-9A-Fa-f:.]+(?:%[A-Za-z0-9_.-]+)?$")
    private val preferredInterfacePrefixes = listOf("wlan", "wifi", "eth", "en", "ap")
    private val hotspotInterfacePrefixes = listOf("ap", "softap", "swlan", "hotspot", "wlan")
    private val rejectedInterfacePrefixes =
        listOf("rmnet", "ccmni", "pdp", "tun", "tap", "wg", "dummy")

    fun isAllowed(address: InetAddress): Boolean =
        when (address) {
            is Inet4Address ->
                address.isLoopbackAddress ||
                    address.isLinkLocalAddress ||
                    address.isSiteLocalAddress
            is Inet6Address ->
                address.isLoopbackAddress ||
                    address.isLinkLocalAddress ||
                    address.isSiteLocalAddress ||
                    address.isUniqueLocalAddress()
            else -> false
        }

    fun requireAllowed(address: InetAddress) {
        require(isAllowed(address)) {
            "Only local network addresses are allowed: ${address.hostAddress}"
        }
    }

    /** Parses numeric IPv4/IPv6 endpoints without accepting DNS names or public addresses. */
    fun parseAllowedAddress(value: String, allowLoopback: Boolean = false): InetAddress? {
        val candidate = value.trim().removePrefix("[").removeSuffix("]")
        if (
            !IPV4_PATTERN.matches(candidate) &&
                !(candidate.contains(':') && IPV6_LITERAL_PATTERN.matches(candidate))
        ) {
            return null
        }
        val address = runCatching { InetAddress.getByName(candidate) }.getOrNull() ?: return null
        if (!allowLoopback && address.isLoopbackAddress) return null
        return address.takeIf(::isAllowed)
    }

    fun parseAllowedIpv4(value: String, allowLoopback: Boolean = false): Inet4Address? =
        parseAllowedAddress(value, allowLoopback) as? Inet4Address


    fun chooseRemoteAddress(addresses: Collection<InetAddress>): InetAddress? =
        addresses
            .asSequence()
            .filter { address -> !address.isLoopbackAddress && isAllowed(address) }
            .maxWithOrNull(
                compareBy<InetAddress> { remoteAddressScore(it) }
                    .thenByDescending { it.hostAddress.orEmpty() }
            )

    internal fun remoteAddressScore(address: InetAddress): Int =
        when (address) {
            is Inet4Address ->
                when {
                    address.isSiteLocalAddress -> 400
                    address.isLinkLocalAddress -> 200
                    address.isLoopbackAddress -> 0
                    else -> 100
                }
            is Inet6Address ->
                when {
                    address.isUniqueLocalAddress() -> 350
                    address.isSiteLocalAddress -> 325
                    address.isLinkLocalAddress -> 150
                    address.isLoopbackAddress -> 0
                    else -> 100
                }
            else -> 0
        }

    fun bestLocalAddress(preferHotspot: Boolean = false): InetAddress? =
        localAddressCandidates(preferHotspot).firstOrNull()?.address

    fun bestLocalIpv4(preferHotspot: Boolean = false): Inet4Address? =
        localAddressCandidates(preferHotspot).firstOrNull { it.address is Inet4Address }?.address
            as? Inet4Address

    internal fun localAddressCandidates(preferHotspot: Boolean = false): List<AddressCandidate> =
        NetworkInterface.getNetworkInterfaces()
            ?.toList()
            .orEmpty()
            .asSequence()
            .filter { networkInterface ->
                runCatching {
                        networkInterface.isUp &&
                            !networkInterface.isLoopback &&
                            !networkInterface.isVirtual &&
                            rejectedInterfacePrefixes.none {
                                networkInterface.name.startsWith(it, ignoreCase = true)
                            }
                    }
                    .getOrDefault(false)
            }
            .flatMap { networkInterface ->
                networkInterface.inetAddresses
                    .toList()
                    .asSequence()
                    .filter { address -> !address.isLoopbackAddress && isAllowed(address) }
                    .map { address ->
                        AddressCandidate(
                            interfaceName = networkInterface.name,
                            address = address,
                            score = score(networkInterface.name, address, preferHotspot),
                        )
                    }
            }
            .sortedWith(
                compareByDescending<AddressCandidate> { it.score }
                    .thenBy { it.interfaceName }
                    .thenBy { it.address.hostAddress }
            )
            .toList()

    internal fun score(interfaceName: String, address: InetAddress, preferHotspot: Boolean): Int {
        var score =
            when (address) {
                is Inet4Address ->
                    when {
                        address.isSiteLocalAddress -> 120
                        address.isLinkLocalAddress -> 30
                        else -> 0
                    }
                is Inet6Address ->
                    when {
                        address.isUniqueLocalAddress() -> 110
                        address.isSiteLocalAddress -> 100
                        address.isLinkLocalAddress -> 20
                        else -> 0
                    }
                else -> 0
            }
        val preferredIndex = preferredInterfacePrefixes.indexOfFirst {
            interfaceName.startsWith(it, ignoreCase = true)
        }
        if (preferredIndex >= 0) score += 50 - preferredIndex
        if (preferHotspot) {
            val hotspotIndex = hotspotInterfacePrefixes.indexOfFirst {
                interfaceName.startsWith(it, ignoreCase = true)
            }
            if (hotspotIndex >= 0) score += 200 - hotspotIndex * 20
        }
        return score
    }

    private fun Inet6Address.isUniqueLocalAddress(): Boolean =
        address.isNotEmpty() && (address[0].toInt() and 0xFE) == 0xFC

    internal data class AddressCandidate(
        val interfaceName: String,
        val address: InetAddress,
        val score: Int,
    )
}
