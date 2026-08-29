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
        val retryAt = coordinator.recordRouteFailure(failedRoute, nowCoordinatorNs = 1_000_000_000L)

        assertEquals(1_500_000_000L, retryAt)
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
                nowCoordinatorNs = retryAt,
                isUsable = { true },
            ),
        )
    }

    @Test
    fun repeatedRouteFailuresIncreaseBackoffUpToBound() {
        val coordinator = TransferCoordinator()
        val route = TransferRouteKey(track(1), sourceA, destination)

        val first = coordinator.recordRouteFailure(route, 0L)
        val second = coordinator.recordRouteFailure(route, first)
        val third = coordinator.recordRouteFailure(route, second)
        val fourth = coordinator.recordRouteFailure(route, third)
        val fifth = coordinator.recordRouteFailure(route, fourth)

        assertEquals(500_000_000L, first)
        assertEquals(1_000_000_000L, second - first)
        assertEquals(2_000_000_000L, third - second)
        assertEquals(4_000_000_000L, fourth - third)
        assertEquals(4_000_000_000L, fifth - fourth)
    }

    private fun demand(
        index: Int,
        destinationPeerId: PeerId,
        priority: TransferPriority,
        requestedAt: Long,
    ) = TransferDemand(track(index), destinationPeerId, priority, null, requestedAt)

    private fun track(index: Int) = TrackId(index.toString(16).padStart(64, '0'))
}
