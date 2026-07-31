package com.darius.unison.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportLeadTimePolicyTest {
    @Test
    fun quietRoomUsesResponsiveMinimumLead() {
        assertEquals(
            TransportLeadTimePolicy.MIN_LEAD_NS,
            TransportLeadTimePolicy.leadNs(
                connectedPeerCount = 1,
                clockReadyPeerCount = 1,
            ),
        )
    }

    @Test
    fun networkAndClockRiskIncreaseLead() {
        val stable = TransportLeadTimePolicy.leadNs(connectedPeerCount = 3, clockReadyPeerCount = 3)
        val uncertain =
            TransportLeadTimePolicy.leadNs(
                connectedPeerCount = 3,
                clockReadyPeerCount = 1,
                maxPeerRoundTripNs = 120_000_000L,
                maxPeerUncertaintyNs = 80_000_000L,
                reconnecting = true,
            )
        assertTrue(uncertain > stable)
    }

    @Test
    fun leadIsAlwaysBounded() {
        assertEquals(
            TransportLeadTimePolicy.MAX_LEAD_NS,
            TransportLeadTimePolicy.leadNs(
                connectedPeerCount = 100,
                clockReadyPeerCount = 0,
                maxPeerRoundTripNs = Long.MAX_VALUE / 4,
                maxPeerUncertaintyNs = Long.MAX_VALUE / 4,
                reconnecting = true,
            ),
        )
    }

    @Test
    fun measuredHealthyRoomStaysResponsive() {
        val lead =
            TransportLeadTimePolicy.leadNs(
                connectedPeerCount = 2,
                clockReadyPeerCount = 2,
                maxPeerRoundTripNs = 35_000_000L,
                maxPeerUncertaintyNs = 4_000_000L,
            )

        assertTrue(lead in 150_000_000L..250_000_000L)
    }
}
