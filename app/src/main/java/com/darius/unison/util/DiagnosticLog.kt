package com.darius.unison.util

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Non-blocking, bounded diagnostic logger.
 *
 * Callers never perform filesystem work. When the bounded queue is saturated the oldest pending
 * diagnostic line is discarded so logging cannot make playback or room shutdown lag. Secrets are
 * sanitized before both Logcat and persistent output.
 */
class DiagnosticLog
internal constructor(
    private val file: File,
    private val writeToLogcat: Boolean = false,
) : AutoCloseable {
    constructor(
        context: Context
    ) : this(
        file = File(context.filesDir, "diagnostics/unison.log"),
        writeToLogcat = true,
    )

    init {
        file.parentFile?.mkdirs()
    }

    private val lock = Any()
    private val droppedLines = AtomicLong(0L)
    private val writer =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(MAX_PENDING_LINES),
            ThreadFactory { runnable ->
                Thread(runnable, "unison-diagnostic-writer").apply { isDaemon = true }
            },
            { runnable, executor ->
                if (!executor.isShutdown) {
                    if (executor.queue.poll() != null) {
                        droppedLines.incrementAndGet()
                    }
                    if (!executor.queue.offer(runnable)) {
                        droppedLines.incrementAndGet()
                    }
                }
            },
        )

    val pendingLineCount: Int
        get() = writer.queue.size

    val droppedLineCount: Long
        get() = droppedLines.get()

    fun i(tag: String, message: String) = write("I", tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        write("W", tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        write("E", tag, message, throwable)

    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val safeMessage = DiagnosticSanitizer.sanitize(message)
        val safeThrowable = throwable?.let(DiagnosticSanitizer::throwableSummary)
        try {
            writer.execute {
                val logcatLine = buildString {
                    append(safeMessage)
                    safeThrowable?.let { append(" :: ").append(it) }
                }
                if (writeToLogcat) {
                    when (level) {
                        "E" -> Log.e(tag, logcatLine)
                        "W" -> Log.w(tag, logcatLine)
                        else -> Log.i(tag, logcatLine)
                    }
                }

                val line = buildString {
                    append(Instant.now())
                        .append(' ')
                        .append(level)
                        .append('/')
                        .append(tag)
                        .append(' ')
                    append(safeMessage)
                    safeThrowable?.let { append(" :: ").append(it) }
                    append('\n')
                }
                synchronized(lock) {
                    rotateIfNeeded()
                    runCatching { file.appendText(line) }
                }
            }
        } catch (_: RejectedExecutionException) {
            // Shutdown or overload must never affect application work.
        }
    }

    suspend fun read(): String =
        withContext(Dispatchers.IO) {
            flushPending()
            synchronized(lock) { file.takeIf(File::exists)?.readText().orEmpty() }
        }

    private fun flushPending() {
        try {
            writer.submit {}.get(READ_FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            // Return the durable prefix rather than blocking diagnostics export indefinitely.
        }
    }

    override fun close() {
        writer.shutdown()
        runCatching { writer.awaitTermination(CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        if (!writer.isTerminated) writer.shutdownNow()
    }

    private fun rotateIfNeeded() {
        if (file.length() < MAX_LOG_BYTES) return
        val rotated = File(file.parentFile, "unison.log.1")
        rotated.delete()
        file.renameTo(rotated)
    }

    private companion object {
        const val MAX_PENDING_LINES = 1_024
        const val MAX_LOG_BYTES = 5 * 1024 * 1024L
        const val READ_FLUSH_TIMEOUT_MS = 2_000L
        const val CLOSE_TIMEOUT_MS = 2_000L
    }
}
