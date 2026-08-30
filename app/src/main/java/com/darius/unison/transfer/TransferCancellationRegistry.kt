package com.darius.unison.transfer

import com.darius.unison.model.TrackId
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Owns the cancellation boundary so cancelling a transfer closes blocking I/O immediately. */
class TransferCancellationRegistry {
    private val jobs = ConcurrentHashMap<TrackId, Job>()
    private val sockets = ConcurrentHashMap<TrackId, Closeable>()
    private val operationLocks = Array(OPERATION_LOCK_STRIPES) { Mutex() }

    /** Number of logical transfers, not jobs plus their attached I/O resources. */
    val activeCount: Int
        get() = jobs.values.count { !it.isCompleted }

    /** Resource count is exposed separately for shutdown diagnostics. */
    val activeResourceCount: Int
        get() = sockets.size

    /**
     * Serializes complete download lifecycles for the same track as a defensive file-store guard.
     */
    suspend fun <T> withTrackOperation(trackId: TrackId, block: suspend () -> T): T =
        operationLocks[operationIndex(trackId)].withLock { block() }

    /**
     * Registers one logical download per track. Duplicate assignments never replace healthy work.
     */
    fun tryRegisterJob(trackId: TrackId, job: Job): Boolean {
        while (true) {
            val existing = jobs[trackId]
            if (existing != null) {
                if (!existing.isCompleted) return false
                jobs.remove(trackId, existing)
                continue
            }
            if (jobs.putIfAbsent(trackId, job) == null) {
                job.invokeOnCompletion { jobs.remove(trackId, job) }
                return true
            }
        }
    }

    fun attachSocket(trackId: TrackId, socket: Closeable) {
        sockets.put(trackId, socket)?.let(::closeQuietly)
    }

    fun detachSocket(trackId: TrackId, socket: Closeable) {
        sockets.remove(trackId, socket)
    }

    fun cancel(trackId: TrackId, reason: String = "Transfer cancelled") {
        jobs[trackId]?.cancel(CancellationException(reason))
        sockets.remove(trackId)?.let(::closeQuietly)
    }

    fun cancelAll(reason: String = "Transfers cancelled") {
        jobs.values.forEach { it.cancel(CancellationException(reason)) }
        sockets.values.forEach(::closeQuietly)
        sockets.clear()
        jobs.entries.removeIf { it.value.isCompleted }
    }

    suspend fun cancelAllAndJoin(
        reason: String = "Transfers cancelled",
        timeoutMs: Long,
    ): Boolean {
        cancelAll(reason)
        val closing = jobs.values.toList()
        val completed =
            withTimeoutOrNull(timeoutMs) {
                closing.forEach { it.join() }
                true
            } ?: false
        jobs.entries.removeIf { it.value.isCompleted }
        return completed && jobs.values.none { !it.isCompleted }
    }

    fun hasActiveJob(trackId: TrackId): Boolean = jobs[trackId]?.isCompleted == false

    fun hasActiveSocket(trackId: TrackId): Boolean = sockets.containsKey(trackId)

    private fun operationIndex(trackId: TrackId): Int =
        (trackId.value.hashCode() and Int.MAX_VALUE) % operationLocks.size

    private fun closeQuietly(value: Closeable) {
        runCatching { value.close() }
    }

    private companion object {
        const val OPERATION_LOCK_STRIPES = 32
    }
}
