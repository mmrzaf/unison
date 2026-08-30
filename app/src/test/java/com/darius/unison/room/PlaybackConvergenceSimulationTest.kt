package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.protocol.ProtocolBody
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exercises song identity and play/pause convergence under loss, duplication and delayed repair.
 * Timing drift is intentionally out of scope; this protects the higher-priority room-state
 * invariant that every ready listener applies the latest canonical revision.
 */
class PlaybackConvergenceSimulationTest {
    private data class DeviceState(
        var playbackRevision: Long,
        var queueRevision: Long,
        var queueItemId: QueueItemId?,
        var playing: Boolean,
    )

    private sealed interface Repair {
        data class Playback(val peerId: PeerId, val snapshot: RoomSnapshot) : Repair

        data class Snapshot(val peerId: PeerId, val snapshot: RoomSnapshot) : Repair
    }

    @Test
    fun threePeersEventuallyConvergeAcrossRandomizedCommandAndNetworkOrdering() {
        repeat(200) { scenario -> runScenario(seed = 7_919L + scenario) }
    }

    private fun runScenario(seed: Long) {
        val random = Random(seed)
        val coordinatorPeer = PeerId("coordinator")
        val peers = listOf(PeerId("guest-a"), PeerId("guest-b"), PeerId("guest-c"))
        val items =
            (0 until 4).map { index ->
                QueueItem.create(
                    TrackDescriptor(
                        trackId = TrackId((index + 1).toString().repeat(64)),
                        sizeBytes = 1_024,
                        durationMs = 120_000,
                        title = "Track $index",
                    ),
                    addedBy = coordinatorPeer,
                    sequence = index.toLong() + 1,
                )
            }
        var revision = 1L
        var queueRevision = 1L
        var canonicalItem = items.first().queueItemId
        var canonicalPlaying = false
        val devices = peers.associateWith {
            DeviceState(
                playbackRevision = 0,
                queueRevision = 0,
                queueItemId = null,
                playing = false,
            )
        }
        val policy = PlaybackConvergencePolicy(minimumRepairIntervalNs = 0)
        val pending = mutableListOf<Repair>()
        var nowNs = 10_000_000_000L

        fun snapshot(): RoomSnapshot =
            RoomSnapshot(
                roomId = "room",
                roomName = "Room",
                term = CoordinatorTerm(1, coordinatorPeer),
                sequence = revision,
                members =
                    listOf(MemberSnapshot(coordinatorPeer, "Coordinator")) +
                        peers.map { MemberSnapshot(it, it.value) },
                queue = items,
                playback =
                    CanonicalPlaybackState(
                        queueItemId = canonicalItem,
                        positionAtTimestampMs = 0,
                        coordinatorTimestampNs = nowNs - 1_000_000_000L,
                        isPlaying = canonicalPlaying,
                        revision = revision,
                    ),
                queueRevision = queueRevision,
            )

        fun report(peerId: PeerId): ProtocolBody.PlaybackStatusReport {
            val state = devices.getValue(peerId)
            return ProtocolBody.PlaybackStatusReport(
                queueItemId = state.queueItemId,
                positionMs = 0,
                isPlaying = state.playing,
                participation = LocalPlaybackParticipation.ACTIVE,
                driftMs = null,
                playbackRevision = state.playbackRevision,
                queueRevision = state.queueRevision,
                canonicalSequence = state.playbackRevision,
            )
        }

        fun enqueueRepairs(room: RoomSnapshot, mayDrop: Boolean) {
            peers.shuffled(random).forEach { peerId ->
                when (policy.decide(peerId, room, report(peerId), nowNs)) {
                    PlaybackConvergencePolicy.Action.None -> Unit
                    is PlaybackConvergencePolicy.Action.SendPlaybackState ->
                        if (!mayDrop || random.nextInt(5) != 0) {
                            pending += Repair.Playback(peerId, room)
                            if (random.nextBoolean()) pending += Repair.Playback(peerId, room)
                        }
                    is PlaybackConvergencePolicy.Action.SendSnapshot ->
                        if (!mayDrop || random.nextInt(5) != 0) {
                            pending += Repair.Snapshot(peerId, room)
                            if (random.nextBoolean()) pending += Repair.Snapshot(peerId, room)
                        }
                }
            }
        }

        fun deliverSome(limit: Int) {
            repeat(minOf(limit, pending.size)) {
                val repair = pending.removeAt(random.nextInt(pending.size))
                val peerId =
                    when (repair) {
                        is Repair.Playback -> repair.peerId
                        is Repair.Snapshot -> repair.peerId
                    }
                val repairedSnapshot =
                    when (repair) {
                        is Repair.Playback -> repair.snapshot
                        is Repair.Snapshot -> repair.snapshot
                    }
                val state = devices.getValue(peerId)
                if (repairedSnapshot.playback.revision < state.playbackRevision) return@repeat
                if (repair is Repair.Snapshot) state.queueRevision = repairedSnapshot.queueRevision
                state.playbackRevision = repairedSnapshot.playback.revision
                state.queueItemId = repairedSnapshot.playback.queueItemId
                state.playing = repairedSnapshot.playback.isPlaying
            }
        }

        repeat(120) {
            nowNs += 2_000_000_000L
            when (random.nextInt(4)) {
                0 -> canonicalPlaying = !canonicalPlaying
                1 -> canonicalItem = items[random.nextInt(items.size)].queueItemId
                2 -> {
                    canonicalItem = items[random.nextInt(items.size)].queueItemId
                    canonicalPlaying = true
                }
                else -> queueRevision++
            }
            revision++

            // Model local divergence and delayed old work completing on arbitrary peers.
            if (random.nextInt(3) == 0) {
                val state = devices.getValue(peers[random.nextInt(peers.size)])
                if (random.nextBoolean()) state.playing = !canonicalPlaying
                else state.queueItemId = items[random.nextInt(items.size)].queueItemId
            }

            enqueueRepairs(snapshot(), mayDrop = true)
            deliverSome(random.nextInt(5))
        }

        // Stop mutating the room, remove packet loss, and require deterministic eventual repair.
        repeat(20) {
            nowNs += 2_000_000_000L
            enqueueRepairs(snapshot(), mayDrop = false)
            deliverSome(pending.size)
        }

        val finalSnapshot = snapshot()
        devices.values.forEach { state ->
            assertEquals(finalSnapshot.queueRevision, state.queueRevision)
            assertEquals(finalSnapshot.playback.revision, state.playbackRevision)
            assertEquals(finalSnapshot.playback.queueItemId, state.queueItemId)
            assertEquals(finalSnapshot.playback.isPlaying, state.playing)
        }
    }

    private fun <T> List<T>.shuffled(random: Random): List<T> =
        toMutableList().also {
            for (index in it.lastIndex downTo 1) {
                val swap = random.nextInt(index + 1)
                val value = it[index]
                it[index] = it[swap]
                it[swap] = value
            }
        }
}
