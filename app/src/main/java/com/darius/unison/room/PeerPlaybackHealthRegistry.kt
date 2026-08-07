package com.darius.unison.room

import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.PeerId

internal enum class PeerPlaybackHealthState {
    WARMING_UP,
    READY,
    DEGRADED,
}

internal data class PeerClockHealth(
    val state: PeerPlaybackHealthState,
    val roundTripNs: Long? = null,
    val uncertaintyNs: Long? = null,
    val updatedAtNs: Long? = null,
)

/**
 * Coordinator-owned playback-health leases. A peer is allowed to influence synchronized room
 * timing only while it has a fresh positive clock-quality lease. Stale or explicitly unhealthy
 * peers repair locally without slowing the healthy playback cohort.
 */
internal class PeerPlaybackHealthRegistry(
    private val readyLeaseNs: Long,
) {
    init {
        require(readyLeaseNs > 0L)
    }

    private data class Entry(
        var synchronized: Boolean = false,
        var everReady: Boolean = false,
        var roundTripNs: Long? = null,
        var uncertaintyNs: Long? = null,
        var participation: LocalPlaybackParticipation? = null,
        var updatedAtNs: Long = 0L,
    )

    private val entries = mutableMapOf<PeerId, Entry>()

    /** Returns true when the peer enters or leaves the READY playback cohort. */
    fun updateClock(
        peerId: PeerId,
        synchronized: Boolean,
        roundTripNs: Long?,
        uncertaintyNs: Long?,
        nowNs: Long,
    ): Boolean {
        require(nowNs >= 0L)
        require(roundTripNs == null || roundTripNs >= 0L)
        require(uncertaintyNs == null || uncertaintyNs >= 0L)
        require(!synchronized || (roundTripNs != null && uncertaintyNs != null))
        val wasReady = health(peerId, nowNs).state == PeerPlaybackHealthState.READY
        val entry = entries.getOrPut(peerId) { Entry() }
        entry.synchronized = synchronized
        entry.updatedAtNs = nowNs
        if (synchronized) {
            entry.everReady = true
            entry.roundTripNs = roundTripNs
            entry.uncertaintyNs = uncertaintyNs
        } else {
            entry.roundTripNs = null
            entry.uncertaintyNs = null
        }
        val isReady = health(peerId, nowNs).state == PeerPlaybackHealthState.READY
        return wasReady != isReady
    }

    /** Returns true when playback participation changes READY cohort membership. */
    fun updateParticipation(
        peerId: PeerId,
        participation: LocalPlaybackParticipation,
        nowNs: Long,
    ): Boolean {
        require(nowNs >= 0L)
        val wasReady = health(peerId, nowNs).state == PeerPlaybackHealthState.READY
        val entry = entries.getOrPut(peerId) { Entry() }
        entry.participation = participation
        val isReady = health(peerId, nowNs).state == PeerPlaybackHealthState.READY
        return wasReady != isReady
    }

    fun health(peerId: PeerId, nowNs: Long): PeerClockHealth {
        val entry = entries[peerId]
            ?: return PeerClockHealth(PeerPlaybackHealthState.WARMING_UP)
        val leaseFresh = nowNs >= entry.updatedAtNs && nowNs - entry.updatedAtNs <= readyLeaseNs
        val state =
            if (
                entry.synchronized &&
                    leaseFresh &&
                    entry.participation == LocalPlaybackParticipation.ACTIVE
            ) {
                PeerPlaybackHealthState.READY
            } else if (entry.everReady) {
                PeerPlaybackHealthState.DEGRADED
            } else {
                PeerPlaybackHealthState.WARMING_UP
            }
        return PeerClockHealth(
            state = state,
            roundTripNs = entry.roundTripNs.takeIf { state == PeerPlaybackHealthState.READY },
            uncertaintyNs = entry.uncertaintyNs.takeIf { state == PeerPlaybackHealthState.READY },
            updatedAtNs = entry.updatedAtNs,
        )
    }

    fun readyPeers(
        connectedPeers: Set<PeerId>,
        localCoordinatorPeerId: PeerId,
        localParticipation: LocalPlaybackParticipation,
        nowNs: Long,
    ): Set<PeerId> =
        buildSet {
            if (
                localCoordinatorPeerId in connectedPeers &&
                    localParticipation == LocalPlaybackParticipation.ACTIVE
            ) {
                add(localCoordinatorPeerId)
            }
            connectedPeers.asSequence()
                .filter { it != localCoordinatorPeerId }
                .filter { health(it, nowNs).state == PeerPlaybackHealthState.READY }
                .forEach { add(it) }
        }

    fun readyClockQualities(
        connectedPeers: Set<PeerId>,
        localCoordinatorPeerId: PeerId,
        nowNs: Long,
    ): List<PeerClockHealth> =
        connectedPeers.asSequence()
            .filter { it != localCoordinatorPeerId }
            .map { health(it, nowNs) }
            .filter { it.state == PeerPlaybackHealthState.READY }
            .toList()

    fun expireReadyLeases(nowNs: Long): Boolean {
        var changed = false
        entries.values.forEach { entry ->
            if (
                entry.synchronized &&
                    (nowNs < entry.updatedAtNs || nowNs - entry.updatedAtNs > readyLeaseNs)
            ) {
                entry.synchronized = false
                entry.roundTripNs = null
                entry.uncertaintyNs = null
                changed = true
            }
        }
        return changed
    }

    fun remove(peerId: PeerId) {
        entries.remove(peerId)
    }

    fun clear() {
        entries.clear()
    }
}
