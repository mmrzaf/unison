package com.darius.unison.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalNetworkRoutePolicyTest {
    @Test
    fun activeVpnAlwaysUsesSystemDefaultRoute() {
        assertEquals(
            LocalNetworkRouteMode.SYSTEM_DEFAULT,
            choose(hasSelected = true, selectedIsActive = false, activeIsVpn = true),
        )
        assertEquals(
            LocalNetworkRouteMode.SYSTEM_DEFAULT,
            choose(hasSelected = false, selectedIsActive = false, activeIsVpn = true),
        )
    }

    @Test
    fun selectedActiveLanUsesSystemDefaultRoute() {
        assertEquals(
            LocalNetworkRouteMode.SYSTEM_DEFAULT,
            choose(hasSelected = true, selectedIsActive = true, activeIsVpn = false),
        )
    }

    @Test
    fun nonDefaultSelectedLanUsesExplicitNetworkBinding() {
        assertEquals(
            LocalNetworkRouteMode.NETWORK_BOUND,
            choose(hasSelected = true, selectedIsActive = false, activeIsVpn = false),
        )
    }

    @Test
    fun missingAndroidLanNetworkUsesValidatedEndpointFallback() {
        assertEquals(
            LocalNetworkRouteMode.ENDPOINT_FALLBACK,
            choose(hasSelected = false, selectedIsActive = false, activeIsVpn = false),
        )
    }

    private fun choose(
        hasSelected: Boolean,
        selectedIsActive: Boolean,
        activeIsVpn: Boolean,
    ): LocalNetworkRouteMode =
        LocalNetworkRoutePolicy.choose(
            LocalNetworkRouteContext(
                hasSelectedNetwork = hasSelected,
                selectedNetworkIsActive = selectedIsActive,
                activeNetworkIsVpn = activeIsVpn,
            )
        )
}
