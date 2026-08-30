package com.darius.unison.util

/** OpenTelemetry-aligned severity ranges without pulling a telemetry SDK into the APK. */
enum class DiagnosticSeverity(
    val severityText: String,
    val severityNumber: Int,
) {
    DEBUG("DEBUG", 5),
    INFO("INFO", 9),
    WARN("WARN", 13),
    ERROR("ERROR", 17),
}

enum class DiagnosticCategory(val wireName: String) {
    APP("app"),
    ROOM("room"),
    NETWORK("network"),
    DISCOVERY("discovery"),
    PLAYBACK("playback"),
    SYNC("sync"),
    TRANSFER("transfer"),
    STORAGE("storage"),
    SECURITY("security"),
}

data class DiagnosticError(
    val type: String,
    val message: String?,
)

/**
 * One immutable log event. The field model mirrors the stable OpenTelemetry Log Data Model:
 * timestamp, severity, body, event name, instrumentation scope and attributes.
 */
data class DiagnosticEvent(
    val sequence: Long,
    val timestamp: String,
    val observedTimestamp: String,
    val monotonicTimeNs: Long,
    val severity: DiagnosticSeverity,
    val eventName: String,
    val body: String?,
    val component: String,
    val category: DiagnosticCategory,
    val attributes: Map<String, Any?>,
    val error: DiagnosticError?,
) {
    val roomSessionId: String?
        get() = attributes[ROOM_SESSION_ID_ATTRIBUTE] as? String

    val roomIdHash: String?
        get() = attributes[ROOM_ID_HASH_ATTRIBUTE] as? String

    /**
     * A valid structured record sized for Android's Logcat transport. Persistent NDJSON keeps the
     * complete sanitized event; Logcat gets the same schema with bounded body/error strings and,
     * only when necessary, a priority-preserving attribute projection so a log entry is never
     * split into invalid JSON by Logcat.
     */
    fun toLogcatJsonLine(maxChars: Int = MAX_LOGCAT_RECORD_CHARS): String {
        val compactAttributes =
            LinkedHashMap<String, Any?>(attributes.size).apply {
                logcatPriorityKeys.forEach { key ->
                    attributes[key]?.let { put(key, compactLogcatValue(it)) }
                }
                attributes.forEach { (key, value) ->
                    if (!containsKey(key)) put(key, compactLogcatValue(value))
                }
            }
        var projected =
            copy(
                body = body?.take(MAX_LOGCAT_BODY_CHARS),
                attributes = compactAttributes,
                error = error?.copy(message = error.message?.take(MAX_LOGCAT_ERROR_CHARS)),
            )
        var line = projected.toJsonLine()
        if (line.length <= maxChars) return line

        val minimumKeys =
            logcatPriorityKeys.filterTo(linkedSetOf()) { compactAttributes.containsKey(it) }
        val mutableAttributes = LinkedHashMap(compactAttributes)
        val removableKeys = mutableAttributes.keys.filterNot(minimumKeys::contains).asReversed()
        for (key in removableKeys) {
            mutableAttributes.remove(key)
            projected = projected.copy(attributes = mutableAttributes)
            line = projected.toJsonLine()
            if (line.length <= maxChars) return line
        }

        projected = projected.copy(body = null, error = projected.error?.copy(message = null))
        line = projected.toJsonLine()
        if (line.length <= maxChars) return line

        // Priority attributes are deliberately tiny, but keep a final valid-JSON fallback rather
        // than ever handing Logcat an oversized record. The durable file still has the full event.
        return projected.copy(attributes = emptyMap(), body = null, error = null).toJsonLine()
    }

    private fun compactLogcatValue(value: Any?): Any? =
        if (value is String) value.take(MAX_LOGCAT_ATTRIBUTE_VALUE_CHARS) else value

    fun toJsonLine(): String = buildString(384) {
        append('{')
        appendJsonField("schemaVersion", SCHEMA_VERSION)
        append(',')
        appendJsonField("sequence", sequence)
        append(',')
        appendJsonField("timestamp", timestamp)
        append(',')
        appendJsonField("observedTimestamp", observedTimestamp)
        append(',')
        appendJsonField("monotonicTimeNs", monotonicTimeNs)
        append(',')
        appendJsonField("severityText", severity.severityText)
        append(',')
        appendJsonField("severityNumber", severity.severityNumber)
        append(',')
        appendJsonField("eventName", eventName)
        body?.let {
            append(',')
            appendJsonField("body", it)
        }
        append(',')
        append("\"resource\":{")
        appendJsonField("service.name", "unison")
        append('}')
        append(',')
        append("\"instrumentationScope\":{")
        appendJsonField("name", component)
        append('}')
        append(',')
        append("\"attributes\":{")
        val normalizedAttributes =
            linkedMapOf<String, Any?>("log.category" to category.wireName).apply {
                putAll(attributes.toSortedMap())
            }
        normalizedAttributes.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            appendJsonString(key)
            append(':')
            appendJsonValue(value)
        }
        append('}')
        error?.let { diagnosticError ->
            append(',')
            append("\"exception\":{")
            appendJsonField("type", diagnosticError.type)
            diagnosticError.message?.let {
                append(',')
                appendJsonField("message", it)
            }
            append('}')
        }
        append('}')
    }

    private fun StringBuilder.appendJsonField(name: String, value: Any?) {
        appendJsonString(name)
        append(':')
        appendJsonValue(value)
    }

    private fun StringBuilder.appendJsonValue(value: Any?) {
        when (value) {
            null -> append("null")
            is Boolean,
            is Byte,
            is Short,
            is Int,
            is Long,
            is Float,
            is Double -> append(value)
            else -> appendJsonString(value.toString())
        }
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val ROOM_SESSION_ID_ATTRIBUTE = "room.session_id"
        const val ROOM_ID_HASH_ATTRIBUTE = "room.id_hash"
        const val ROOM_ROLE_ATTRIBUTE = "room.role"
        private const val MAX_LOGCAT_RECORD_CHARS = 3_500
        private const val MAX_LOGCAT_BODY_CHARS = 256
        private const val MAX_LOGCAT_ERROR_CHARS = 256
        private const val MAX_LOGCAT_ATTRIBUTE_VALUE_CHARS = 128
        private val logcatPriorityKeys =
            listOf(
                ROOM_SESSION_ID_ATTRIBUTE,
                ROOM_ID_HASH_ATTRIBUTE,
                ROOM_ROLE_ATTRIBUTE,
                "command.id",
                "command.type",
                "command.source",
                "transport.action",
                "transport.phase",
                "queue.item_id",
                "track.id",
                "peer.id",
                "transfer.operation_id",
                "transfer.assignment_id",
                "transfer.phase",
                "transfer.reason",
                "playback.late_ms",
                "playback.arrival_late_ms",
                "playback.executor_late_ms",
                "playback.state",
                "sync.filtered_drift_ms",
                "sync.action",
                "sync.hard_seek_count",
                "log.dropped_count",
            )
    }
}
