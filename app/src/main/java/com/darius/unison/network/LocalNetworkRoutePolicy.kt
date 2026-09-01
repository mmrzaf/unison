package com.darius.unison.network

/**
 * Pure routing policy for an outbound numeric LAN endpoint.
 *
 * Android Network instances deliberately stay out of this type so the policy can be exhaustively
 * unit-tested. The router is responsible for proving that a selected network is a usable
 * Wi-Fi/Ethernet network that routes to the endpoint before constructing this context.
 */
internal object LocalNetworkRoutePolicy {
    fun choose(context: LocalNetworkRouteContext): LocalNetworkRouteMode =
        when {
            context.activeNetworkIsVpn -> LocalNetworkRouteMode.SYSTEM_DEFAULT
            !context.hasSelectedNetwork -> LocalNetworkRouteMode.ENDPOINT_FALLBACK
            context.selectedNetworkIsActive -> LocalNetworkRouteMode.SYSTEM_DEFAULT
            else -> LocalNetworkRouteMode.NETWORK_BOUND
        }
}

internal data class LocalNetworkRouteContext(
    val hasSelectedNetwork: Boolean,
    val selectedNetworkIsActive: Boolean,
    val activeNetworkIsVpn: Boolean,
)
