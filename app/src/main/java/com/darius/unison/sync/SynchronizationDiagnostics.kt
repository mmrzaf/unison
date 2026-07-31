package com.darius.unison.sync

import com.darius.unison.util.DiagnosticLog
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * Bounded telemetry collector. Producers never write files; a dedicated IO coroutine serializes
 * events to the normal diagnostic log and keeps a bounded export buffer.
 */
class SynchronizationDiagnostics(
    scope: CoroutineScope,
    private val log: DiagnosticLog,
    private val maxEntries: Int = 2_000,
) : AutoCloseable {
    private val events = ArrayDeque<SynchronizationEvent>()
    private val lock = Any()
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
                synchronized(lock) {
                    events.addLast(event)
                    while (events.size > maxEntries) events.removeFirst()
                }
                log.i(TAG, event.toCompactLine())
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
        synchronized(lock) { events.clear() }
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

    suspend fun exportJson(): String =
        withContext(Dispatchers.Default) {
            snapshot().joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") {
                it.toJson()
            }
        }

    suspend fun exportCsv(): String =
        withContext(Dispatchers.Default) {
            buildString {
                append(CSV_HEADER).append('\n')
                snapshot().forEach { append(it.toCsv()).append('\n') }
            }
        }

    fun snapshot(): List<SynchronizationEvent> = synchronized(lock) { events.toList() }

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

    private fun SynchronizationEvent.toCompactLine(): String = buildString {
        append("sync_tick")
        append(" item=").append(queueItemId?.take(8) ?: "none")
        append(" rawDriftMs=").append(rawDriftMs ?: "unknown")
        append(" filteredDriftMs=").append(filteredDriftMs ?: "unknown")
        append(" speed=").append(selectedSpeed)
        append(" baseline=").append(learnedBaselineSpeed)
        append(" clockRate=").append(clockRate)
        append(" clockRttMs=").append(clockRttMs ?: "unknown")
        append(" clockUncertaintyMs=").append(clockUncertaintyMs ?: "unknown")
        append(" clockState=").append(clockState)
        append(" syncState=").append(playbackSyncState)
        append(" action=").append(action)
        append(" reason=").append(actionReason)
        append(" route=").append(outputRoute)
    }

    private fun SynchronizationEvent.toJson(): String = buildString {
        append("  {")
        val fields =
            listOf(
                "timestampLocalNs" to timestampLocalNs,
                "timestampCoordinatorNs" to timestampCoordinatorNs,
                "deviceId" to deviceId,
                "deviceModel" to deviceModel,
                "androidVersion" to androidVersion,
                "outputRoute" to outputRoute,
                "roomIdHash" to roomIdHash,
                "coordinatorTerm" to coordinatorTerm,
                "queueItemId" to queueItemId,
                "canonicalPositionMs" to canonicalPositionMs,
                "sampledPlayerPositionMs" to sampledPlayerPositionMs,
                "sampleAgeMs" to sampleAgeMs,
                "rawDriftMs" to rawDriftMs,
                "filteredDriftMs" to filteredDriftMs,
                "selectedSpeed" to selectedSpeed,
                "learnedBaselineSpeed" to learnedBaselineSpeed,
                "clockOffsetNs" to clockOffsetNs,
                "clockRate" to clockRate,
                "clockRttMs" to clockRttMs,
                "clockUncertaintyMs" to clockUncertaintyMs,
                "clockState" to clockState,
                "playbackSyncState" to playbackSyncState,
                "action" to action,
                "actionReason" to actionReason,
                "hardSeekCount" to hardSeekCount,
                "buffering" to buffering,
            )
        fields.forEachIndexed { index, (name, value) ->
            if (index > 0) append(',')
            append('\n').append("    \"").append(name).append("\": ")
            when (value) {
                null -> append("null")
                is Number,
                is Boolean -> append(value)
                else -> append('"').append(jsonEscape(value.toString())).append('"')
            }
        }
        append("\n  }")
    }

    private fun SynchronizationEvent.toCsv(): String =
        listOf(
                timestampLocalNs,
                timestampCoordinatorNs,
                deviceId,
                deviceModel,
                androidVersion,
                outputRoute,
                roomIdHash,
                coordinatorTerm,
                queueItemId,
                canonicalPositionMs,
                sampledPlayerPositionMs,
                sampleAgeMs,
                rawDriftMs,
                filteredDriftMs,
                selectedSpeed,
                learnedBaselineSpeed,
                clockOffsetNs,
                clockRate,
                clockRttMs,
                clockUncertaintyMs,
                clockState,
                playbackSyncState,
                action,
                actionReason,
                hardSeekCount,
                buffering,
            )
            .joinToString(",") { csvEscape(it?.toString().orEmpty()) }

    private fun jsonEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    private fun csvEscape(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' }) return value
        val doubledQuotes = value.replace("\"", "\"\"")
        return "\"" + doubledQuotes + "\""
    }

    private companion object {
        const val TAG = "UnisonSync"
        const val ROUTINE_SAMPLE_INTERVAL_NS = 20_000_000_000L
        const val CORRECTION_SAMPLE_INTERVAL_NS = 2_000_000_000L
        const val SIGNIFICANT_DRIFT_MS = 100L
        const val CLOSE_TIMEOUT_MS = 2_000L
        const val CSV_HEADER =
            "timestampLocalNs,timestampCoordinatorNs,deviceId,deviceModel," +
                "androidVersion,outputRoute,roomIdHash,coordinatorTerm,queueItemId,canonicalPositionMs," +
                "sampledPlayerPositionMs,sampleAgeMs,rawDriftMs,filteredDriftMs,selectedSpeed," +
                "learnedBaselineSpeed,clockOffsetNs,clockRate,clockRttMs,clockUncertaintyMs," +
                "clockState,playbackSyncState,action,actionReason,hardSeekCount,buffering"
    }
}
