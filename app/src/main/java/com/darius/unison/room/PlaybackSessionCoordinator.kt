package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.playback.AudioOutputRoute
import com.darius.unison.protocol.ProtocolBody

/**
 * Owns the small but critical state machine around canonical playback synchronization.
 *
 * [RoomRuntime] remains the only room-state actor and performs all effects. This coordinator keeps
 * revision acceptance, repair throttling, cadence and discontinuity bookkeeping together so those
 * rules cannot drift across reconnect, promotion and teardown paths.
 */
internal class PlaybackSessionCoordinator(
    private val playbackStatusReportIntervalNs: Long,
    private val clockQualityReportIntervalNs: Long,
    private val convergence: PlaybackConvergencePolicy = PlaybackConvergencePolicy(),
) {
    sealed interface IncomingSyncDecision {
        data class Apply(val playback: CanonicalPlaybackState) : IncomingSyncDecision

        data class RequestSnapshot(val lastAppliedSequence: Long) : IncomingSyncDecision

        data class IgnoreStale(
            val incomingRevision: Long,
            val newestKnownRevision: Long,
        ) : IncomingSyncDecision
    }

    private var latestPlaybackStateSync: CanonicalPlaybackState? = null
    private var lastPlaybackReferenceBroadcastNs = 0L
    private var lastPlaybackStatusReportNs = 0L
    private var lastClockQualityReportNs = 0L
    private var lastObservedOutputRoute: AudioOutputRoute? = null

    @Synchronized
    fun evaluateIncomingSync(
        sync: ProtocolBody.PlaybackStateSync,
        snapshot: RoomSnapshot,
    ): IncomingSyncDecision {
        val canonical = sync.playback
        if (
            sync.canonicalSequence < 0L ||
                sync.queueRevision < 0L ||
                canonical.revision < 0L ||
                sync.queueRevision > sync.canonicalSequence ||
                canonical.revision > sync.canonicalSequence
        ) {
            return IncomingSyncDecision.RequestSnapshot(snapshot.sequence)
        }

        val newestKnownRevision =
            maxOf(snapshot.playback.revision, latestPlaybackStateSync?.revision ?: 0L)
        if (canonical.revision < newestKnownRevision) {
            return IncomingSyncDecision.IgnoreStale(canonical.revision, newestKnownRevision)
        }

        if (
            sync.canonicalSequence > snapshot.sequence ||
                sync.queueRevision > snapshot.queueRevision ||
                canonical.revision > snapshot.sequence
        ) {
            return IncomingSyncDecision.RequestSnapshot(snapshot.sequence)
        }

        latestPlaybackStateSync = canonical
        return IncomingSyncDecision.Apply(canonical)
    }

    @Synchronized
    fun canonicalForTick(snapshot: RoomSnapshot, coordinator: Boolean): CanonicalPlaybackState =
        if (coordinator) snapshot.playback else latestPlaybackStateSync ?: snapshot.playback

    @Synchronized
    fun playbackStateSync(
        snapshot: RoomSnapshot,
        atCoordinatorNs: Long,
        recovery: Boolean = false,
    ): ProtocolBody.PlaybackStateSync =
        ProtocolBody.PlaybackStateSync(
            playback = snapshot.playback.forStateSync(atCoordinatorNs),
            canonicalSequence = snapshot.sequence,
            queueRevision = snapshot.queueRevision,
            recovery = recovery,
        )

    @Synchronized
    fun shouldReportPlaybackStatus(sampledAtLocalNs: Long): Boolean {
        if (sampledAtLocalNs - lastPlaybackStatusReportNs < playbackStatusReportIntervalNs) {
            return false
        }
        lastPlaybackStatusReportNs = sampledAtLocalNs
        return true
    }

    @Synchronized
    fun shouldBroadcastPlaybackReference(nowCoordinatorNs: Long, intervalNs: Long): Boolean {
        if (nowCoordinatorNs - lastPlaybackReferenceBroadcastNs < intervalNs) return false
        lastPlaybackReferenceBroadcastNs = nowCoordinatorNs
        return true
    }

    @Synchronized
    fun shouldReportClockQuality(nowLocalNs: Long, newlySynchronized: Boolean): Boolean {
        if (
            !newlySynchronized &&
                nowLocalNs - lastClockQualityReportNs < clockQualityReportIntervalNs
        ) {
            return false
        }
        lastClockQualityReportNs = nowLocalNs
        return true
    }

    /** Returns true only when a previously observed route changed. */
    @Synchronized
    fun observeOutputRoute(route: AudioOutputRoute): Boolean {
        val previous = lastObservedOutputRoute
        lastObservedOutputRoute = route
        return previous != null && previous != route
    }

    @Synchronized
    fun convergenceAction(
        peerId: PeerId,
        snapshot: RoomSnapshot,
        report: ProtocolBody.PlaybackStatusReport,
        coordinatorNowNs: Long,
        playbackExecutable: Boolean = true,
    ): PlaybackConvergencePolicy.Action =
        convergence.decide(peerId, snapshot, report, coordinatorNowNs, playbackExecutable)

    @Synchronized fun forgetPeer(peerId: PeerId) = convergence.forget(peerId)

    @Synchronized
    fun seedCanonical(playback: CanonicalPlaybackState) {
        latestPlaybackStateSync = playback
    }

    @Synchronized
    fun clearCanonical() {
        latestPlaybackStateSync = null
    }

    @Synchronized
    fun resetClockQuality() {
        lastClockQualityReportNs = 0L
    }

    @Synchronized
    fun resetAfterDiscontinuity(canonical: CanonicalPlaybackState?) {
        latestPlaybackStateSync = canonical
        lastPlaybackReferenceBroadcastNs = 0L
        lastPlaybackStatusReportNs = 0L
        lastObservedOutputRoute = null
    }

    @Synchronized
    fun resetSession() {
        latestPlaybackStateSync = null
        lastPlaybackReferenceBroadcastNs = 0L
        lastPlaybackStatusReportNs = 0L
        lastClockQualityReportNs = 0L
        lastObservedOutputRoute = null
        convergence.reset()
    }
}
