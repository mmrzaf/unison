package com.darius.unison.sync

import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

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
    private val maxEntries: Int = 20_000,
) : AutoCloseable {
    private val events = ArrayDeque<SynchronizationEvent>()
    private val lock = Any()
    private val channel = Channel<SynchronizationEvent>(
        capacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val writer: Job = scope.launch(Dispatchers.IO) {
        for (event in channel) {
            synchronized(lock) {
                events.addLast(event)
                while (events.size > maxEntries) events.removeFirst()
            }
            log.i(TAG, event.toCompactLine())
        }
    }

    fun record(event: SynchronizationEvent) {
        channel.trySend(event)
    }

    suspend fun exportJson(): String = withContext(Dispatchers.Default) {
        snapshot().joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") { it.toJson() }
    }

    suspend fun exportCsv(): String = withContext(Dispatchers.Default) {
        buildString {
            append(CSV_HEADER).append('\n')
            snapshot().forEach { append(it.toCsv()).append('\n') }
        }
    }

    fun snapshot(): List<SynchronizationEvent> = synchronized(lock) { events.toList() }

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
        val fields = listOf(
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
                is Number, is Boolean -> append(value)
                else -> append('"').append(jsonEscape(value.toString())).append('"')
            }
        }
        append("\n  }")
    }

    private fun SynchronizationEvent.toCsv(): String = listOf(
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
    ).joinToString(",") { csvEscape(it?.toString().orEmpty()) }

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun csvEscape(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' }) return value
        val doubledQuotes = value.replace("\"", "\"\"")
        return "\"" + doubledQuotes + "\""
    }

    private companion object {
        const val TAG = "UnisonSync"
        const val CSV_HEADER = "timestampLocalNs,timestampCoordinatorNs,deviceId,deviceModel," +
            "androidVersion,outputRoute,roomIdHash,coordinatorTerm,queueItemId,canonicalPositionMs," +
            "sampledPlayerPositionMs,sampleAgeMs,rawDriftMs,filteredDriftMs,selectedSpeed," +
            "learnedBaselineSpeed,clockOffsetNs,clockRate,clockRttMs,clockUncertaintyMs," +
            "clockState,playbackSyncState,action,actionReason,hardSeekCount,buffering"
    }
}
