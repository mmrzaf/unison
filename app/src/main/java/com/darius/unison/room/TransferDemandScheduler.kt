package com.darius.unison.room

import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferPriority

/** One destination's request for content, ranked by playback consequence rather than arrival order. */
internal data class TransferDemand(
    val trackId: TrackId,
    val destinationPeerId: PeerId,
    val priority: TransferPriority,
    val neededByCoordinatorNs: Long? = null,
    val requestedAtCoordinatorNs: Long,
)

internal data class TransferRouteKey(
    val trackId: TrackId,
    val sourcePeerId: PeerId,
    val destinationPeerId: PeerId,
)

internal data class TransferRouteHealth(
    val failures: Int = 0,
    val lastFailureCoordinatorNs: Long = 0L,
)

/**
 * Pure coordinator-side scheduling policy. Authorization/transport execution remains in
 * [com.darius.unison.transfer.TransferManager]; this class decides what deserves a transfer slot
 * and which available source should serve it.
 */
internal class TransferDemandScheduler(
    private val maxActivePerDestination: Int = DEFAULT_MAX_ACTIVE_PER_DESTINATION,
) {
    init {
        require(maxActivePerDestination > 0)
    }

    private val demands = mutableMapOf<Pair<TrackId, PeerId>, TransferDemand>()
    private val activeRoutes = mutableMapOf<Pair<TrackId, PeerId>, TransferRouteKey>()
    private val routeHealth = mutableMapOf<TransferRouteKey, TransferRouteHealth>()
    private val sourceActiveUploads = mutableMapOf<PeerId, Int>()

    fun upsert(demand: TransferDemand) {
        val key = demand.trackId to demand.destinationPeerId
        val previous = demands[key]
        if (
            previous == null ||
                demand.requestedAtCoordinatorNs >= previous.requestedAtCoordinatorNs ||
                demand.isMoreUrgentThan(previous)
        ) {
            demands[key] = demand
        }
    }

    fun remove(trackId: TrackId, destinationPeerId: PeerId) {
        demands.remove(trackId to destinationPeerId)
        activeRoutes.remove(trackId to destinationPeerId)?.let(::decrementSource)
    }

    fun removePeer(peerId: PeerId) {
        demands.keys.removeAll { (_, destination) -> destination == peerId }
        activeRoutes.entries
            .filter { (_, route) -> route.sourcePeerId == peerId || route.destinationPeerId == peerId }
            .map { it.key }
            .forEach { key -> activeRoutes.remove(key)?.let(::decrementSource) }
        routeHealth.keys.removeAll { it.sourcePeerId == peerId || it.destinationPeerId == peerId }
        sourceActiveUploads.remove(peerId)
    }

    fun clear() {
        demands.clear()
        activeRoutes.clear()
        routeHealth.clear()
        sourceActiveUploads.clear()
    }

    fun markActive(route: TransferRouteKey) {
        val key = route.trackId to route.destinationPeerId
        val prior = activeRoutes.put(key, route)
        if (prior != null) decrementSource(prior)
        sourceActiveUploads[route.sourcePeerId] = (sourceActiveUploads[route.sourcePeerId] ?: 0) + 1
    }

    fun markTerminal(trackId: TrackId, destinationPeerId: PeerId) {
        activeRoutes.remove(trackId to destinationPeerId)?.let(::decrementSource)
    }

    fun recordRouteFailure(route: TransferRouteKey, nowCoordinatorNs: Long) {
        val previous = routeHealth[route] ?: TransferRouteHealth()
        routeHealth[route] =
            previous.copy(
                failures = (previous.failures + 1).coerceAtMost(MAX_FAILURE_PENALTY),
                lastFailureCoordinatorNs = nowCoordinatorNs,
            )
        markTerminal(route.trackId, route.destinationPeerId)
    }

    fun activeCount(destinationPeerId: PeerId): Int =
        activeRoutes.values.count { it.destinationPeerId == destinationPeerId }

    fun pendingDemands(destinationPeerId: PeerId): List<TransferDemand> {
        if (activeCount(destinationPeerId) >= maxActivePerDestination) return emptyList()
        return demands.values
            .asSequence()
            .filter { it.destinationPeerId == destinationPeerId }
            .filter { (it.trackId to destinationPeerId) !in activeRoutes }
            .sortedWith(DEMAND_ORDER)
            .toList()
    }

    fun nextDemand(destinationPeerId: PeerId): TransferDemand? =
        pendingDemands(destinationPeerId).firstOrNull()

    /** Returns a lower-priority active route that should yield to [incoming], if any. */
    fun preemptionCandidate(incoming: TransferDemand): TransferRouteKey? {
        if (activeCount(incoming.destinationPeerId) < maxActivePerDestination) return null
        return activeRoutes.values
            .asSequence()
            .filter { it.destinationPeerId == incoming.destinationPeerId }
            .mapNotNull { route -> demandFor(route.trackId, route.destinationPeerId)?.let { it to route } }
            .maxWithOrNull(compareBy<Pair<TransferDemand, TransferRouteKey>> { it.first.priority.ordinal }
                .thenBy { it.first.neededByCoordinatorNs ?: Long.MAX_VALUE })
            ?.takeIf { (activeDemand, _) -> DEMAND_ORDER.compare(incoming, activeDemand) < 0 }
            ?.second
    }

    fun chooseSource(
        demand: TransferDemand,
        availableSources: Set<PeerId>,
        nowCoordinatorNs: Long,
        isUsable: (PeerId) -> Boolean,
    ): PeerId? =
        availableSources.asSequence()
            .filter { it != demand.destinationPeerId && isUsable(it) }
            .filter { source ->
                val health =
                    routeHealth[TransferRouteKey(demand.trackId, source, demand.destinationPeerId)]
                health == null ||
                    nowCoordinatorNs < health.lastFailureCoordinatorNs ||
                    nowCoordinatorNs - health.lastFailureCoordinatorNs >= ROUTE_RETRY_COOLDOWN_NS
            }
            .minWithOrNull(
                compareBy<PeerId> { source ->
                    routeHealth[
                        TransferRouteKey(demand.trackId, source, demand.destinationPeerId)
                    ]?.failures ?: 0
                }.thenBy { sourceActiveUploads[it] ?: 0 }
                    .thenBy { it.value }
            )

    fun demandFor(trackId: TrackId, destinationPeerId: PeerId): TransferDemand? =
        demands[trackId to destinationPeerId]

    private fun decrementSource(route: TransferRouteKey) {
        val current = sourceActiveUploads[route.sourcePeerId] ?: return
        if (current <= 1) sourceActiveUploads.remove(route.sourcePeerId)
        else sourceActiveUploads[route.sourcePeerId] = current - 1
    }

    private fun TransferDemand.isMoreUrgentThan(other: TransferDemand): Boolean =
        DEMAND_ORDER.compare(this, other) < 0

    companion object {
        const val DEFAULT_MAX_ACTIVE_PER_DESTINATION = 2
        private const val MAX_FAILURE_PENALTY = 8
        private const val ROUTE_RETRY_COOLDOWN_NS = 1_500_000_000L
        private val DEMAND_ORDER =
            compareBy<TransferDemand> { it.priority.ordinal }
                .thenBy { it.neededByCoordinatorNs ?: Long.MAX_VALUE }
                .thenBy { it.requestedAtCoordinatorNs }
                .thenBy { it.trackId.value }
    }
}
