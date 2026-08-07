package com.darius.unison.room

import com.darius.unison.playback.CanonicalPlaybackDispatcher
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog

/** Owns structured room-session context and lifecycle records outside the room actor. */
internal class RoomDiagnostics(private val log: DiagnosticLog) {
    private val logger = log.scoped("RoomRuntime", DiagnosticCategory.ROOM)

    fun begin(roomId: String, role: String) = log.beginRoom(roomId, role)

    fun updateRole(role: String) = log.updateRoomRole(role)

    fun currentSessionId(): String? = log.currentRoomSessionId()

    fun debug(eventName: String, vararg attributes: Pair<String, Any?>) {
        logger.debug(eventName, attributes = mapOf(*attributes))
    }

    fun info(eventName: String, vararg attributes: Pair<String, Any?>) {
        logger.info(eventName, attributes = mapOf(*attributes))
    }

    fun warn(
        eventName: String,
        throwable: Throwable? = null,
        vararg attributes: Pair<String, Any?>,
    ) {
        logger.warn(eventName, attributes = mapOf(*attributes), throwable = throwable)
    }

    fun error(
        eventName: String,
        throwable: Throwable? = null,
        vararg attributes: Pair<String, Any?>,
    ) {
        logger.error(eventName, attributes = mapOf(*attributes), throwable = throwable)
    }

    fun created(listenPort: Int) {
        logger.info(
            "room.session.created",
            "Room ready",
            attributes = mapOf("network.listen_port" to listenPort),
        )
    }

    fun joinStarted(attempt: Int, peerId: String, port: Int) {
        logger.info(
            "room.join.started",
            "Connecting to coordinator",
            attributes =
                mapOf(
                    "join.attempt" to attempt,
                    "peer.id" to peerId.take(12),
                    "network.target_port" to port,
                ),
        )
    }

    fun joinAdmitted(attempt: Int, durationMs: Long) {
        logger.info(
            "room.join.admitted",
            "Coordinator admission completed",
            attributes = mapOf("join.attempt" to attempt, "operation.duration_ms" to durationMs),
        )
    }

    fun joinFailed(attempt: Int, durationMs: Long, error: Throwable) {
        logger.warn(
            "room.join.failed",
            "Coordinator admission failed",
            attributes = mapOf("join.attempt" to attempt, "operation.duration_ms" to durationMs),
            throwable = error,
        )
    }

    fun end(
        sessionId: String?,
        durationMs: Long,
        connectionCount: Int,
        transferCount: Int,
        remainingJobs: Int,
        playback: CanonicalPlaybackDispatcher.Metrics,
    ) {
        if (sessionId == null) return
        logger.info(
            "room.session.ended",
            "Room session shut down",
            attributes =
                mapOf(
                    "operation.duration_ms" to durationMs,
                    "network.connection_count" to connectionCount,
                    "transfer.active_count" to transferCount,
                    "coroutine.remaining_jobs" to remainingJobs,
                    "log.pending_count" to log.pendingEventCount,
                    "log.dropped_count" to log.droppedEventCount,
                    "playback.exact_applied" to playback.exactApplied,
                    "playback.exact_submitted" to playback.exactSubmitted,
                    "playback.reconcile_applied" to playback.reconciliationApplied,
                    "playback.reconcile_submitted" to playback.reconciliationSubmitted,
                    "playback.reconcile_collapsed" to playback.reconciliationCollapsed,
                    "playback.reconcile_skipped" to playback.reconciliationSkipped,
                    "playback.failures" to playback.failures,
                ),
        )
        log.endRoom(sessionId)
    }

    fun endNow(sessionId: String?) = log.endRoom(sessionId)
}
