package com.darius.unison.room

import com.darius.unison.sync.ClockEstimate
import com.darius.unison.sync.ClockSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomRoleEnginesTest {
    private fun estimate(state: ClockSyncState) = ClockEstimate(
        offsetNs = 1_000,
        rate = 1.0001,
        rttNs = 5_000,
        rttVariationNs = 100,
        uncertaintyNs = 500,
        sampledAtLocalNs = 1_000_000,
        lastGoodSampleLocalNs = 1_000_000,
        sampleAgeNs = 0,
        acceptedSampleCount = 10,
        rejectedSampleCount = 0,
        state = state,
    )

    @Test
    fun coordinatorAlwaysUsesLocalMonotonicTime() {
        assertTrue(CoordinatorEngine.canApplyScheduledCommand(estimate(ClockSyncState.UNSYNCHRONIZED)))
        assertEquals(42L, CoordinatorEngine.coordinatorTimeNs(42L, estimate(ClockSyncState.STALE)))
    }

    @Test
    fun participantRequiresLockedClockForScheduledCommands() {
        assertFalse(ParticipantEngine.canApplyScheduledCommand(estimate(ClockSyncState.ACQUIRING)))
        assertNull(ParticipantEngine.coordinatorTimeNs(2_000_000L, estimate(ClockSyncState.STALE)))
        assertTrue(ParticipantEngine.canApplyScheduledCommand(estimate(ClockSyncState.LOCKED)))
        assertEquals(2_001_100L, ParticipantEngine.coordinatorTimeNs(2_000_000L, estimate(ClockSyncState.LOCKED)))
    }
}
