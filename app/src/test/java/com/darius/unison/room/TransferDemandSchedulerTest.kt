package com.darius.unison.room

import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferDemandSchedulerTest {
    private val sourceA = PeerId("source-a")
    private val sourceB = PeerId("source-b")
    private val destination = PeerId("destination")

    @Test
    fun urgentDemandRanksAheadOfEarlierBackgroundDemand() {
        val scheduler = TransferDemandScheduler(maxActivePerDestination = 2)
        val background = demand(1, TransferPriority.PLAYBACK_RUNWAY, requestedAt = 1L)
        val urgent = demand(2, TransferPriority.USER_SELECTED, requestedAt = 2L)
        scheduler.upsert(background)
        scheduler.upsert(urgent)

        assertEquals(urgent.trackId, scheduler.nextDemand(destination)?.trackId)
    }

    @Test
    fun laterProjectionCanDowngradeOldUserSelection() {
        val scheduler = TransferDemandScheduler(maxActivePerDestination = 2)
        val track = track(1)
        scheduler.upsert(
            TransferDemand(track, destination, TransferPriority.USER_SELECTED, null, 1L)
        )
        scheduler.upsert(
            TransferDemand(track, destination, TransferPriority.PLAYBACK_RUNWAY, null, 2L)
        )

        assertEquals(
            TransferPriority.PLAYBACK_RUNWAY,
            scheduler.demandFor(track, destination)?.priority,
        )
    }

    @Test
    fun urgentDemandPreemptsWorstActiveRouteWhenSlotsAreFull() {
        val scheduler = TransferDemandScheduler(maxActivePerDestination = 2)
        val backgroundA = demand(1, TransferPriority.BACKGROUND, 1L)
        val backgroundB = demand(2, TransferPriority.PLAYBACK_RUNWAY, 2L)
        scheduler.upsert(backgroundA)
        scheduler.upsert(backgroundB)
        scheduler.markActive(TransferRouteKey(backgroundA.trackId, sourceA, destination))
        scheduler.markActive(TransferRouteKey(backgroundB.trackId, sourceB, destination))

        val urgent = demand(3, TransferPriority.CURRENT_REQUIRED, 3L)
        scheduler.upsert(urgent)
        val preempt = scheduler.preemptionCandidate(urgent)

        assertEquals(backgroundA.trackId, preempt?.trackId)
    }

    @Test
    fun routeFailureDoesNotRemoveSourceAndBiasesSelectionToHealthySource() {
        val scheduler = TransferDemandScheduler(maxActivePerDestination = 2)
        val demand = demand(1, TransferPriority.NEXT_BOUNDARY, 1L)
        scheduler.upsert(demand)
        val failedRoute = TransferRouteKey(demand.trackId, sourceA, destination)
        scheduler.recordRouteFailure(failedRoute, nowCoordinatorNs = 10L)

        assertEquals(
            sourceB,
            scheduler.chooseSource(
                demand,
                availableSources = setOf(sourceA, sourceB),
                nowCoordinatorNs = 2_000_000_000L,
                isUsable = { true },
            ),
        )
    }

    @Test
    fun recentlyFailedRouteCoolsDownInsteadOfHotLooping() {
        val scheduler = TransferDemandScheduler(maxActivePerDestination = 2)
        val demand = demand(1, TransferPriority.NEXT_BOUNDARY, 1L)
        scheduler.upsert(demand)
        scheduler.recordRouteFailure(
            TransferRouteKey(demand.trackId, sourceA, destination),
            nowCoordinatorNs = 1_000_000_000L,
        )

        assertNull(
            scheduler.chooseSource(
                demand,
                availableSources = setOf(sourceA),
                nowCoordinatorNs = 1_100_000_000L,
                isUsable = { true },
            )
        )
        assertEquals(
            sourceA,
            scheduler.chooseSource(
                demand,
                availableSources = setOf(sourceA),
                nowCoordinatorNs = 2_600_000_000L,
                isUsable = { true },
            ),
        )
    }

    @Test
    fun activeSlotBoundStopsFurtherAssignment() {
        val scheduler = TransferDemandScheduler(maxActivePerDestination = 1)
        val first = demand(1, TransferPriority.NEXT_BOUNDARY, 1L)
        val second = demand(2, TransferPriority.PLAYBACK_RUNWAY, 2L)
        scheduler.upsert(first)
        scheduler.upsert(second)
        scheduler.markActive(TransferRouteKey(first.trackId, sourceA, destination))

        assertTrue(scheduler.pendingDemands(destination).isEmpty())
        assertNull(scheduler.nextDemand(destination))
    }

    private fun demand(index: Int, priority: TransferPriority, requestedAt: Long) =
        TransferDemand(track(index), destination, priority, null, requestedAt)

    private fun track(index: Int) = TrackId(index.toString(16).padStart(64, '0'))
}
