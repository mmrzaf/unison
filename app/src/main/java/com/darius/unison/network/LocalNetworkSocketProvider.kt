package com.darius.unison.network

import java.io.IOException
import java.net.InetAddress
import java.net.Socket

/**
 * Creates sockets on the local network route associated with a peer endpoint.
 *
 * The protocol only carries numeric LAN endpoints. Android-specific Network objects stay process
 * local and are never serialized. A provider may bind a socket to a concrete network or use a
 * validated endpoint fallback when Android exposes no Network (for example some hotspot downstream
 * interfaces).
 */
enum class LocalNetworkRouteFailureReason {
    /** Android policy (for example a non-bypassable VPN) rejected explicit network selection. */
    POLICY_BLOCKED,

    /** The process is not allowed to perform the requested network operation. */
    ACCESS_DENIED,

    /** The selected Android Network disappeared or stopped routing to the peer endpoint. */
    NETWORK_LOST,

    /** Socket creation failed for a reason that could not be classified more precisely. */
    SOCKET_PROVISION_FAILED,
}

/**
 * Typed failure raised while provisioning a local-network socket, before TCP connect begins.
 *
 * Transfer/control code can distinguish this from an ordinary connect timeout or refusal without
 * parsing platform exception text. [errno] and [errnoCode] are diagnostic metadata only.
 */
class LocalNetworkRouteException(
    val reason: LocalNetworkRouteFailureReason,
    val errno: String? = null,
    val errnoCode: Int? = null,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

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
