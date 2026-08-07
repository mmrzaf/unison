package com.darius.unison.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local authority for Unison LAN routing.
 *
 * Discovery records the Android [Network] associated with resolved endpoints when available.
 * Control and transfer sockets later ask this router for a socket, so they use the same network
 * instead of relying on the system default route. Android 11 devices can still infer the matching
 * Wi-Fi/Ethernet network from LinkProperties. Some LocalOnlyHotspot downstream interfaces have no
 * Network object; those use the validated numeric-endpoint fallback deliberately.
 */
class AndroidLocalNetworkRouter(
    context: Context,
    private val log: DiagnosticLog,
) : LocalNetworkSocketProvider {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val discoveredNetworks = ConcurrentHashMap<String, Network>()
    @Volatile private var activeRoomNetwork: Network? = null
    @Volatile private var activeLocalAddress: InetAddress? = null

    fun clearDiscoveryRoutes() {
        discoveredNetworks.clear()
    }

    fun resetSession() {
        activeRoomNetwork = null
        activeLocalAddress = null
    }

    /**
     * Records all valid addresses returned by NSD and returns the best endpoint to expose to the
     * rest of the app. The associated Network remains local to this process.
     */
    fun rememberResolvedService(
        addresses: Collection<InetAddress>,
        network: Network?,
    ): InetAddress? {
        val allowed = addresses.filter(NetworkAddressPolicy::isAllowed)
        if (allowed.isEmpty()) return null
        val selected = NetworkAddressPolicy.chooseRemoteAddress(allowed) ?: return null
        val resolvedNetwork = network?.takeIf(::isLocalNetworkUsable) ?: inferNetwork(selected)
        if (resolvedNetwork != null) {
            allowed.forEach { discoveredNetworks[addressKey(it)] = resolvedNetwork }
        }
        log.debug(
            TAG,
            DiagnosticCategory.NETWORK,
            "network.route.discovered",
            attributes = routeAttributes(selected, resolvedNetwork),
        )
        return selected
    }

    override fun createSocket(remoteAddress: InetAddress, purpose: String): RoutedSocket {
        NetworkAddressPolicy.requireAllowed(remoteAddress)
        val network = resolveNetwork(remoteAddress)
        val routed =
            if (network != null) {
                RoutedSocket(
                    socket = network.socketFactory.createSocket(),
                    routeMode = LocalNetworkRouteMode.NETWORK_BOUND,
                    networkId = networkId(network),
                    transport = transportName(network),
                    addressFamily = addressFamily(remoteAddress),
                )
            } else {
                RoutedSocket(
                    socket = Socket(),
                    routeMode = LocalNetworkRouteMode.ENDPOINT_FALLBACK,
                    addressFamily = addressFamily(remoteAddress),
                )
            }
        log.debug(
            TAG,
            DiagnosticCategory.NETWORK,
            "network.socket.route_selected",
            attributes = routed.diagnosticAttributes() + ("network.socket_purpose" to purpose),
        )
        return routed
    }

    override fun onConnected(route: RoutedSocket, socket: Socket) {
        val localAddress = socket.localAddress?.takeIf(NetworkAddressPolicy::isAllowed)
        activeLocalAddress = localAddress
        if (route.routeMode == LocalNetworkRouteMode.NETWORK_BOUND) {
            resolveNetworkById(route.networkId)?.let { activeRoomNetwork = it }
        } else if (localAddress != null) {
            findNetworkByLocalAddress(localAddress)?.let { activeRoomNetwork = it }
        }
        log.debug(
            TAG,
            DiagnosticCategory.NETWORK,
            "network.socket.connected",
            attributes = route.diagnosticAttributes() +
                mapOf(
                    "network.local_address_family" to localAddress?.let(::addressFamily),
                ),
        )
    }

    /** Records the LAN selected by an inbound room/control or transfer connection. */
    override fun observeInboundSocket(socket: Socket) {
        val localAddress = socket.localAddress?.takeIf(NetworkAddressPolicy::isAllowed) ?: return
        activeLocalAddress = localAddress
        val network = findNetworkByLocalAddress(localAddress)
        if (network != null) activeRoomNetwork = network
        log.debug(
            TAG,
            DiagnosticCategory.NETWORK,
            "network.socket.inbound_route_observed",
            attributes = routeAttributes(localAddress, network),
        )
    }

    /**
     * Chooses the address that peers should use for this device on the current room network.
     * Interface enumeration is only a fallback for hotspot/downstream cases where Android exposes
     * no Network object for the LAN.
     */
    fun preferredLocalAddress(preferHotspot: Boolean): InetAddress? {
        activeLocalAddress?.takeIf(NetworkAddressPolicy::isAllowed)?.let { return it }
        activeRoomNetwork
            ?.takeIf(::isLocalNetworkUsable)
            ?.let(::linkProperties)
            ?.let { bestAddress(it, preferHotspot) }
            ?.let { return it }

        val active = connectivityManager.activeNetwork
        if (active != null && isLocalNetworkUsable(active)) {
            linkProperties(active)?.let { bestAddress(it, preferHotspot) }?.let { return it }
        }

        connectivityManager.getAllNetworks()
            .asSequence()
            .filter(::isLocalNetworkUsable)
            .mapNotNull { network ->
                val properties = linkProperties(network) ?: return@mapNotNull null
                val address = bestAddress(properties, preferHotspot) ?: return@mapNotNull null
                Triple(network, properties, address)
            }
            .sortedByDescending { (_, properties, address) ->
                NetworkAddressPolicy.score(properties.interfaceName.orEmpty(), address, preferHotspot)
            }
            .firstOrNull()
            ?.let { (network, _, address) ->
                activeRoomNetwork = network
                activeLocalAddress = address
                return address
            }

        return NetworkAddressPolicy.bestLocalAddress(preferHotspot)
    }

    private fun resolveNetwork(remoteAddress: InetAddress): Network? {
        discoveredNetworks[addressKey(remoteAddress)]
            ?.takeIf(::isLocalNetworkUsable)
            ?.takeIf { networkRoutesTo(it, remoteAddress) }
            ?.let {
                activeRoomNetwork = it
                return it
            }
        activeRoomNetwork
            ?.takeIf(::isLocalNetworkUsable)
            ?.takeIf { networkRoutesTo(it, remoteAddress) }
            ?.let { return it }
        return inferNetwork(remoteAddress)?.also { activeRoomNetwork = it }
    }

    private fun inferNetwork(remoteAddress: InetAddress): Network? =
        connectivityManager.getAllNetworks()
            .asSequence()
            .filter(::isLocalNetworkUsable)
            .filter { networkRoutesTo(it, remoteAddress) }
            .sortedByDescending { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                when {
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> 30
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> 20
                    else -> 0
                }
            }
            .firstOrNull()

    private fun networkRoutesTo(network: Network, remoteAddress: InetAddress): Boolean =
        linkProperties(network)?.routes?.any { route -> route.matches(remoteAddress) } == true

    private fun findNetworkByLocalAddress(localAddress: InetAddress): Network? =
        connectivityManager.getAllNetworks().firstOrNull { network ->
            isLocalNetworkUsable(network) &&
                linkProperties(network)?.linkAddresses?.any { it.address == localAddress } == true
        }

    private fun resolveNetworkById(id: String?): Network? {
        if (id == null) return null
        return connectivityManager.getAllNetworks().firstOrNull { networkId(it) == id && isLocalNetworkUsable(it) }
    }

    private fun isLocalNetworkUsable(network: Network): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun linkProperties(network: Network): LinkProperties? =
        connectivityManager.getLinkProperties(network)

    private fun bestAddress(properties: LinkProperties, preferHotspot: Boolean): InetAddress? =
        properties.linkAddresses
            .asSequence()
            .map { it.address }
            .filter { !it.isLoopbackAddress && NetworkAddressPolicy.isAllowed(it) }
            .maxByOrNull {
                NetworkAddressPolicy.score(properties.interfaceName.orEmpty(), it, preferHotspot)
            }

    private fun routeAttributes(address: InetAddress, network: Network?): Map<String, Any?> =
        mapOf(
            "network.route_mode" to
                if (network == null) LocalNetworkRouteMode.ENDPOINT_FALLBACK.name
                else LocalNetworkRouteMode.NETWORK_BOUND.name,
            "network.id" to network?.let(::networkId),
            "network.transport" to network?.let(::transportName),
            "network.address_family" to addressFamily(address),
        )

    private fun transportName(network: Network): String {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
            else -> "UNKNOWN"
        }
    }

    private fun networkId(network: Network): String = network.networkHandle.toString(16)

    private fun addressKey(address: InetAddress): String =
        address.hostAddress.orEmpty().substringBefore('%').lowercase()

    private fun addressFamily(address: InetAddress): String =
        when (address) {
            is Inet4Address -> "IPV4"
            is Inet6Address -> "IPV6"
            else -> "UNKNOWN"
        }

    private companion object {
        const val TAG = "AndroidLocalNetworkRouter"
    }
}
