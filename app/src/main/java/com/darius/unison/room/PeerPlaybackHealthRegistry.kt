package com.darius.unison.room

import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.PeerId

internal enum class PeerPlaybackHealthState {
    WARMING_UP,
    CATCHING_UP,
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
 * Coordinator-owned playback-health/admission leases. Clock health alone is not enough to join the
 * blocking playback cohort: a peer must also have the current playback runway locally available.
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
        var contentReady: Boolean = false,
        var roundTripNs: Long? = null,
        var uncertaintyNs: Long? = null,
        var participation: LocalPlaybackParticipation? = null,
        var updatedAtNs: Long = 0L,
    )

    private val entries = mutableMapOf<PeerId, Entry>()

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
            entry.roundTripNs = roundTripNs
            entry.uncertaintyNs = uncertaintyNs
        } else {
            entry.roundTripNs = null
            entry.uncertaintyNs = null
        }
        val isReady = health(peerId, nowNs).state == PeerPlaybackHealthState.READY
        if (isReady) entry.everReady = true
        return wasReady != isReady
    }

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
        if (isReady) entry.everReady = true
        return wasReady != isReady
    }

    fun updateContentReady(peerId: PeerId, contentReady: Boolean, nowNs: Long): Boolean {
        require(nowNs >= 0L)
        val wasReady = health(peerId, nowNs).state == PeerPlaybackHealthState.READY
        val entry = entries.getOrPut(peerId) { Entry() }
        entry.contentReady = contentReady
        val isReady = health(peerId, nowNs).state == PeerPlaybackHealthState.READY
        if (isReady) entry.everReady = true
        return wasReady != isReady
    }

    fun isContentReady(peerId: PeerId): Boolean = entries[peerId]?.contentReady == true

    fun isClockReady(peerId: PeerId, nowNs: Long): Boolean {
        require(nowNs >= 0L)
        val entry = entries[peerId] ?: return false
        val leaseFresh = nowNs >= entry.updatedAtNs && nowNs - entry.updatedAtNs <= readyLeaseNs
        return entry.synchronized && leaseFresh
    }

    /**
     * True when a peer has a fresh room clock and is actively participating in playback. Content
     * runway is deliberately irrelevant here: a listener can be audibly synchronized with the
     * current song while the next song is still catching up.
     */
    fun isSynchronizationParticipant(peerId: PeerId, nowNs: Long): Boolean =
        when (health(peerId, nowNs).state) {
            PeerPlaybackHealthState.READY,
            PeerPlaybackHealthState.CATCHING_UP -> true
            PeerPlaybackHealthState.WARMING_UP,
            PeerPlaybackHealthState.DEGRADED -> false
        }

    fun health(peerId: PeerId, nowNs: Long): PeerClockHealth {
        val entry = entries[peerId]
            ?: return PeerClockHealth(PeerPlaybackHealthState.WARMING_UP)
        val leaseFresh = nowNs >= entry.updatedAtNs && nowNs - entry.updatedAtNs <= readyLeaseNs
        val transportReady =
            entry.synchronized &&
                leaseFresh &&
                entry.participation == LocalPlaybackParticipation.ACTIVE
        val state =
            when {
                transportReady && entry.contentReady -> PeerPlaybackHealthState.READY
                transportReady -> PeerPlaybackHealthState.CATCHING_UP
                entry.everReady -> PeerPlaybackHealthState.DEGRADED
                else -> PeerPlaybackHealthState.WARMING_UP
            }
        return PeerClockHealth(
            state = state,
            roundTripNs = entry.roundTripNs.takeIf { transportReady },
            uncertaintyNs = entry.uncertaintyNs.takeIf { transportReady },
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

    /**
     * Peers whose verified media is relevant to room readiness. Unlike [readyPeers], this projection
     * intentionally ignores temporary audible-output inhibition. A phone call must not erase
     * knowledge that a device still owns a verified copy of the track.
     *
     * Newly joining/catching-up peers remain non-blocking until clock + current runway are ready.
     */
    fun contentReadinessPeers(
        connectedPeers: Set<PeerId>,
        localCoordinatorPeerId: PeerId,
        nowNs: Long,
    ): Set<PeerId> =
        buildSet {
            if (localCoordinatorPeerId in connectedPeers) add(localCoordinatorPeerId)
            connectedPeers.asSequence()
                .filter { it != localCoordinatorPeerId }
                .filter { peerId ->
                    val entry = entries[peerId] ?: return@filter false
                    entry.contentReady && isClockReady(peerId, nowNs)
                }
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
