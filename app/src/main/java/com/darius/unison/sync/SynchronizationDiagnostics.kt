package com.darius.unison.sync

import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import com.darius.unison.util.DiagnosticLogger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One safe, structured synchronization event. Secrets and file paths are deliberately absent. */
data class SynchronizationEvent(
    val timestampLocalNs: Long,
    val timestampCoordinatorNs: Long,
    val deviceId: String,
    val deviceModel: String,
    val androidVersion: Int,
    val outputRoute: String,
    val roomIdHash: String,
    val coordinatorTerm: Long,
    val queueItemId: String?,
    val canonicalPositionMs: Long?,
    val sampledPlayerPositionMs: Long,
    val sampleAgeMs: Long,
    val rawDriftMs: Long?,
    val filteredDriftMs: Long?,
    val selectedSpeed: Float,
    val learnedBaselineSpeed: Float,
    val clockOffsetNs: Long,
    val clockRate: Double,
    val clockRttMs: Double?,
    val clockUncertaintyMs: Double?,
    val clockState: String,
    val playbackSyncState: String,
    val action: String,
    val actionReason: String,
    val hardSeekCount: Int,
    val buffering: Boolean,
)

/**
 * Rate-limited synchronization diagnostics. Producers never perform disk I/O: a bounded channel
 * feeds the application's single structured diagnostic sink.
 */
class SynchronizationDiagnostics(
    scope: CoroutineScope,
    log: DiagnosticLog,
) : AutoCloseable {
    private val logger: DiagnosticLogger = log.scoped(TAG, DiagnosticCategory.SYNC)
    private val channel =
        Channel<SynchronizationEvent>(
            capacity = 256,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val lastRoutineRecordNs = AtomicLong(Long.MIN_VALUE)
    private val lastCorrectionRecordNs = AtomicLong(Long.MIN_VALUE)
    private val decisionLock = Any()
    private var lastStateSignature: String? = null
    private var lastHardSeekCount = 0
    private val writer: Job =
        scope.launch(Dispatchers.IO) {
            for (event in channel) {
                val attributes = event.toDiagnosticAttributes()
                when {
                    event.action == "SEEK" ->
                        logger.warn("sync.hard_seek", attributes = attributes)
                    event.buffering ->
                        logger.debug("sync.buffering", attributes = attributes)
                    event.action == "SET_SPEED" ->
                        logger.debug("sync.speed_adjustment", attributes = attributes)
                    else -> logger.debug("sync.sample", attributes = attributes)
                }
            }
        }

    fun record(event: SynchronizationEvent) {
        val decision =
            synchronized(decisionLock) {
                val signature = event.stateSignature()
                val transition = signature != lastStateSignature
                if (transition) lastStateSignature = signature
                val newHardSeek = event.hardSeekCount > lastHardSeekCount
                if (event.hardSeekCount > lastHardSeekCount) lastHardSeekCount = event.hardSeekCount
                RecordDecision(transition = transition, newHardSeek = newHardSeek)
            }
        val correctionSample =
            event.isCorrection() &&
                claimInterval(
                    lastCorrectionRecordNs,
                    event.timestampLocalNs,
                    CORRECTION_SAMPLE_INTERVAL_NS,
                )
        val urgent =
            decision.newHardSeek ||
                (decision.transition && event.buffering) ||
                event.action == "SEEK"
        val routineSample =
            if (!decision.transition && !urgent && !correctionSample) {
                claimRoutineSample(event.timestampLocalNs)
            } else {
                false
            }
        if (!decision.transition && !urgent && !correctionSample && !routineSample) return
        if (!routineSample) markRecorded(lastRoutineRecordNs, event.timestampLocalNs)
        channel.trySend(event)
    }

    fun clear() {
        lastRoutineRecordNs.set(Long.MIN_VALUE)
        lastCorrectionRecordNs.set(Long.MIN_VALUE)
        synchronized(decisionLock) {
            lastStateSignature = null
            lastHardSeekCount = 0
        }
    }

    private fun claimRoutineSample(nowNs: Long): Boolean {
        return claimInterval(lastRoutineRecordNs, nowNs, ROUTINE_SAMPLE_INTERVAL_NS)
    }

    private fun claimInterval(marker: AtomicLong, nowNs: Long, intervalNs: Long): Boolean {
        while (true) {
            val previous = marker.get()
            if (previous != Long.MIN_VALUE && nowNs - previous < intervalNs) return false
            if (marker.compareAndSet(previous, nowNs)) return true
        }
    }

    private fun markRecorded(marker: AtomicLong, nowNs: Long) {
        while (true) {
            val previous = marker.get()
            if (previous >= nowNs) return
            if (marker.compareAndSet(previous, nowNs)) return
        }
    }

    private fun SynchronizationEvent.isCorrection(): Boolean =
        action == "SET_SPEED" ||
            action == "SEEK" ||
            (rawDriftMs?.let { kotlin.math.abs(it) >= SIGNIFICANT_DRIFT_MS } == true)

    private fun SynchronizationEvent.stateSignature(): String = buildString {
        append(clockState).append('|')
        append(playbackSyncState).append('|')
        append(action).append('|')
        append(actionReason).append('|')
        append(buffering).append('|')
        append(outputRoute)
    }

    private data class RecordDecision(
        val transition: Boolean,
        val newHardSeek: Boolean,
    )

    suspend fun closeAndJoin(timeoutMs: Long = CLOSE_TIMEOUT_MS): Boolean {
        channel.close()
        val drained =
            withTimeoutOrNull(timeoutMs) {
                writer.join()
                true
            } ?: false
        if (!drained) writer.cancelAndJoin()
        return drained
    }

    override fun close() {
        channel.close()
        writer.cancel()
    }

    private fun SynchronizationEvent.toDiagnosticAttributes(): Map<String, Any?> =
        linkedMapOf(
            "sync.timestamp_local_ns" to timestampLocalNs,
            "sync.timestamp_coordinator_ns" to timestampCoordinatorNs,
            "device.id" to deviceId,
            "device.model" to deviceModel,
            "android.api_level" to androidVersion,
            "audio.output_route" to outputRoute,
            "sync.coordinator_term" to coordinatorTerm,
            "queue.item_id" to queueItemId?.take(12),
            "playback.canonical_position_ms" to canonicalPositionMs,
            "playback.sampled_position_ms" to sampledPlayerPositionMs,
            "playback.sample_age_ms" to sampleAgeMs,
            "sync.raw_drift_ms" to rawDriftMs,
            "sync.filtered_drift_ms" to filteredDriftMs,
            "playback.speed" to selectedSpeed,
            "playback.baseline_speed" to learnedBaselineSpeed,
            "clock.offset_ns" to clockOffsetNs,
            "clock.rate" to clockRate,
            "clock.rtt_ms" to clockRttMs,
            "clock.uncertainty_ms" to clockUncertaintyMs,
            "clock.state" to clockState,
            "sync.state" to playbackSyncState,
            "sync.action" to action,
            "sync.reason" to actionReason,
            "sync.hard_seek_count" to hardSeekCount,
            "playback.buffering" to buffering,
        )

    private companion object {
        const val TAG = "UnisonSync"
        const val ROUTINE_SAMPLE_INTERVAL_NS = 20_000_000_000L
        const val CORRECTION_SAMPLE_INTERVAL_NS = 2_000_000_000L
        const val SIGNIFICANT_DRIFT_MS = 100L
        const val CLOSE_TIMEOUT_MS = 2_000L
    }
}
