package com.darius.unison.room

import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.PeerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerPlaybackHealthRegistryTest {
    private val coordinator = PeerId("coordinator-123456")
    private val guest = PeerId("guest-123456789012")

    @Test
    fun unknownPeerWarmsUpAndFreshClockLeaseMakesItReady() {
        val registry = PeerPlaybackHealthRegistry(readyLeaseNs = 10_000L)
        assertEquals(PeerPlaybackHealthState.WARMING_UP, registry.health(guest, 0L).state)

        assertFalse(registry.updateClock(guest, true, 20L, 5L, nowNs = 100L))
        assertFalse(registry.updateParticipation(guest, LocalPlaybackParticipation.ACTIVE, 100L))
        assertEquals(PeerPlaybackHealthState.CATCHING_UP, registry.health(guest, 100L).state)
        assertTrue(registry.updateContentReady(guest, true, 150L))
        assertFalse(registry.updateClock(guest, true, 25L, 6L, nowNs = 200L))

        assertEquals(PeerPlaybackHealthState.READY, registry.health(guest, 500L).state)
        assertEquals(25L, registry.health(guest, 500L).roundTripNs)
    }

    @Test
    fun explicitClockLossImmediatelyDegradesPreviouslyReadyPeer() {
        val registry = PeerPlaybackHealthRegistry(readyLeaseNs = 10_000L)
        registry.updateClock(guest, true, 20L, 5L, nowNs = 100L)
        registry.updateParticipation(guest, LocalPlaybackParticipation.ACTIVE, 100L)
        registry.updateContentReady(guest, true, 100L)
        registry.updateClock(guest, false, null, null, nowNs = 200L)

        assertEquals(PeerPlaybackHealthState.DEGRADED, registry.health(guest, 200L).state)
        assertEquals(null, registry.health(guest, 200L).roundTripNs)
    }

    @Test
    fun expiredLeaseStopsPeerInfluencingPlaybackCohort() {
        val registry = PeerPlaybackHealthRegistry(readyLeaseNs = 1_000L)
        registry.updateClock(guest, true, 20L, 5L, nowNs = 100L)
        registry.updateParticipation(guest, LocalPlaybackParticipation.ACTIVE, 100L)
        registry.updateContentReady(guest, true, 100L)
        val connected = setOf(coordinator, guest)

        assertTrue(
            guest in
                registry.readyPeers(
                    connected,
                    coordinator,
                    LocalPlaybackParticipation.ACTIVE,
                    nowNs = 1_000L,
                )
        )
        assertTrue(registry.expireReadyLeases(nowNs = 1_101L))
        assertFalse(
            guest in
                registry.readyPeers(
                    connected,
                    coordinator,
                    LocalPlaybackParticipation.ACTIVE,
                    nowNs = 1_101L,
                )
        )
        assertTrue(
            coordinator in
                registry.readyPeers(
                    connected,
                    coordinator,
                    LocalPlaybackParticipation.ACTIVE,
                    nowNs = 1_101L,
                )
        )
    }

    @Test
    fun warmingGuestNeverDelaysHealthyCoordinator() {
        val registry = PeerPlaybackHealthRegistry(readyLeaseNs = 35_000_000_000L)
        val connected = setOf(coordinator, guest)

        assertEquals(
            setOf(coordinator),
            registry.readyPeers(
                connected,
                coordinator,
                LocalPlaybackParticipation.ACTIVE,
                nowNs = 0L,
            ),
        )
    }

    @Test
    fun inhibitedPeerLeavesReadyCohortWithoutLosingClockLease() {
        val registry = PeerPlaybackHealthRegistry(readyLeaseNs = 10_000L)
        registry.updateClock(guest, true, 20L, 5L, nowNs = 100L)
        registry.updateParticipation(guest, LocalPlaybackParticipation.ACTIVE, 100L)
        registry.updateContentReady(guest, true, 100L)
        assertEquals(PeerPlaybackHealthState.READY, registry.health(guest, 200L).state)

        assertTrue(
            registry.updateParticipation(
                guest,
                LocalPlaybackParticipation.OUTPUT_INHIBITED,
                nowNs = 250L,
            )
        )
        assertEquals(PeerPlaybackHealthState.DEGRADED, registry.health(guest, 250L).state)

        assertTrue(registry.updateParticipation(guest, LocalPlaybackParticipation.ACTIVE, 300L))
        assertEquals(PeerPlaybackHealthState.READY, registry.health(guest, 300L).state)
    }

    @Test
    fun synchronizedGuestDoesNotJoinBlockingCohortUntilContentRunwayIsReady() {
        val registry = PeerPlaybackHealthRegistry(readyLeaseNs = 10_000L)
        val connected = setOf(coordinator, guest)
        registry.updateClock(guest, true, 20L, 5L, 100L)
        registry.updateParticipation(guest, LocalPlaybackParticipation.ACTIVE, 100L)

        assertEquals(PeerPlaybackHealthState.CATCHING_UP, registry.health(guest, 200L).state)
        assertEquals(
            setOf(coordinator),
            registry.readyPeers(connected, coordinator, LocalPlaybackParticipation.ACTIVE, 200L),
        )

        assertTrue(registry.updateContentReady(guest, true, 250L))
        assertEquals(
            setOf(coordinator, guest),
            registry.readyPeers(connected, coordinator, LocalPlaybackParticipation.ACTIVE, 250L),
        )
    }

    @Test
    fun catchingUpPeerStillCountsAsActiveSynchronizationParticipant() {
        val registry = PeerPlaybackHealthRegistry(readyLeaseNs = 10_000L)
        registry.updateClock(guest, true, 20L, 5L, 100L)
        registry.updateParticipation(guest, LocalPlaybackParticipation.ACTIVE, 100L)

        assertEquals(PeerPlaybackHealthState.CATCHING_UP, registry.health(guest, 200L).state)
        assertTrue(registry.isSynchronizationParticipant(guest, 200L))

        registry.updateParticipation(guest, LocalPlaybackParticipation.OUTPUT_INHIBITED, 250L)
        assertFalse(registry.isSynchronizationParticipant(guest, 250L))
    }

    @Test
    fun clockReadinessIsIndependentFromPlaybackAdmission() {
        val registry = PeerPlaybackHealthRegistry(readyLeaseNs = 10_000L)
        registry.updateParticipation(guest, LocalPlaybackParticipation.ACTIVE, 100L)

        assertFalse(registry.isClockReady(guest, 100L))
        registry.updateClock(guest, true, 20L, 5L, 150L)

        assertTrue(registry.isClockReady(guest, 150L))
        assertEquals(PeerPlaybackHealthState.CATCHING_UP, registry.health(guest, 150L).state)
    }

    @Test
    fun contentReadinessKeepsVerifiedPeerWhileAudibleParticipationIsInhibited() {
        val registry = PeerPlaybackHealthRegistry(readyLeaseNs = 10_000L)
        val connected = setOf(coordinator, guest)
        registry.updateClock(guest, true, 20L, 5L, nowNs = 100L)
        registry.updateParticipation(guest, LocalPlaybackParticipation.ACTIVE, 100L)
        registry.updateContentReady(guest, true, 100L)

        registry.updateParticipation(guest, LocalPlaybackParticipation.OUTPUT_INHIBITED, 200L)

        assertEquals(
            emptySet<PeerId>(),
            registry.readyPeers(
                connected,
                coordinator,
                LocalPlaybackParticipation.OUTPUT_INHIBITED,
                200L,
            ),
        )
        assertEquals(
            setOf(coordinator, guest),
            registry.contentReadinessPeers(connected, coordinator, 200L),
        )
    }

    @Test
    fun inhibitedCoordinatorStillAnchorsContentReadiness() {
        val registry = PeerPlaybackHealthRegistry(readyLeaseNs = 10_000L)
        val connected = setOf(coordinator, guest)

        assertEquals(
            setOf(coordinator),
            registry.contentReadinessPeers(connected, coordinator, nowNs = 0L),
        )
        assertEquals(
            emptySet<PeerId>(),
            registry.readyPeers(
                connected,
                coordinator,
                LocalPlaybackParticipation.OUTPUT_INHIBITED,
                nowNs = 0L,
            ),
        )
    }
}
