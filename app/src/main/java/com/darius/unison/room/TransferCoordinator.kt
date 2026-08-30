package com.darius.unison.room

import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferPriority
import com.darius.unison.transfer.TransferCapacityPolicy

/**
 * One destination's request for content, ranked by playback consequence rather than arrival order.
 */
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
    val retryAfterCoordinatorNs: Long = 0L,
)

/**
 * Coordinator-side owner of transfer demand, admission, active routes, and route backoff.
 * Authorization and byte transport remain effects executed by
 * [com.darius.unison.transfer.TransferManager]. All methods are called from the serialized room
 * actor, so transfer policy has one deterministic owner without adding another concurrency domain.
 */
internal class TransferCoordinator(
    private val capacity: TransferCapacityPolicy = TransferCapacityPolicy.DEFAULT
) {

    private val demands = mutableMapOf<Pair<TrackId, PeerId>, TransferDemand>()
    private val activeRoutes = mutableMapOf<Pair<TrackId, PeerId>, TransferRouteKey>()
    private val routeHealth = mutableMapOf<TransferRouteKey, TransferRouteHealth>()

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

    /** Removes future demand without disturbing an already admitted transfer. */
    fun removeDemand(trackId: TrackId, destinationPeerId: PeerId) {
        demands.remove(trackId to destinationPeerId)
    }

    /** Terminal success/non-retryable removal: demand and any admitted route are both finished. */
    fun finish(trackId: TrackId, destinationPeerId: PeerId) {
        demands.remove(trackId to destinationPeerId)
        activeRoutes.remove(trackId to destinationPeerId)
    }

    fun removePeer(peerId: PeerId) {
        demands.keys.removeAll { (_, destination) -> destination == peerId }
        activeRoutes.entries
            .filter { (_, route) ->
                route.sourcePeerId == peerId || route.destinationPeerId == peerId
            }
            .map { it.key }
            .forEach { key -> activeRoutes.remove(key) }
        routeHealth.keys.removeAll { it.sourcePeerId == peerId || it.destinationPeerId == peerId }
    }

    fun clear() {
        demands.clear()
        activeRoutes.clear()
        routeHealth.clear()
    }

    fun markActive(route: TransferRouteKey) {
        val key = route.trackId to route.destinationPeerId
        val prior = activeRoutes[key]
        if (prior == route) return
        require(prior == null) { "Track/destination already has an active transfer" }
        require(canAdmit(route)) { "Transfer route exceeds configured capacity" }
        activeRoutes[key] = route
    }

    fun markTerminal(trackId: TrackId, destinationPeerId: PeerId) {
        activeRoutes.remove(trackId to destinationPeerId)
    }

    /** Records one genuine route failure and returns the coordinator time when it may retry. */
    fun recordRouteFailure(route: TransferRouteKey, nowCoordinatorNs: Long): Long {
        val previous = routeHealth[route] ?: TransferRouteHealth()
        val failures = (previous.failures + 1).coerceAtMost(MAX_FAILURE_PENALTY)
        val cooldownNs =
            (BASE_ROUTE_RETRY_COOLDOWN_NS shl (failures - 1).coerceAtMost(3)).coerceAtMost(
                MAX_ROUTE_RETRY_COOLDOWN_NS
            )
        val retryAfter = nowCoordinatorNs + cooldownNs
        routeHealth[route] =
            previous.copy(
                failures = failures,
                retryAfterCoordinatorNs = retryAfter,
            )
        markTerminal(route.trackId, route.destinationPeerId)
        return retryAfter
    }

    fun activeCount(destinationPeerId: PeerId): Int =
        activeRoutes.values.count { it.destinationPeerId == destinationPeerId }

    fun activeSourceCount(sourcePeerId: PeerId): Int =
        activeRoutes.values.count { it.sourcePeerId == sourcePeerId }

    fun activePairCount(sourcePeerId: PeerId, destinationPeerId: PeerId): Int =
        activeRoutes.values.count {
            it.sourcePeerId == sourcePeerId && it.destinationPeerId == destinationPeerId
        }

    fun isActive(trackId: TrackId, destinationPeerId: PeerId): Boolean =
        (trackId to destinationPeerId) in activeRoutes

    fun canAdmit(route: TransferRouteKey): Boolean {
        if ((route.trackId to route.destinationPeerId) in activeRoutes) return false
        if (activeCount(route.destinationPeerId) >= capacity.maxInboundPerDestination) return false
        if (activeSourceCount(route.sourcePeerId) >= capacity.maxOutboundPerSource) return false
        if (
            activePairCount(route.sourcePeerId, route.destinationPeerId) >=
                capacity.maxPerSourceDestinationPair
        )
            return false
        return true
    }

    fun pendingDestinations(): Set<PeerId> =
        demands.values.mapTo(linkedSetOf()) { it.destinationPeerId }

    fun pendingDemands(destinationPeerId: PeerId): List<TransferDemand> {
        if (activeCount(destinationPeerId) >= capacity.maxInboundPerDestination) return emptyList()
        return demands.values
            .asSequence()
            .filter { it.destinationPeerId == destinationPeerId }
            .filter { (it.trackId to destinationPeerId) !in activeRoutes }
            .sortedWith(DEMAND_ORDER)
            .toList()
    }

    fun nextDemand(destinationPeerId: PeerId): TransferDemand? =
        pendingDemands(destinationPeerId).firstOrNull()

    fun chooseSource(
        demand: TransferDemand,
        availableSources: Set<PeerId>,
        nowCoordinatorNs: Long,
        isUsable: (PeerId) -> Boolean,
    ): PeerId? =
        availableSources
            .asSequence()
            .filter { it != demand.destinationPeerId && isUsable(it) }
            .filter { source ->
                canAdmit(TransferRouteKey(demand.trackId, source, demand.destinationPeerId))
            }
            .filter { source ->
                val health =
                    routeHealth[TransferRouteKey(demand.trackId, source, demand.destinationPeerId)]
                health == null || nowCoordinatorNs >= health.retryAfterCoordinatorNs
            }
            .minWithOrNull(
                compareBy<PeerId> { source ->
                        routeHealth[
                                TransferRouteKey(demand.trackId, source, demand.destinationPeerId)]
                            ?.failures ?: 0
                    }
                    .thenBy { activeSourceCount(it) }
                    .thenBy { it.value }
            )

    fun demandFor(trackId: TrackId, destinationPeerId: PeerId): TransferDemand? =
        demands[trackId to destinationPeerId]

    private fun TransferDemand.isMoreUrgentThan(other: TransferDemand): Boolean =
        DEMAND_ORDER.compare(this, other) < 0

    companion object {
        private const val MAX_FAILURE_PENALTY = 8
        private const val BASE_ROUTE_RETRY_COOLDOWN_NS = 500_000_000L
        private const val MAX_ROUTE_RETRY_COOLDOWN_NS = 4_000_000_000L
        private val DEMAND_ORDER =
            compareBy<TransferDemand> { it.priority.ordinal }
                .thenBy { it.neededByCoordinatorNs ?: Long.MAX_VALUE }
                .thenBy { it.requestedAtCoordinatorNs }
                .thenBy { it.trackId.value }
    }
}
