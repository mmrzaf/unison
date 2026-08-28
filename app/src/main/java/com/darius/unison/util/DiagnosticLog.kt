package com.darius.unison.util

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Non-blocking, bounded, structured application logger.
 *
 * Persistence is newline-delimited JSON so every durable line is independently parseable. The
 * logical record shape follows OpenTelemetry's stable log data model without shipping a telemetry
 * SDK or network exporter. Logging is deliberately lossy under overload: old pending diagnostics
 * are discarded before application work is allowed to block.
 */
class DiagnosticLog
internal constructor(
    private val file: File,
    private val writeToLogcat: Boolean = false,
) : AutoCloseable {
    constructor(
        context: Context
    ) : this(
        file = File(context.filesDir, "diagnostics/unison.ndjson"),
        writeToLogcat = true,
    )

    private data class RoomContext(
        val sessionId: String,
        val roomIdHash: String,
        val role: String,
    )

    init {
        file.parentFile?.mkdirs()
        // The pre-1.0 text logger is intentionally not carried forward. Structured NDJSON is the
        // only durable format from this release onward.
        File(file.parentFile, "unison.log").delete()
        File(file.parentFile, "unison.log.1").delete()
    }

    private val lock = Any()
    private val droppedEvents = AtomicLong(0L)
    private val sequence = AtomicLong(0L)
    private val roomContext = AtomicReference<RoomContext?>(null)
    private val recentEvents = ArrayDeque<DiagnosticEvent>(MAX_RECENT_EVENTS)
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()
    private var bufferedWriter: BufferedWriter? = null
    private var currentFileBytes: Long = file.length()
    private var unflushedEvents = 0
    private var lastFlushMonotonicNs = System.nanoTime()
    private var lastUiRevisionMonotonicNs = Long.MIN_VALUE

    private val writer =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(MAX_PENDING_EVENTS),
            ThreadFactory { runnable ->
                Thread(runnable, "unison-diagnostic-writer").apply { isDaemon = true }
            },
            { runnable, executor ->
                if (!executor.isShutdown) {
                    if (executor.queue.poll() != null) droppedEvents.incrementAndGet()
                    if (!executor.queue.offer(runnable)) droppedEvents.incrementAndGet()
                }
            },
        )

    val pendingEventCount: Int
        get() = writer.queue.size

    val droppedEventCount: Long
        get() = droppedEvents.get()

    fun scoped(component: String, category: DiagnosticCategory): DiagnosticLogger =
        DiagnosticLogger(this, component.take(MAX_COMPONENT_CHARS), category)

    fun debug(
        component: String,
        category: DiagnosticCategory,
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = emit(DiagnosticSeverity.DEBUG, component, category, eventName, body, attributes, throwable)

    fun info(
        component: String,
        category: DiagnosticCategory,
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = emit(DiagnosticSeverity.INFO, component, category, eventName, body, attributes, throwable)

    fun warn(
        component: String,
        category: DiagnosticCategory,
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = emit(DiagnosticSeverity.WARN, component, category, eventName, body, attributes, throwable)

    fun error(
        component: String,
        category: DiagnosticCategory,
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = emit(DiagnosticSeverity.ERROR, component, category, eventName, body, attributes, throwable)

    /** Begins a local diagnostic room session. Raw room identifiers are never persisted. */
    fun beginRoom(roomId: String, role: String): String {
        val context =
            RoomContext(
                sessionId = UUID.randomUUID().toString(),
                roomIdHash = hashIdentifier(roomId),
                role = DiagnosticSanitizer.sanitizeAttribute(role),
            )
        roomContext.set(context)
        return context.sessionId
    }

    fun updateRoomRole(role: String) {
        roomContext.updateAndGet { current ->
            current?.copy(role = DiagnosticSanitizer.sanitizeAttribute(role))
        }
    }

    fun endRoom(sessionId: String? = roomContext.get()?.sessionId) {
        roomContext.updateAndGet { current ->
            if (sessionId == null || current?.sessionId == sessionId) null else current
        }
    }

    fun currentRoomSessionId(): String? = roomContext.get()?.sessionId

    fun snapshot(roomSessionId: String? = null): List<DiagnosticEvent> =
        synchronized(lock) {
            if (roomSessionId == null) {
                recentEvents.toList()
            } else {
                recentEvents.filter { it.roomSessionId == roomSessionId }
            }
        }

    /** Removes the active room's visible diagnostics without affecting other application events. */
    fun clearRoom(roomSessionId: String) {
        synchronized(lock) {
            recentEvents.removeAll { it.roomSessionId == roomSessionId }
        }
        _revision.value = _revision.value + 1L
    }

    internal fun emit(
        severity: DiagnosticSeverity,
        component: String,
        category: DiagnosticCategory,
        eventName: String,
        body: String?,
        attributes: Map<String, Any?>,
        throwable: Throwable?,
    ) {
        val requestedEventName = eventName.trim().take(MAX_EVENT_NAME_CHARS)
        val validEventName = EVENT_NAME.matches(requestedEventName)
        val safeEventName =
            if (validEventName) requestedEventName else INVALID_EVENT_NAME
        val safeBody = body?.let(DiagnosticSanitizer::sanitize)?.takeIf(String::isNotBlank)
        val room = roomContext.get()
        val safeAttributes =
            sanitizeAttributes(
                if (validEventName) {
                    attributes
                } else {
                    attributes +
                        ("diagnostic.original_event_name" to
                            DiagnosticSanitizer.sanitizeAttribute(requestedEventName))
                },
                room,
            )
        val error =
            throwable?.let {
                DiagnosticError(
                    type = (it::class.qualifiedName ?: it::class.simpleName ?: "Throwable")
                        .take(MAX_ERROR_TYPE_CHARS),
                    message =
                        DiagnosticSanitizer.sanitize(it.message.orEmpty())
                            .take(MAX_ERROR_MESSAGE_CHARS)
                            .ifBlank { null },
                )
            }
        val now = Instant.now().toString()
        val event =
            DiagnosticEvent(
                sequence = sequence.incrementAndGet(),
                timestamp = now,
                observedTimestamp = now,
                monotonicTimeNs = monotonicNowNs(),
                severity = severity,
                eventName = safeEventName,
                body = safeBody,
                component = DiagnosticSanitizer.sanitizeAttribute(component).take(MAX_COMPONENT_CHARS),
                category = category,
                attributes = safeAttributes,
                error = error,
            )
        try {
            writer.execute { persist(event) }
        } catch (_: RejectedExecutionException) {
            droppedEvents.incrementAndGet()
        }
    }

    private fun persist(queuedEvent: DiagnosticEvent) {
        val event = queuedEvent.copy(observedTimestamp = Instant.now().toString())
        if (writeToLogcat) {
            val line = event.toLogcatJsonLine()
            when (event.severity) {
                DiagnosticSeverity.DEBUG -> Log.d(LOGCAT_TAG, line)
                DiagnosticSeverity.INFO -> Log.i(LOGCAT_TAG, line)
                DiagnosticSeverity.WARN -> Log.w(LOGCAT_TAG, line)
                DiagnosticSeverity.ERROR -> Log.e(LOGCAT_TAG, line)
            }
        }

        val serialized = event.toJsonLine() + '\n'
        val encodedBytes = serialized.toByteArray(Charsets.UTF_8).size.toLong()
        synchronized(lock) {
            recentEvents.addLast(event)
            while (recentEvents.size > MAX_RECENT_EVENTS) recentEvents.removeFirst()
            runCatching {
                    if (currentFileBytes > 0L && currentFileBytes + encodedBytes > MAX_LOG_BYTES) {
                        rotateNow()
                    }
                    writerForCurrentFile().write(serialized)
                    currentFileBytes += encodedBytes
                    unflushedEvents++
                    val nowNs = System.nanoTime()
                    if (
                        event.severity.severityNumber >= DiagnosticSeverity.WARN.severityNumber ||
                            unflushedEvents >= FLUSH_EVENT_BATCH ||
                            nowNs - lastFlushMonotonicNs >= FLUSH_INTERVAL_NS
                    ) {
                        flushWriter(nowNs)
                    }
                }
                .onFailure {
                    droppedEvents.incrementAndGet()
                    closeWriter()
                    currentFileBytes = file.length()
                }
        }
        val revisionNowNs = System.nanoTime()
        if (
            event.severity.severityNumber >= DiagnosticSeverity.WARN.severityNumber ||
                lastUiRevisionMonotonicNs == Long.MIN_VALUE ||
                revisionNowNs - lastUiRevisionMonotonicNs >= UI_REVISION_INTERVAL_NS
        ) {
            lastUiRevisionMonotonicNs = revisionNowNs
            _revision.value = event.sequence
        }
    }

    suspend fun readRaw(): String =
        withContext(Dispatchers.IO) {
            flushPending()
            synchronized(lock) {
                buildString {
                    rotatedFilesOldestFirst().forEach { rotated ->
                        if (rotated.exists()) append(rotated.readText())
                    }
                    if (file.exists()) append(file.readText())
                }
            }
        }

    private fun monotonicNowNs(): Long =
        runCatching { SystemClock.elapsedRealtimeNanos() }.getOrElse { System.nanoTime() }

    private fun sanitizeAttributes(
        attributes: Map<String, Any?>,
        room: RoomContext?,
    ): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>(attributes.size + 3)
        attributes.entries.take(MAX_ATTRIBUTES).forEach { (key, value) ->
            if (value == null) return@forEach
            val safeKey = key.trim().take(MAX_ATTRIBUTE_KEY_CHARS)
            if (!ATTRIBUTE_KEY.matches(safeKey)) return@forEach
            result[safeKey] =
                if (SENSITIVE_ATTRIBUTE_KEY.containsMatchIn(safeKey)) {
                    "<redacted>"
                } else {
                    sanitizeAttributeValue(value)
                }
        }
        room?.let {
            result[DiagnosticEvent.ROOM_SESSION_ID_ATTRIBUTE] = it.sessionId
            result[DiagnosticEvent.ROOM_ID_HASH_ATTRIBUTE] = it.roomIdHash
            result[DiagnosticEvent.ROOM_ROLE_ATTRIBUTE] = it.role
        }
        return result
    }

    private fun sanitizeAttributeValue(value: Any?): Any? =
        when (value) {
            null,
            is Boolean,
            is Byte,
            is Short,
            is Int,
            is Long -> value
            is Float -> value.takeIf(Float::isFinite)?.toDouble()
            is Double -> value.takeIf(Double::isFinite)
            else -> DiagnosticSanitizer.sanitizeAttribute(value.toString())
        }

    private fun flushPending() {
        try {
            writer.submit {
                synchronized(lock) { flushWriter() }
            }.get(READ_FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            // Diagnostics are best-effort; never block application teardown/export indefinitely.
        }
    }

    override fun close() {
        writer.shutdown()
        runCatching { writer.awaitTermination(CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        if (!writer.isTerminated) writer.shutdownNow()
        synchronized(lock) { closeWriter() }
    }

    private fun writerForCurrentFile(): BufferedWriter =
        bufferedWriter
            ?: FileOutputStream(file, true)
                .bufferedWriter(Charsets.UTF_8)
                .also { bufferedWriter = it }

    private fun flushWriter(nowNs: Long = System.nanoTime()) {
        bufferedWriter?.flush()
        unflushedEvents = 0
        lastFlushMonotonicNs = nowNs
    }

    private fun closeWriter() {
        runCatching { bufferedWriter?.flush() }
        runCatching { bufferedWriter?.close() }
        bufferedWriter = null
        unflushedEvents = 0
    }

    private fun rotateNow() {
        closeWriter()
        for (index in MAX_ROTATED_FILES downTo 1) {
            val source = if (index == 1) file else rotatedFile(index - 1)
            val destination = rotatedFile(index)
            if (destination.exists()) destination.delete()
            if (source.exists()) source.renameTo(destination)
        }
        currentFileBytes = 0L
        lastFlushMonotonicNs = System.nanoTime()
    }

    private fun rotatedFilesOldestFirst(): List<File> =
        (MAX_ROTATED_FILES downTo 1).map(::rotatedFile)

    private fun rotatedFile(index: Int): File = File(file.parentFile, "${file.name}.$index")

    private fun hashIdentifier(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    companion object {
        private const val LOGCAT_TAG = "Unison"
        private const val INVALID_EVENT_NAME = "diagnostic.invalid_event_name"
        private const val MAX_PENDING_EVENTS = 1_024
        private const val MAX_RECENT_EVENTS = 5_000
        private const val MAX_LOG_BYTES = 2 * 1024 * 1024L
        private const val MAX_ROTATED_FILES = 2
        private const val FLUSH_EVENT_BATCH = 32
        private const val FLUSH_INTERVAL_NS = 1_000_000_000L
        private const val UI_REVISION_INTERVAL_NS = 250_000_000L
        private const val READ_FLUSH_TIMEOUT_MS = 2_000L
        private const val CLOSE_TIMEOUT_MS = 2_000L
        private const val MAX_COMPONENT_CHARS = 64
        private const val MAX_EVENT_NAME_CHARS = 96
        private const val MAX_ATTRIBUTES = 32
        private const val MAX_ATTRIBUTE_KEY_CHARS = 64
        private const val MAX_ERROR_TYPE_CHARS = 160
        private const val MAX_ERROR_MESSAGE_CHARS = 1_024
        private val EVENT_NAME = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")
        private val ATTRIBUTE_KEY = Regex("[a-z][a-z0-9_.-]*")
        private val SENSITIVE_ATTRIBUTE_KEY =
            Regex("(?i)(secret|token|passphrase|pin|password|credential|authorization|proof|key_material)")
    }
}

class DiagnosticLogger internal constructor(
    private val sink: DiagnosticLog,
    private val component: String,
    private val category: DiagnosticCategory,
) {
    fun debug(
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = emit(DiagnosticSeverity.DEBUG, eventName, body, attributes, throwable)

    fun info(
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = emit(DiagnosticSeverity.INFO, eventName, body, attributes, throwable)

    fun warn(
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = emit(DiagnosticSeverity.WARN, eventName, body, attributes, throwable)

    fun error(
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = emit(DiagnosticSeverity.ERROR, eventName, body, attributes, throwable)

    private fun emit(
        severity: DiagnosticSeverity,
        eventName: String,
        body: String?,
        attributes: Map<String, Any?>,
        throwable: Throwable?,
    ) {
        sink.emit(severity, component, category, eventName, body, attributes, throwable)
    }
}
