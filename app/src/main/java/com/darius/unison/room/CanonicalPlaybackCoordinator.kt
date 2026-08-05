package com.darius.unison.room

import com.darius.unison.model.MemberPlaybackTelemetry
import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.playback.PlaybackIntentReconciliationPolicy
import com.darius.unison.playback.PlayerMutationCoordinator
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.ScheduledPlaybackController
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.util.DiagnosticLog
import com.darius.unison.util.MonotonicClock

/**
 * Applies and repairs canonical playback state. All methods are invoked by the serialized room
 * actor; this class owns effects but never owns or mutates the canonical room snapshot.
 */
internal class CanonicalPlaybackCoordinator(
    private val player: PlayerPort,
    private val playerMutations: PlayerMutationCoordinator,
    private val scheduler: ScheduledPlaybackController,
    private val clock: MonotonicClock,
    private val clockSync: ClockSyncEngine,
    private val playbackSession: PlaybackSessionCoordinator,
    private val localPeerId: () -> PeerId,
    private val isCoordinator: () -> Boolean,
    private val snapshotProvider: suspend () -> RoomSnapshot?,
    private val refreshPlayerQueue: suspend (RoomSnapshot) -> Unit,
    private val scheduleQueueRefresh: (Long) -> Unit,
    private val requestSnapshot: suspend (Long) -> Unit,
    private val send: suspend (PeerId, ProtocolBody) -> Unit,
    private val broadcast: suspend (ProtocolBody, PeerId?) -> Unit,
    private val updateMemberTelemetry: (PeerId, MemberPlaybackTelemetry) -> Unit,
    private val log: DiagnosticLog,
    private val futureCommandToleranceNs: Long,
) {
    suspend fun applyRemoteSync(sync: ProtocolBody.PlaybackStateSync) {
        if (isCoordinator()) return
        val snapshot = snapshotProvider() ?: return
        val canonical =
            when (val decision = playbackSession.evaluateIncomingSync(sync, snapshot)) {
                is PlaybackSessionCoordinator.IncomingSyncDecision.Apply -> decision.playback
                is PlaybackSessionCoordinator.IncomingSyncDecision.RequestSnapshot -> {
                    requestSnapshot(decision.lastAppliedSequence)
                    return
                }
                is PlaybackSessionCoordinator.IncomingSyncDecision.IgnoreStale -> {
                    log.i(
                        TAG,
                        "Ignored stale playback sync revision=${decision.incomingRevision} " +
                            "known=${decision.newestKnownRevision}",
                    )
                    return
                }
            }
        if (!clockSync.synchronized) return
        val queueItem = canonical.queueItemId
        if (queueItem == null) {
            scheduler.cancel("Canonical queue is empty")
            playerMutations.synchronize { pause() }
            return
        }
        if (snapshot.queue.none { it.queueItemId == queueItem }) {
            requestSnapshot(snapshot.sequence)
            return
        }

        val coordinatorNow = clockSync.coordinatorNowNs()
        if (canonical.coordinatorTimestampNs > coordinatorNow + futureCommandToleranceNs) {
            if (player.state.value.queueItemId == null) refreshPlayerQueue(snapshot)
            scheduler.scheduleSeek(
                queueItemId = queueItem,
                positionMs = canonical.positionAtTimestampMs,
                resume = canonical.isPlaying,
                executeAtCoordinatorNs = canonical.coordinatorTimestampNs,
            )
            scheduleQueueRefresh(canonical.coordinatorTimestampNs)
            return
        }

        val local = player.state.value
        if (local.queueItemId != queueItem) {
            refreshPlayerQueue(snapshot)
            scheduler.scheduleSeek(
                queueItemId = queueItem,
                positionMs = canonical.projectedPositionMs(coordinatorNow),
                resume = canonical.isPlaying,
                executeAtCoordinatorNs = coordinatorNow,
            )
            scheduleQueueRefresh(coordinatorNow)
            return
        }

        when (
            PlaybackIntentReconciliationPolicy.decide(
                canonicalPlaying = canonical.isPlaying,
                localPlayWhenReady = local.playWhenReady,
                locallySuppressed = local.locallySuppressed,
            )
        ) {
            PlaybackIntentReconciliationPolicy.Action.PLAY -> playerMutations.synchronize { play() }
            PlaybackIntentReconciliationPolicy.Action.PAUSE ->
                playerMutations.synchronize { pause() }
            PlaybackIntentReconciliationPolicy.Action.NONE -> Unit
        }
    }

    suspend fun handleStatusReport(
        peerId: PeerId,
        report: ProtocolBody.PlaybackStatusReport,
    ) {
        val snapshot = snapshotProvider() ?: return
        if (snapshot.members.none { it.peerId == peerId && it.connected }) return
        val now = clock.nowNs()
        when (val action = playbackSession.convergenceAction(peerId, snapshot, report, now)) {
            PlaybackConvergencePolicy.Action.None -> Unit
            is PlaybackConvergencePolicy.Action.SendPlaybackState -> {
                log.i(
                    TAG,
                    "Repair playback peer=${peerId.value.take(8)} " +
                        "reason=${action.reason} localRev=${report.playbackRevision} " +
                        "canonicalRev=${snapshot.playback.revision}",
                )
                if (peerId == localPeerId()) {
                    repairLocal(snapshot, now, action.reason)
                } else {
                    send(
                        peerId,
                        playbackSession.playbackStateSync(snapshot, now, recovery = true),
                    )
                }
            }
            is PlaybackConvergencePolicy.Action.SendSnapshot -> {
                log.i(
                    TAG,
                    "Repair queue peer=${peerId.value.take(8)} reason=${action.reason} " +
                        "localRev=${report.queueRevision} canonicalRev=${snapshot.queueRevision}",
                )
                if (peerId == localPeerId()) {
                    refreshPlayerQueue(snapshot)
                    repairLocal(snapshot, now, action.reason)
                } else {
                    send(peerId, ProtocolBody.Snapshot(snapshot))
                    send(
                        peerId,
                        playbackSession.playbackStateSync(snapshot, now, recovery = true),
                    )
                }
            }
        }
        val status =
            ProtocolBody.MemberPlaybackStatus(
                peerId = peerId,
                queueItemId = report.queueItemId,
                positionMs = report.positionMs,
                isPlaying = report.isPlaying,
                driftMs = report.driftMs,
                playbackRevision = report.playbackRevision,
            )
        applyMemberStatus(status)
        broadcast(status, peerId)
    }

    fun applyMemberStatus(status: ProtocolBody.MemberPlaybackStatus) {
        updateMemberTelemetry(
            status.peerId,
            MemberPlaybackTelemetry(positionMs = status.positionMs, driftMs = status.driftMs),
        )
    }

    private suspend fun repairLocal(
        snapshot: RoomSnapshot,
        coordinatorNowNs: Long,
        reason: String,
    ) {
        val canonical = snapshot.playback
        val queueItemId = canonical.queueItemId
        if (queueItemId == null) {
            scheduler.cancel("Canonical queue is empty")
            playerMutations.maintenance { pause() }
            return
        }

        val local = player.state.value
        if (local.queueItemId != queueItemId) {
            refreshPlayerQueue(snapshot)
            scheduler.scheduleSeek(
                queueItemId = queueItemId,
                positionMs = canonical.projectedPositionMs(coordinatorNowNs),
                resume = canonical.isPlaying,
                executeAtCoordinatorNs = coordinatorNowNs,
            )
            scheduleQueueRefresh(coordinatorNowNs)
            return
        }

        if (local.playWhenReady == canonical.isPlaying) return
        if (canonical.isPlaying) {
            scheduler.schedulePlay(
                queueItemId = queueItemId,
                positionMs = canonical.projectedPositionMs(coordinatorNowNs),
                executeAtCoordinatorNs = coordinatorNowNs,
            )
        } else {
            scheduler.schedulePause(
                queueItemId = queueItemId,
                positionMs = canonical.projectedPositionMs(coordinatorNowNs),
                executeAtCoordinatorNs = coordinatorNowNs,
            )
        }
        log.i(TAG, "Applied local canonical playback repair reason=$reason")
    }

    companion object {
        private const val TAG = "CanonicalPlayback"
    }
}
