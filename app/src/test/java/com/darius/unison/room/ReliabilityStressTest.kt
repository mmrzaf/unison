package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.RoomMediaReadiness
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferPriority
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.transfer.TransferCapacityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * High-count deterministic abuse of the small state machines that guard Unison's critical paths.
 * This deliberately uses the normal JVM test surface rather than introducing a simulation system.
 */
class ReliabilityStressTest {
    @Test
    fun transferCoordinatorMaintainsCapacityAndConvergesUnderTwentyThousandMutations() {
        val capacity = TransferCapacityPolicy.DEFAULT
        val coordinator = TransferCoordinator(capacity)
        val sources = List(4) { PeerId("source-$it") }
        val destinations = List(3) { PeerId("destination-$it") }
        val active = linkedMapOf<Pair<TrackId, PeerId>, TransferRouteKey>()
        var state = 0x13579BDFL

        fun nextInt(bound: Int): Int {
            state = (state * 1_103_515_245L + 12_345L) and 0x7fff_ffffL
            return (state % bound).toInt()
        }

        repeat(20_000) { step ->
            val destination = destinations[nextInt(destinations.size)]
            val track = track(nextInt(48))
            val key = track to destination
            when (nextInt(5)) {
                0, 1 -> {
                    coordinator.upsert(
                        TransferDemand(
                            trackId = track,
                            destinationPeerId = destination,
                            priority = TransferPriority.entries[nextInt(TransferPriority.entries.size)],
                            neededByCoordinatorNs = null,
                            requestedAtCoordinatorNs = step.toLong(),
                        )
                    )
                    val demand = coordinator.demandFor(track, destination)
                    if (demand != null && key !in active) {
                        val source =
                            coordinator.chooseSource(
                                demand = demand,
                                availableSources = sources.toSet(),
                                nowCoordinatorNs = step.toLong() * 1_000_000_000L,
                                isUsable = { true },
                            )
                        if (source != null) {
                            val route = TransferRouteKey(track, source, destination)
                            coordinator.markActive(route)
                            active[key] = route
                        }
                    }
                }
                2 -> {
                    active.remove(key)?.let {
                        coordinator.markTerminal(track, destination)
                    }
                }
                3 -> {
                    active.remove(key)?.let { route ->
                        coordinator.recordRouteFailure(route, step.toLong() * 1_000_000_000L)
                    }
                }
                else -> coordinator.removeDemand(track, destination)
            }

            destinations.forEach { peer ->
                assertTrue(coordinator.activeCount(peer) <= capacity.maxInboundPerDestination)
            }
            sources.forEach { peer ->
                assertTrue(coordinator.activeSourceCount(peer) <= capacity.maxOutboundPerSource)
                destinations.forEach { destinationPeer ->
                    assertTrue(
                        coordinator.activePairCount(peer, destinationPeer) <=
                            capacity.maxPerSourceDestinationPair
                    )
                }
            }
        }

        active.keys.toList().forEach { (trackId, destinationPeerId) ->
            coordinator.markTerminal(trackId, destinationPeerId)
        }
        active.clear()
        coordinator.clear()
        destinations.forEach { assertEquals(0, coordinator.activeCount(it)) }
        sources.forEach { assertEquals(0, coordinator.activeSourceCount(it)) }
    }

    @Test
    fun repeatedPrepareAndUnavailablePlaybackReconciliationStayIdempotentAndQuiet() {
        val coordinatorPeer = PeerId("coordinator")
        val listener = PeerId("listener")
        val descriptor = TrackDescriptor(track(1), 2_000_000, "audio/mpeg", 180_000)
        val item = QueueItem.create(descriptor, coordinatorPeer, 1)
        val snapshot =
            RoomSnapshot(
                roomId = "room",
                roomName = "Room",
                term = CoordinatorTerm(1, coordinatorPeer),
                sequence = 8,
                members =
                    listOf(
                        MemberSnapshot(coordinatorPeer, "Coordinator"),
                        MemberSnapshot(listener, "Listener"),
                    ),
                queue = listOf(item),
                playback =
                    CanonicalPlaybackState(
                        queueItemId = item.queueItemId,
                        positionAtTimestampMs = 10_000,
                        coordinatorTimestampNs = 1_000_000_000L,
                        isPlaying = true,
                        revision = 8,
                    ),
                queueRevision = 4,
            )
        val report =
            ProtocolBody.PlaybackStatusReport(
                queueItemId = null,
                positionMs = 0,
                isPlaying = false,
                participation = LocalPlaybackParticipation.ACTIVE,
                driftMs = null,
                playbackRevision = 7,
                queueRevision = 4,
                canonicalSequence = 8,
            )
        val convergence = PlaybackConvergencePolicy(minimumRepairIntervalNs = 0L)

        repeat(10_000) {
            val readiness =
                RoomMediaReadinessPolicy.derive(
                    snapshot = snapshot,
                    roomReadyQueueItemIds = emptySet(),
                    explicitPreparationQueueItemIds = setOf(item.queueItemId),
                    locallyAvailableTrackIds = emptySet(),
                )
            assertEquals(RoomMediaReadiness.PREPARING, readiness[item.queueItemId])
            assertEquals(
                PlaybackConvergencePolicy.Action.None,
                convergence.decide(
                    peerId = listener,
                    snapshot = snapshot,
                    report = report,
                    coordinatorNowNs = 2_000_000_000L + it,
                    playbackExecutable = false,
                ),
            )
        }

        val ready =
            RoomMediaReadinessPolicy.derive(
                snapshot = snapshot,
                roomReadyQueueItemIds = setOf(item.queueItemId),
                explicitPreparationQueueItemIds = setOf(item.queueItemId),
                locallyAvailableTrackIds = setOf(descriptor.trackId),
            )
        assertEquals(RoomMediaReadiness.READY, ready[item.queueItemId])
        assertTrue(RoomMediaReadinessPolicy.canPlay(item.queueItemId, ready))
    }

    private fun track(index: Int) = TrackId(index.toString(16).padStart(64, '0'))
}
