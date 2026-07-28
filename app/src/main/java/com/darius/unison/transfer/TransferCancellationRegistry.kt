package com.darius.unison.transfer

import com.darius.unison.model.TrackId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/** Owns the cancellation boundary so cancelling a transfer closes blocking I/O immediately. */
class TransferCancellationRegistry {
    private val jobs = ConcurrentHashMap<TrackId, Job>()
    private val sockets = ConcurrentHashMap<TrackId, Closeable>()

    fun registerJob(trackId: TrackId, job: Job) {
        jobs.put(trackId, job)?.cancel(CancellationException("Transfer replaced"))
        job.invokeOnCompletion { jobs.remove(trackId, job) }
    }

    fun attachSocket(trackId: TrackId, socket: Closeable) {
        sockets.put(trackId, socket)?.let(::closeQuietly)
    }

    fun detachSocket(trackId: TrackId, socket: Closeable) {
        sockets.remove(trackId, socket)
    }

    fun cancel(trackId: TrackId, reason: String = "Transfer cancelled") {
        sockets.remove(trackId)?.let(::closeQuietly)
        jobs.remove(trackId)?.cancel(CancellationException(reason))
    }

    fun cancelAll(reason: String = "Transfers cancelled") {
        sockets.values.forEach(::closeQuietly)
        sockets.clear()
        jobs.values.forEach { it.cancel(CancellationException(reason)) }
        jobs.clear()
    }

    fun hasActiveJob(trackId: TrackId): Boolean = jobs.containsKey(trackId)
    fun hasActiveSocket(trackId: TrackId): Boolean = sockets.containsKey(trackId)

    private fun closeQuietly(value: Closeable) {
        runCatching { value.close() }
    }
}
