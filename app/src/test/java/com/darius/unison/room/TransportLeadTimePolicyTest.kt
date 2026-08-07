package com.darius.unison.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportLeadTimePolicyTest {
    @Test
    fun localOnlyCohortUsesResponsiveMinimumLead() {
        assertEquals(
            TransportLeadTimePolicy.MIN_LEAD_NS,
            TransportLeadTimePolicy.leadNs(readyPeerCount = 1),
        )
    }

    @Test
    fun measuredNetworkAndClockRiskIncreaseLead() {
        val stable =
            TransportLeadTimePolicy.leadNs(
                readyPeerCount = 3,
                peerRoundTripsNs = listOf(20_000_000L, 25_000_000L),
                peerUncertaintiesNs = listOf(2_000_000L, 3_000_000L),
            )
        val uncertain =
            TransportLeadTimePolicy.leadNs(
                readyPeerCount = 3,
                peerRoundTripsNs = listOf(120_000_000L, 140_000_000L),
                peerUncertaintiesNs = listOf(80_000_000L, 90_000_000L),
                reconnecting = true,
            )
        assertTrue(uncertain > stable)
    }

    @Test
    fun aSingleOutlierDoesNotBecomeTheRoomClock() {
        val lead =
            TransportLeadTimePolicy.leadNs(
                readyPeerCount = 8,
                peerRoundTripsNs =
                    listOf(
                        20_000_000L,
                        22_000_000L,
                        24_000_000L,
                        25_000_000L,
                        27_000_000L,
                        30_000_000L,
                        700_000_000L,
                    ),
                peerUncertaintiesNs =
                    listOf(
                        2_000_000L,
                        2_000_000L,
                        3_000_000L,
                        3_000_000L,
                        4_000_000L,
                        5_000_000L,
                        250_000_000L,
                    ),
            )
        assertTrue(lead < 500_000_000L)
    }

    @Test
    fun leadIsAlwaysBounded() {
        assertEquals(
            TransportLeadTimePolicy.MAX_LEAD_NS,
            TransportLeadTimePolicy.leadNs(
                readyPeerCount = 100,
                peerRoundTripsNs = List(99) { Long.MAX_VALUE / 4 },
                peerUncertaintiesNs = List(99) { Long.MAX_VALUE / 4 },
                reconnecting = true,
            ),
        )
    }

    @Test
    fun measuredHealthyRoomStaysResponsive() {
        val lead =
            TransportLeadTimePolicy.leadNs(
                readyPeerCount = 2,
                peerRoundTripsNs = listOf(35_000_000L),
                peerUncertaintiesNs = listOf(4_000_000L),
            )
        assertTrue(lead in 150_000_000L..250_000_000L)
    }
}
