package com.darius.unison.room

import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.playback.PlaybackIntentReconciliationPolicy
import com.darius.unison.playback.PlaybackPauseCause
import com.darius.unison.playback.PlayerExecutor
import com.darius.unison.playback.PlayerPort
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import com.darius.unison.util.MonotonicClock

/**
 * Applies and repairs canonical playback state as a playback effect component. The room actor may
 * schedule these methods, but disk/player work executes outside actor ownership. This class never
 * owns or mutates the canonical room snapshot.
 */
internal class CanonicalPlaybackCoordinator(
    private val player: PlayerPort,
    private val playerExecutor: PlayerExecutor,
    private val clock: MonotonicClock,
    private val clockSync: ClockSyncEngine,
    private val playbackSession: PlaybackSessionCoordinator,
    private val localPeerId: () -> PeerId,
    private val isCoordinator: () -> Boolean,
    private val snapshotProvider: suspend () -> RoomSnapshot?,
    private val refreshPlayerQueue: suspend (RoomSnapshot) -> Unit,
    private val isQueueItemExecutable: suspend (QueueItemId) -> Boolean,
    private val scheduleQueueRefresh: (Long) -> Unit,
    private val requestSnapshot: suspend (Long) -> Unit,
    private val send: suspend (PeerId, ProtocolBody) -> Unit,
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
                    log.info(
                        TAG,
                        DiagnosticCategory.SYNC,
                        "sync.remote_state.stale",
                        attributes =
                            mapOf(
                                "sync.incoming_revision" to decision.incomingRevision,
                                "sync.known_revision" to decision.newestKnownRevision,
                            ),
                    )
                    return
                }
            }
        if (!clockSync.synchronized) return
        val queueItem = canonical.queueItemId
        if (queueItem == null) {
            playerExecutor.cancel("Canonical queue is empty")
            playerExecutor.synchronize { pause(PlaybackPauseCause.CANONICAL_QUEUE_EMPTY) }
            return
        }
        if (snapshot.queue.none { it.queueItemId == queueItem }) {
            requestSnapshot(snapshot.sequence)
            return
        }
        if (!isQueueItemExecutable(queueItem)) {
            log.debug(
                TAG,
                DiagnosticCategory.PLAYBACK,
                "playback.execution.waiting_for_media",
                attributes = mapOf("queue.item_id" to queueItem.value.take(12)),
            )
            return
        }

        val coordinatorNow = clockSync.coordinatorNowNs()
        if (canonical.coordinatorTimestampNs > coordinatorNow + futureCommandToleranceNs) {
            if (player.state.value.queueItemId == null) refreshPlayerQueue(snapshot)
            playerExecutor.scheduleSeek(
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
            playerExecutor.scheduleSeek(
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
                participation = local.participation,
            )
        ) {
            PlaybackIntentReconciliationPolicy.Action.PLAY -> playerExecutor.synchronize { play() }
            PlaybackIntentReconciliationPolicy.Action.PAUSE ->
                playerExecutor.synchronize { pause(PlaybackPauseCause.CANONICAL_RECONCILIATION) }
            PlaybackIntentReconciliationPolicy.Action.NONE -> Unit
        }
    }

    /**
     * Called once when the canonical current track becomes locally executable after media arrival.
     * Reconstructs the latest desired state instead of replaying an old scheduled command.
     */
    suspend fun reconcileLocalExecution(reason: String) {
        val snapshot = snapshotProvider() ?: return
        val queueItemId = snapshot.playback.queueItemId ?: return
        if (!isQueueItemExecutable(queueItemId)) return
        val coordinatorNowNs =
            if (isCoordinator()) clock.nowNs()
            else {
                if (!clockSync.synchronized) return
                clockSync.coordinatorNowNs()
            }
        refreshPlayerQueue(snapshot)
        repairLocal(snapshot, coordinatorNowNs, reason)
    }

    suspend fun handleStatusReport(
        peerId: PeerId,
        report: ProtocolBody.PlaybackStatusReport,
        playbackExecutable: Boolean = true,
    ) {
        val snapshot = snapshotProvider() ?: return
        if (snapshot.members.none { it.peerId == peerId }) return
        val now = clock.nowNs()
        when (
            val action =
                playbackSession.convergenceAction(
                    peerId,
                    snapshot,
                    report,
                    now,
                    playbackExecutable,
                )
        ) {
            PlaybackConvergencePolicy.Action.None -> Unit
            is PlaybackConvergencePolicy.Action.SendPlaybackState -> {
                log.info(
                    TAG,
                    DiagnosticCategory.SYNC,
                    "sync.peer.playback_repair",
                    attributes =
                        mapOf(
                            "peer.id" to peerId.value.take(12),
                            "sync.reason" to action.reason,
                            "sync.peer_revision" to report.playbackRevision,
                            "sync.canonical_revision" to snapshot.playback.revision,
                        ),
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
                log.info(
                    TAG,
                    DiagnosticCategory.SYNC,
                    "sync.peer.queue_repair",
                    attributes =
                        mapOf(
                            "peer.id" to peerId.value.take(12),
                            "sync.reason" to action.reason,
                            "sync.peer_revision" to report.queueRevision,
                            "sync.canonical_revision" to snapshot.queueRevision,
                        ),
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
    }

    private suspend fun repairLocal(
        snapshot: RoomSnapshot,
        coordinatorNowNs: Long,
        reason: String,
    ) {
        val canonical = snapshot.playback
        val queueItemId = canonical.queueItemId
        if (queueItemId == null) {
            playerExecutor.cancel("Canonical queue is empty")
            playerExecutor.maintenance { pause(PlaybackPauseCause.CANONICAL_QUEUE_EMPTY) }
            return
        }

        if (!isQueueItemExecutable(queueItemId)) {
            log.debug(
                TAG,
                DiagnosticCategory.PLAYBACK,
                "playback.execution.waiting_for_media",
                attributes =
                    mapOf("queue.item_id" to queueItemId.value.take(12), "sync.reason" to reason),
            )
            return
        }
        val local = player.state.value
        if (local.participation != LocalPlaybackParticipation.ACTIVE) return
        if (local.queueItemId != queueItemId) {
            refreshPlayerQueue(snapshot)
            playerExecutor.scheduleSeek(
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
            playerExecutor.schedulePlay(
                queueItemId = queueItemId,
                positionMs = canonical.projectedPositionMs(coordinatorNowNs),
                executeAtCoordinatorNs = coordinatorNowNs,
            )
        } else {
            playerExecutor.schedulePause(
                queueItemId = queueItemId,
                positionMs = canonical.projectedPositionMs(coordinatorNowNs),
                executeAtCoordinatorNs = coordinatorNowNs,
            )
        }
        log.info(
            TAG,
            DiagnosticCategory.SYNC,
            "sync.local.playback_repaired",
            attributes = mapOf("sync.reason" to reason),
        )
    }

    companion object {
        private const val TAG = "CanonicalPlayback"
    }
}
