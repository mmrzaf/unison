package com.darius.unison.room

import kotlin.math.ceil
import kotlin.math.max

/**
 * Adaptive synchronized-execution lead derived only from the healthy playback cohort. Joining or
 * degraded peers cannot inflate command latency for listeners that are already synchronized.
 */
object TransportLeadTimePolicy {
    const val MIN_LEAD_NS = 150_000_000L
    const val MAX_LEAD_NS = 1_200_000_000L

    fun leadNs(
        readyPeerCount: Int,
        peerRoundTripsNs: Collection<Long> = emptyList(),
        peerUncertaintiesNs: Collection<Long> = emptyList(),
        reconnecting: Boolean = false,
    ): Long {
        val remoteReadyPeers = (readyPeerCount - 1).coerceAtLeast(0)
        if (remoteReadyPeers == 0) return MIN_LEAD_NS

        // A single outlier should repair locally rather than becoming the room's clock. Use a
        // robust upper quartile of healthy peer quality instead of the absolute maximum.
        val representativeRtt =
            upperQuartile(peerRoundTripsNs.map { it.coerceIn(0L, MAX_MEASURED_RTT_NS) })
        val representativeUncertainty =
            upperQuartile(
                peerUncertaintiesNs.map { it.coerceIn(0L, MAX_MEASURED_UNCERTAINTY_NS) }
            )
        val measuredNetworkAllowance =
            safeAdd(representativeRtt, safeMultiply(representativeUncertainty, 2L))
        val topologyFloor = remoteReadyPeers.toLong() * PER_REMOTE_PEER_FLOOR_NS
        val reconnectAllowance = if (reconnecting) RECONNECT_ALLOWANCE_NS else 0L
        return safeAdd(
                MIN_LEAD_NS,
                safeAdd(max(measuredNetworkAllowance, topologyFloor), reconnectAllowance),
            )
            .coerceIn(MIN_LEAD_NS, MAX_LEAD_NS)
    }

    private fun upperQuartile(values: Collection<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val index = (ceil(sorted.size * 0.75).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun safeAdd(left: Long, right: Long): Long =
        if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private fun safeMultiply(value: Long, multiplier: Long): Long =
        if (value > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else value * multiplier

    private const val PER_REMOTE_PEER_FLOOR_NS = 30_000_000L
    private const val RECONNECT_ALLOWANCE_NS = 350_000_000L
    private const val MAX_MEASURED_RTT_NS = 800_000_000L
    private const val MAX_MEASURED_UNCERTAINTY_NS = 300_000_000L
}
