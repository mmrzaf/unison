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

/**
 * Connectivity health deliberately excludes track identity: a broken phone-to-phone route is not
 * healed by assigning another song over the same source/destination pair.
 */
internal data class TransferPeerRouteKey(
    val sourcePeerId: PeerId,
    val destinationPeerId: PeerId,
)

internal data class TransferRouteHealth(
    val failures: Int = 0,
    val retryAfterCoordinatorNs: Long = 0L,
    val suspended: Boolean = false,
)

internal data class TransferRouteFailureDecision(
    val failures: Int,
    val retryAtCoordinatorNs: Long?,
    val suspended: Boolean,
)

/**
 * Coordinator-side owner of transfer demand, admission, active routes, and route health.
 * Authorization and byte transport remain effects executed by
 * [com.darius.unison.transfer.TransferManager]. All methods are called from the serialized room
 * actor, so transfer policy has one deterministic owner without adding another concurrency domain.
 */
internal class TransferCoordinator(
    private val capacity: TransferCapacityPolicy = TransferCapacityPolicy.DEFAULT
) {

    private val demands = mutableMapOf<Pair<TrackId, PeerId>, TransferDemand>()
    private val activeRoutes = mutableMapOf<Pair<TrackId, PeerId>, TransferRouteKey>()
    private val routeHealth = mutableMapOf<TransferPeerRouteKey, TransferRouteHealth>()

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

    /**
     * Terminal success/non-retryable removal. If this finishes an admitted transfer, successful
     * delivery proves the source/destination route healthy again and clears its prior penalty.
     */
    fun finish(trackId: TrackId, destinationPeerId: PeerId) {
        demands.remove(trackId to destinationPeerId)
        activeRoutes.remove(trackId to destinationPeerId)?.let(::recordRouteSuccess)
    }

    fun removePeer(peerId: PeerId) {
        demands.keys.removeAll { (_, destination) -> destination == peerId }
        activeRoutes.entries
            .filter { (_, route) ->
                route.sourcePeerId == peerId || route.destinationPeerId == peerId
            }
            .map { it.key }
            .forEach { key -> activeRoutes.remove(key) }
        clearRouteHealthForPeer(peerId)
    }

    fun clear() {
        demands.clear()
        activeRoutes.clear()
        routeHealth.clear()
    }

    fun clearRouteHealth() {
        routeHealth.clear()
    }

    /** A peer endpoint/network transition is an explicit reason to reconsider suspended routes. */
    fun clearRouteHealthForPeer(peerId: PeerId) {
        routeHealth.keys.removeAll {
            it.sourcePeerId == peerId || it.destinationPeerId == peerId
        }
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

    /**
     * Records a phone-to-phone connectivity failure and releases the current assignment.
     *
     * Repeated transient failures use bounded exponential backoff and then suspend the pair after
     * [MAX_CONSECUTIVE_ROUTE_FAILURES]. A deterministic failure can suspend immediately. A
     * suspension is intentionally open-ended: peer/network change or explicit user preparation
     * retry clears route health and re-enters source selection.
     */
    fun recordRouteFailure(
        route: TransferRouteKey,
        nowCoordinatorNs: Long,
        suspendImmediately: Boolean = false,
    ): TransferRouteFailureDecision {
        val peerRoute = route.peerRoute()
        val previous = routeHealth[peerRoute] ?: TransferRouteHealth()
        val failures = (previous.failures + 1).coerceAtMost(MAX_CONSECUTIVE_ROUTE_FAILURES)
        val suspended =
            suspendImmediately || previous.suspended || failures >= MAX_CONSECUTIVE_ROUTE_FAILURES
        val retryAt =
            if (suspended) {
                null
            } else {
                val cooldownNs =
                    (BASE_ROUTE_RETRY_COOLDOWN_NS shl (failures - 1).coerceAtMost(3)).coerceAtMost(
                        MAX_ROUTE_RETRY_COOLDOWN_NS
                    )
                nowCoordinatorNs + cooldownNs
            }
        routeHealth[peerRoute] =
            TransferRouteHealth(
                failures = failures,
                retryAfterCoordinatorNs = retryAt ?: Long.MAX_VALUE,
                suspended = suspended,
            )
        markTerminal(route.trackId, route.destinationPeerId)
        return TransferRouteFailureDecision(
            failures = failures,
            retryAtCoordinatorNs = retryAt,
            suspended = suspended,
        )
    }

    private fun recordRouteSuccess(route: TransferRouteKey) {
        routeHealth.remove(route.peerRoute())
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
                val health = routeHealth[TransferPeerRouteKey(source, demand.destinationPeerId)]
                health == null ||
                    (!health.suspended && nowCoordinatorNs >= health.retryAfterCoordinatorNs)
            }
            .minWithOrNull(
                compareBy<PeerId> { source ->
                        routeHealth[TransferPeerRouteKey(source, demand.destinationPeerId)]
                            ?.failures ?: 0
                    }
                    .thenBy { activeSourceCount(it) }
                    .thenBy { it.value }
            )

    fun demandFor(trackId: TrackId, destinationPeerId: PeerId): TransferDemand? =
        demands[trackId to destinationPeerId]

    internal fun routeHealthFor(
        sourcePeerId: PeerId,
        destinationPeerId: PeerId,
    ): TransferRouteHealth? = routeHealth[TransferPeerRouteKey(sourcePeerId, destinationPeerId)]

    private fun TransferRouteKey.peerRoute(): TransferPeerRouteKey =
        TransferPeerRouteKey(sourcePeerId, destinationPeerId)

    private fun TransferDemand.isMoreUrgentThan(other: TransferDemand): Boolean =
        DEMAND_ORDER.compare(this, other) < 0

    companion object {
        internal const val MAX_CONSECUTIVE_ROUTE_FAILURES = 5
        private const val BASE_ROUTE_RETRY_COOLDOWN_NS = 500_000_000L
        private const val MAX_ROUTE_RETRY_COOLDOWN_NS = 4_000_000_000L
        private val DEMAND_ORDER =
            compareBy<TransferDemand> { it.priority.ordinal }
                .thenBy { it.neededByCoordinatorNs ?: Long.MAX_VALUE }
                .thenBy { it.requestedAtCoordinatorNs }
                .thenBy { it.trackId.value }
    }
}
