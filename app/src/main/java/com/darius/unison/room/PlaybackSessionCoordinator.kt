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
    private val lifecycleDiscontinuityNs: Long,
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
    private var lastPlaybackSyncTickLocalNs = 0L
    private var lastClockQualityReportNs = 0L
    private var lastObservedOutputRoute: AudioOutputRoute? = null

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

    fun canonicalForTick(snapshot: RoomSnapshot, coordinator: Boolean): CanonicalPlaybackState =
        if (coordinator) snapshot.playback else latestPlaybackStateSync ?: snapshot.playback

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

    fun suspendSynchronizationTicks() {
        lastPlaybackSyncTickLocalNs = 0L
    }

    /** Returns true when the actor was delayed long enough to require full reacquisition. */
    fun beginSynchronizationTick(nowLocalNs: Long): Boolean {
        val previousTick = lastPlaybackSyncTickLocalNs
        lastPlaybackSyncTickLocalNs = nowLocalNs
        return previousTick != 0L && nowLocalNs - previousTick > lifecycleDiscontinuityNs
    }

    fun shouldReportPlaybackStatus(sampledAtLocalNs: Long): Boolean {
        if (sampledAtLocalNs - lastPlaybackStatusReportNs < playbackStatusReportIntervalNs) {
            return false
        }
        lastPlaybackStatusReportNs = sampledAtLocalNs
        return true
    }

    fun shouldBroadcastPlaybackReference(nowCoordinatorNs: Long, intervalNs: Long): Boolean {
        if (nowCoordinatorNs - lastPlaybackReferenceBroadcastNs < intervalNs) return false
        lastPlaybackReferenceBroadcastNs = nowCoordinatorNs
        return true
    }

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
    fun observeOutputRoute(route: AudioOutputRoute): Boolean {
        val previous = lastObservedOutputRoute
        lastObservedOutputRoute = route
        return previous != null && previous != route
    }

    fun convergenceAction(
        peerId: PeerId,
        snapshot: RoomSnapshot,
        report: ProtocolBody.PlaybackStatusReport,
        coordinatorNowNs: Long,
    ): PlaybackConvergencePolicy.Action =
        convergence.decide(peerId, snapshot, report, coordinatorNowNs)

    fun forgetPeer(peerId: PeerId) = convergence.forget(peerId)

    fun seedCanonical(playback: CanonicalPlaybackState) {
        latestPlaybackStateSync = playback
    }

    fun clearCanonical() {
        latestPlaybackStateSync = null
    }

    fun resetClockQuality() {
        lastClockQualityReportNs = 0L
    }

    fun resetAfterDiscontinuity(canonical: CanonicalPlaybackState?) {
        latestPlaybackStateSync = canonical
        lastPlaybackReferenceBroadcastNs = 0L
        lastPlaybackStatusReportNs = 0L
        lastPlaybackSyncTickLocalNs = 0L
        lastObservedOutputRoute = null
    }

    fun resetSession() {
        latestPlaybackStateSync = null
        lastPlaybackReferenceBroadcastNs = 0L
        lastPlaybackStatusReportNs = 0L
        lastPlaybackSyncTickLocalNs = 0L
        lastClockQualityReportNs = 0L
        lastObservedOutputRoute = null
        convergence.reset()
    }
}
