package com.darius.unison.network

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serializes platform service resolutions so at most one is outstanding.
 *
 * Android 11–13 allow a single in-flight `resolveService` per `NsdManager` client and reject the
 * next with `FAILURE_ALREADY_ACTIVE`. The slot is therefore released only by the platform's own
 * completion callback, never by a local timeout: starting another resolution early would only fail
 * and drop that room. The queue is device-scoped for the same reason: a resolution started during
 * an earlier scan keeps the slot until the platform reports it, even after that scan ended.
 *
 * [start] receives a completion callback that must be invoked exactly when the platform finishes
 * (success or failure). Extra invocations are ignored.
 */
internal class SerialResolveQueue<T>(
    private val maxPending: Int,
    private val keyOf: (T) -> String,
    private val start: (item: T, complete: () -> Unit) -> Unit,
) {
    private val lock = Any()
    private val pending = ArrayDeque<T>()
    private var inFlightKey: String? = null

    /** Queues [item] unless the same key is already pending or in flight, or the queue is full. */
    fun enqueue(item: T) {
        val key = keyOf(item)
        synchronized(lock) {
            if (key == inFlightKey || pending.any { keyOf(it) == key }) return
            if (pending.size >= maxPending) return
            pending.addLast(item)
        }
        startNextIfIdle()
    }

    /** Drops a pending (not yet started) resolution. An in-flight one cannot be withdrawn. */
    fun remove(key: String) {
        synchronized(lock) { pending.removeAll { keyOf(it) == key } }
    }

    /** Drops every pending resolution. The in-flight one, if any, still owns the slot. */
    fun clearPending() {
        synchronized(lock) { pending.clear() }
    }

    internal val pendingCount: Int
        get() = synchronized(lock) { pending.size }

    internal val inFlight: String?
        get() = synchronized(lock) { inFlightKey }

    private fun startNextIfIdle() {
        val next =
            synchronized(lock) {
                if (inFlightKey != null || pending.isEmpty()) return
                pending.removeFirst().also { inFlightKey = keyOf(it) }
            }
        val completed = AtomicBoolean(false)
        start(next) {
            if (completed.compareAndSet(false, true)) {
                synchronized(lock) { inFlightKey = null }
                startNextIfIdle()
            }
        }
    }
}
