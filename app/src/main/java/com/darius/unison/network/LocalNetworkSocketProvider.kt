package com.darius.unison.network

import java.net.InetAddress
import java.net.Socket

/**
 * Creates sockets on the local network route associated with a peer endpoint.
 *
 * The protocol only carries numeric LAN endpoints. Android-specific Network objects stay process
 * local and are never serialized. A provider may bind a socket to a concrete network or use a
 * validated endpoint fallback when Android exposes no Network (for example some hotspot
 * downstream interfaces).
 */
interface LocalNetworkSocketProvider {
    fun createSocket(remoteAddress: InetAddress, purpose: String): RoutedSocket

    fun onConnected(route: RoutedSocket, socket: Socket)

    fun observeInboundSocket(socket: Socket)
}

data class RoutedSocket(
    val socket: Socket,
    val routeMode: LocalNetworkRouteMode,
    val networkId: String? = null,
    val transport: String? = null,
    val addressFamily: String,
) {
    fun diagnosticAttributes(): Map<String, Any?> =
        mapOf(
            "network.route_mode" to routeMode.name,
            "network.id" to networkId,
            "network.transport" to transport,
            "network.address_family" to addressFamily,
        )
}

enum class LocalNetworkRouteMode {
    /** Socket explicitly bound to an Android Network. */
    NETWORK_BOUND,

    /** Plain socket using Android's current default network. */
    SYSTEM_DEFAULT,

    /** Plain socket used when Android exposes no Network for the validated LAN endpoint. */
    ENDPOINT_FALLBACK,
}
