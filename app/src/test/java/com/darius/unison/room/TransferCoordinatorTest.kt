package com.darius.unison.room

import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferPriority
import com.darius.unison.transfer.TransferCapacityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferCoordinatorTest {
    private val sourceA = PeerId("source-a")
    private val sourceB = PeerId("source-b")
    private val destination = PeerId("destination")

    @Test
    fun urgentDemandRanksAheadOfEarlierBackgroundDemand() {
        val coordinator = TransferCoordinator()
        val background = demand(1, destination, TransferPriority.PLAYBACK_RUNWAY, 1L)
        val urgent = demand(2, destination, TransferPriority.USER_SELECTED, 2L)
        coordinator.upsert(background)
        coordinator.upsert(urgent)

        assertEquals(urgent.trackId, coordinator.nextDemand(destination)?.trackId)
    }

    @Test
    fun sameSourceDestinationPairIsSerializedBeforeSocketAssignment() {
        val coordinator = TransferCoordinator()
        val first = demand(1, destination, TransferPriority.NEXT_BOUNDARY, 1L)
        val second = demand(2, destination, TransferPriority.PLAYBACK_RUNWAY, 2L)
        coordinator.upsert(first)
        coordinator.upsert(second)
        coordinator.markActive(TransferRouteKey(first.trackId, sourceA, destination))

        assertNull(
            coordinator.chooseSource(
                second,
                availableSources = setOf(sourceA),
                nowCoordinatorNs = 10L,
                isUsable = { true },
            )
        )
        assertEquals(1, coordinator.activePairCount(sourceA, destination))
    }

    @Test
    fun destinationCanReceiveFromTwoIndependentSources() {
        val coordinator = TransferCoordinator()
        val first = demand(1, destination, TransferPriority.NEXT_BOUNDARY, 1L)
        val second = demand(2, destination, TransferPriority.PLAYBACK_RUNWAY, 2L)
        coordinator.upsert(first)
        coordinator.upsert(second)
        coordinator.markActive(TransferRouteKey(first.trackId, sourceA, destination))

        assertEquals(
            sourceB,
            coordinator.chooseSource(
                second,
                availableSources = setOf(sourceA, sourceB),
                nowCoordinatorNs = 10L,
                isUsable = { true },
            ),
        )
    }

    @Test
    fun sourceOutboundCapacityIsAdmissionConstraintNotJustRankingHint() {
        val capacity =
            TransferCapacityPolicy(
                maxInboundPerDestination = 2,
                maxOutboundPerSource = 2,
                maxPerSourceDestinationPair = 1,
            )
        val coordinator = TransferCoordinator(capacity)
        val destinationB = PeerId("destination-b")
        val destinationC = PeerId("destination-c")
        coordinator.markActive(TransferRouteKey(track(1), sourceA, destination))
        coordinator.markActive(TransferRouteKey(track(2), sourceA, destinationB))
        val waiting = demand(3, destinationC, TransferPriority.BACKGROUND, 3L)
        coordinator.upsert(waiting)

        assertNull(
            coordinator.chooseSource(
                waiting,
                availableSources = setOf(sourceA),
                nowCoordinatorNs = 10L,
                isUsable = { true },
            )
        )
    }

    @Test
    fun obsoleteDemandDoesNotDestroyAlreadyAdmittedWork() {
        val coordinator = TransferCoordinator()
        val demand = demand(1, destination, TransferPriority.BACKGROUND, 1L)
        val route = TransferRouteKey(demand.trackId, sourceA, destination)
        coordinator.upsert(demand)
        coordinator.markActive(route)

        coordinator.removeDemand(demand.trackId, destination)

        assertNull(coordinator.demandFor(demand.trackId, destination))
        assertTrue(coordinator.isActive(demand.trackId, destination))
        coordinator.markTerminal(demand.trackId, destination)
        assertFalse(coordinator.isActive(demand.trackId, destination))
    }

    @Test
    fun routeFailureBacksOffAndHealthyAlternateSourceCanProceedImmediately() {
        val coordinator = TransferCoordinator()
        val demand = demand(1, destination, TransferPriority.NEXT_BOUNDARY, 1L)
        coordinator.upsert(demand)
        val failedRoute = TransferRouteKey(demand.trackId, sourceA, destination)
        val decision =
            coordinator.recordRouteFailure(failedRoute, nowCoordinatorNs = 1_000_000_000L)

        assertEquals(1_500_000_000L, decision.retryAtCoordinatorNs)
        assertFalse(decision.suspended)
        assertEquals(
            sourceB,
            coordinator.chooseSource(
                demand,
                availableSources = setOf(sourceA, sourceB),
                nowCoordinatorNs = 1_100_000_000L,
                isUsable = { true },
            ),
        )
        assertEquals(
            sourceA,
            coordinator.chooseSource(
                demand,
                availableSources = setOf(sourceA),
                nowCoordinatorNs = decision.retryAtCoordinatorNs!!,
                isUsable = { true },
            ),
        )
    }

    @Test
    fun routeHealthIsSharedAcrossTracksForTheSamePeerPair() {
        val coordinator = TransferCoordinator()
        val first = demand(1, destination, TransferPriority.NEXT_BOUNDARY, 1L)
        val second = demand(2, destination, TransferPriority.PLAYBACK_RUNWAY, 2L)
        coordinator.upsert(first)
        coordinator.upsert(second)
        val decision =
            coordinator.recordRouteFailure(
                TransferRouteKey(first.trackId, sourceA, destination),
                nowCoordinatorNs = 1_000_000_000L,
            )

        assertNull(
            coordinator.chooseSource(
                second,
                availableSources = setOf(sourceA),
                nowCoordinatorNs = 1_100_000_000L,
                isUsable = { true },
            )
        )
        assertEquals(
            sourceA,
            coordinator.chooseSource(
                second,
                availableSources = setOf(sourceA),
                nowCoordinatorNs = decision.retryAtCoordinatorNs!!,
                isUsable = { true },
            ),
        )
    }

    @Test
    fun repeatedRouteFailuresSuspendPairAfterFiveAttempts() {
        val coordinator = TransferCoordinator()
        var now = 0L
        var decision: TransferRouteFailureDecision? = null

        repeat(TransferCoordinator.MAX_CONSECUTIVE_ROUTE_FAILURES) { index ->
            decision =
                coordinator.recordRouteFailure(
                    TransferRouteKey(track(index + 1), sourceA, destination),
                    now,
                )
            now = decision!!.retryAtCoordinatorNs ?: now
        }

        assertEquals(TransferCoordinator.MAX_CONSECUTIVE_ROUTE_FAILURES, decision!!.failures)
        assertTrue(decision!!.suspended)
        assertNull(decision!!.retryAtCoordinatorNs)
        assertTrue(coordinator.routeHealthFor(sourceA, destination)!!.suspended)
        assertNull(
            coordinator.chooseSource(
                demand(9, destination, TransferPriority.USER_SELECTED, now),
                availableSources = setOf(sourceA),
                nowCoordinatorNs = Long.MAX_VALUE,
                isUsable = { true },
            )
        )
    }

    @Test
    fun deterministicFailureSuspendsPairImmediately() {
        val coordinator = TransferCoordinator()
        val route = TransferRouteKey(track(1), sourceA, destination)

        val decision = coordinator.recordRouteFailure(route, 0L, suspendImmediately = true)

        assertEquals(1, decision.failures)
        assertTrue(decision.suspended)
        assertNull(decision.retryAtCoordinatorNs)
    }

    @Test
    fun peerNetworkChangeClearsSuspendedPair() {
        val coordinator = TransferCoordinator()
        val demand = demand(1, destination, TransferPriority.USER_SELECTED, 1L)
        coordinator.upsert(demand)
        coordinator.recordRouteFailure(
            TransferRouteKey(demand.trackId, sourceA, destination),
            0L,
            suspendImmediately = true,
        )
        assertNull(
            coordinator.chooseSource(
                demand,
                setOf(sourceA),
                Long.MAX_VALUE,
                isUsable = { true },
            )
        )

        coordinator.clearRouteHealthForPeer(destination)

        assertEquals(
            sourceA,
            coordinator.chooseSource(
                demand,
                setOf(sourceA),
                Long.MAX_VALUE,
                isUsable = { true },
            ),
        )
    }

    @Test
    fun successfulTransferClearsPriorPairPenalty() {
        val coordinator = TransferCoordinator()
        val demand = demand(1, destination, TransferPriority.NEXT_BOUNDARY, 1L)
        coordinator.upsert(demand)
        coordinator.recordRouteFailure(
            TransferRouteKey(demand.trackId, sourceA, destination),
            0L,
        )
        val retryAt = coordinator.routeHealthFor(sourceA, destination)!!.retryAfterCoordinatorNs
        coordinator.markActive(TransferRouteKey(demand.trackId, sourceA, destination))

        coordinator.finish(demand.trackId, destination)

        assertNull(coordinator.routeHealthFor(sourceA, destination))
        val next = demand(2, destination, TransferPriority.NEXT_BOUNDARY, retryAt)
        coordinator.upsert(next)
        assertEquals(
            sourceA,
            coordinator.chooseSource(next, setOf(sourceA), 0L, isUsable = { true }),
        )
    }

    @Test
    fun repeatedRouteFailuresIncreaseBackoffUntilCircuitBreaker() {
        val coordinator = TransferCoordinator()
        var now = 0L
        val deltas = mutableListOf<Long>()

        repeat(TransferCoordinator.MAX_CONSECUTIVE_ROUTE_FAILURES - 1) { index ->
            val decision =
                coordinator.recordRouteFailure(
                    TransferRouteKey(track(index + 1), sourceA, destination),
                    now,
                )
            val retryAt = decision.retryAtCoordinatorNs!!
            deltas += retryAt - now
            now = retryAt
        }

        assertEquals(
            listOf(500_000_000L, 1_000_000_000L, 2_000_000_000L, 4_000_000_000L),
            deltas,
        )
    }

    private fun demand(
        index: Int,
        destinationPeerId: PeerId,
        priority: TransferPriority,
        requestedAt: Long,
    ) = TransferDemand(track(index), destinationPeerId, priority, null, requestedAt)

    private fun track(index: Int) = TrackId(index.toString(16).padStart(64, '0'))
}
