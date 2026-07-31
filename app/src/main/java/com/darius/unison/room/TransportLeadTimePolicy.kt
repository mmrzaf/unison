package com.darius.unison.room

import kotlin.math.max

/** Adaptive synchronized-execution lead used by every room size and topology. */
object TransportLeadTimePolicy {
    const val MIN_LEAD_NS = 150_000_000L
    const val MAX_LEAD_NS = 1_200_000_000L

    fun leadNs(
        connectedPeerCount: Int,
        clockReadyPeerCount: Int,
        maxPeerRoundTripNs: Long = 0L,
        maxPeerUncertaintyNs: Long = 0L,
        reconnecting: Boolean = false,
    ): Long {
        val remotePeers = (connectedPeerCount - 1).coerceAtLeast(0)
        val unsynchronizedPeers = (connectedPeerCount - clockReadyPeerCount).coerceAtLeast(0)
        if (remotePeers == 0) return MIN_LEAD_NS

        val measuredNetworkAllowance =
            safeAdd(
                maxPeerRoundTripNs.coerceIn(0L, MAX_MEASURED_RTT_NS),
                safeMultiply(maxPeerUncertaintyNs.coerceIn(0L, MAX_MEASURED_UNCERTAINTY_NS), 2L),
            )
        val topologyFloor = remotePeers.toLong() * PER_REMOTE_PEER_FLOOR_NS
        val readinessAllowance = unsynchronizedPeers.toLong() * UNSYNCHRONIZED_PEER_ALLOWANCE_NS
        val reconnectAllowance = if (reconnecting) RECONNECT_ALLOWANCE_NS else 0L
        return safeAdd(
                MIN_LEAD_NS,
                safeAdd(
                    max(measuredNetworkAllowance, topologyFloor),
                    safeAdd(readinessAllowance, reconnectAllowance),
                ),
            )
            .coerceIn(MIN_LEAD_NS, MAX_LEAD_NS)
    }

    private fun safeAdd(left: Long, right: Long): Long =
        if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private fun safeMultiply(value: Long, multiplier: Long): Long =
        if (value > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else value * multiplier

    private const val PER_REMOTE_PEER_FLOOR_NS = 30_000_000L
    private const val UNSYNCHRONIZED_PEER_ALLOWANCE_NS = 220_000_000L
    private const val RECONNECT_ALLOWANCE_NS = 350_000_000L
    private const val MAX_MEASURED_RTT_NS = 800_000_000L
    private const val MAX_MEASURED_UNCERTAINTY_NS = 300_000_000L
}
