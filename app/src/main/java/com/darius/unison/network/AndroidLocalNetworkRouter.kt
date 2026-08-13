package com.darius.unison.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import java.io.IOException
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
     * rest of the app. The associated [Network] remains local to this process.
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
            allowed.forEach { address ->
                discoveredNetworks[addressKey(address)] = resolvedNetwork
            }
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
            when {
                network == null -> endpointFallbackRoute(remoteAddress)
                network == connectivityManager.activeNetwork -> systemDefaultRoute(remoteAddress, network)
                else -> createNetworkBoundRoute(remoteAddress, network, purpose)
            }

        log.debug(
            TAG,
            DiagnosticCategory.NETWORK,
            "network.socket.route_selected",
            attributes = routed.diagnosticAttributes() + ("network.socket_purpose" to purpose),
        )
        return routed
    }

    /**
     * The system default route already follows [ConnectivityManager.activeNetwork]. Avoiding an
     * explicit bind here is both cheaper and more robust on Android 16 devices where binding a raw
     * socket back onto the already-active Wi-Fi network can fail with EPERM.
     */
    private fun systemDefaultRoute(
        remoteAddress: InetAddress,
        network: Network,
    ): RoutedSocket =
        RoutedSocket(
            socket = Socket(),
            routeMode = LocalNetworkRouteMode.SYSTEM_DEFAULT,
            networkId = networkId(network),
            transport = transportName(network),
            addressFamily = addressFamily(remoteAddress),
        )

    private fun endpointFallbackRoute(remoteAddress: InetAddress): RoutedSocket =
        RoutedSocket(
            socket = Socket(),
            routeMode = LocalNetworkRouteMode.ENDPOINT_FALLBACK,
            addressFamily = addressFamily(remoteAddress),
        )

    private fun createNetworkBoundRoute(
        remoteAddress: InetAddress,
        network: Network,
        purpose: String,
    ): RoutedSocket =
        try {
            RoutedSocket(
                socket = network.socketFactory.createSocket(),
                routeMode = LocalNetworkRouteMode.NETWORK_BOUND,
                networkId = networkId(network),
                transport = transportName(network),
                addressFamily = addressFamily(remoteAddress),
            )
        } catch (error: IOException) {
            fallbackAfterNetworkBindFailure(remoteAddress, network, purpose, error) ?: throw error
        } catch (error: SecurityException) {
            fallbackAfterNetworkBindFailure(remoteAddress, network, purpose, error) ?: throw error
        }

    /**
     * A network can become the system default between route selection and socket creation. If an
     * explicit bind fails, a plain socket is safe only when the current active LAN itself routes to
     * the validated endpoint. Otherwise preserve the failure instead of silently switching LANs.
     */
    private fun fallbackAfterNetworkBindFailure(
        remoteAddress: InetAddress,
        selectedNetwork: Network,
        purpose: String,
        error: Exception,
    ): RoutedSocket? {
        val activeNetwork =
            connectivityManager.activeNetwork
                ?.takeIf(::isLocalNetworkUsable)
                ?.takeIf { network -> networkRoutesTo(network, remoteAddress) }
                ?: return null

        log.warn(
            TAG,
            DiagnosticCategory.NETWORK,
            "network.socket.bind_failed_fallback",
            attributes =
                mapOf(
                    "network.socket_purpose" to purpose,
                    "network.selected_id" to networkId(selectedNetwork),
                    "network.selected_transport" to transportName(selectedNetwork),
                    "network.fallback_id" to networkId(activeNetwork),
                    "network.fallback_transport" to transportName(activeNetwork),
                    "network.address_family" to addressFamily(remoteAddress),
                ),
            throwable = error,
        )
        return systemDefaultRoute(remoteAddress, activeNetwork)
    }

    override fun onConnected(route: RoutedSocket, socket: Socket) {
        val localAddress = socket.localAddress?.takeIf(NetworkAddressPolicy::isAllowed)
        activeLocalAddress = localAddress

        if (route.routeMode == LocalNetworkRouteMode.NETWORK_BOUND) {
            resolveNetworkById(route.networkId)?.let { network ->
                activeRoomNetwork = network
            }
        } else if (localAddress != null) {
            findNetworkByLocalAddress(localAddress)?.let { network ->
                activeRoomNetwork = network
            }
        }

        log.debug(
            TAG,
            DiagnosticCategory.NETWORK,
            "network.socket.connected",
            attributes =
                routedAttributes(route) +
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
        if (network != null) {
            activeRoomNetwork = network
        }

        log.debug(
            TAG,
            DiagnosticCategory.NETWORK,
            "network.socket.inbound_route_observed",
            attributes = routeAttributes(localAddress, network),
        )
    }

    /**
     * Chooses the address peers should use for this device on the current room network.
     *
     * Preference order is intentionally stable: an address proven by a live socket, the room's
     * bound network, the system active LAN, any remaining local Android network, then direct
     * interface enumeration for hotspot/downstream cases where Android exposes no usable [Network].
     */
    fun preferredLocalAddress(preferHotspot: Boolean): InetAddress? {
        activeLocalAddress
            ?.takeIf(NetworkAddressPolicy::isAllowed)
            ?.let { address -> return address }

        activeRoomNetwork
            ?.takeIf(::isLocalNetworkUsable)
            ?.let(::linkProperties)
            ?.let { properties -> bestAddress(properties, preferHotspot) }
            ?.let { address -> return address }

        val active = connectivityManager.activeNetwork
        if (active != null && isLocalNetworkUsable(active)) {
            linkProperties(active)
                ?.let { properties -> bestAddress(properties, preferHotspot) }
                ?.let { address -> return address }
        }

        currentNetworks()
            .asSequence()
            .filter(::isLocalNetworkUsable)
            .mapNotNull { network ->
                val properties = linkProperties(network) ?: return@mapNotNull null
                val address = bestAddress(properties, preferHotspot) ?: return@mapNotNull null
                Triple(network, properties, address)
            }
            .maxByOrNull { (_, properties, address) ->
                NetworkAddressPolicy.score(
                    properties.interfaceName.orEmpty(),
                    address,
                    preferHotspot,
                )
            }
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
            ?.takeIf { network -> networkRoutesTo(network, remoteAddress) }
            ?.let { network ->
                activeRoomNetwork = network
                return network
            }

        activeRoomNetwork
            ?.takeIf(::isLocalNetworkUsable)
            ?.takeIf { network -> networkRoutesTo(network, remoteAddress) }
            ?.let { network -> return network }

        return inferNetwork(remoteAddress)?.also { network ->
            activeRoomNetwork = network
        }
    }

    private fun inferNetwork(remoteAddress: InetAddress): Network? =
        currentNetworks()
            .asSequence()
            .filter(::isLocalNetworkUsable)
            .filter { network -> networkRoutesTo(network, remoteAddress) }
            .maxByOrNull(::transportScore)

    private fun findNetworkByLocalAddress(localAddress: InetAddress): Network? =
        currentNetworks().firstOrNull { network ->
            isLocalNetworkUsable(network) &&
                linkProperties(network)
                    ?.linkAddresses
                    ?.any { linkAddress -> linkAddress.address == localAddress } == true
        }

    private fun resolveNetworkById(id: String?): Network? {
        if (id == null) return null

        activeRoomNetwork
            ?.takeIf { network -> networkId(network) == id }
            ?.takeIf(::isLocalNetworkUsable)
            ?.let { network -> return network }

        discoveredNetworks.values
            .firstOrNull { network ->
                networkId(network) == id && isLocalNetworkUsable(network)
            }
            ?.let { network -> return network }

        return currentNetworks().firstOrNull { network ->
            networkId(network) == id && isLocalNetworkUsable(network)
        }
    }

    private fun networkRoutesTo(network: Network, remoteAddress: InetAddress): Boolean =
        linkProperties(network)?.routes?.any { route -> route.matches(remoteAddress) } == true

    private fun isLocalNetworkUsable(network: Network): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun transportScore(network: Network): Int {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> 30
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> 20
            else -> 0
        }
    }

    private fun linkProperties(network: Network): LinkProperties? =
        connectivityManager.getLinkProperties(network)

    private fun bestAddress(properties: LinkProperties, preferHotspot: Boolean): InetAddress? =
        properties.linkAddresses
            .asSequence()
            .map { linkAddress -> linkAddress.address }
            .filter { address ->
                !address.isLoopbackAddress && NetworkAddressPolicy.isAllowed(address)
            }
            .maxByOrNull { address ->
                NetworkAddressPolicy.score(
                    properties.interfaceName.orEmpty(),
                    address,
                    preferHotspot,
                )
            }

    @Suppress("DEPRECATION")
    private fun currentNetworks(): Array<Network> =
        connectivityManager.allNetworks

    private fun routeAttributes(address: InetAddress, network: Network?): Map<String, Any?> =
        mapOf(
            "network.route_mode" to
                if (network == null) {
                    LocalNetworkRouteMode.ENDPOINT_FALLBACK.name
                } else {
                    LocalNetworkRouteMode.NETWORK_BOUND.name
                },
            "network.id" to network?.let(::networkId),
            "network.transport" to network?.let(::transportName),
            "network.address_family" to addressFamily(address),
        )

    private fun routedAttributes(route: RoutedSocket): Map<String, Any?> =
        route.diagnosticAttributes()

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
