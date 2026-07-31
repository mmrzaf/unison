package com.darius.unison.room

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bounded single-consumer event loop used as the ownership boundary for mutable room-session state.
 * Critical producers use [submit] and therefore receive backpressure. Replaceable telemetry may use
 * [trySubmit] and explicitly handle a false result.
 *
 * [onDropped] is invoked for accepted events that cannot finish because the loop or its owning
 * scope is cancelled. Completion-bearing events must use it so shutdown never strands callers.
 */
class SerializedEventLoop<E>(
    scope: CoroutineScope,
    capacity: Int,
    private val handler: suspend (E) -> Unit,
    private val onFailure: (E, Throwable) -> Unit = { _, _ -> },
    private val onDropped: (E, CancellationException) -> Unit = { _, _ -> },
) : AutoCloseable {
    private class LoopContext(val owner: Any) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<LoopContext>
    }

    private val events =
        Channel<E>(
            capacity = capacity,
            onUndeliveredElement = { event ->
                runCatching { onDropped(event, CancellationException(CLOSED_MESSAGE)) }
            },
        )
    private val job: Job =
        scope.launch(LoopContext(this@SerializedEventLoop)) {
            for (event in events) {
                try {
                    handler(event)
                } catch (cancelled: CancellationException) {
                    runCatching { onDropped(event, cancelled) }
                    throw cancelled
                } catch (error: Throwable) {
                    onFailure(event, error)
                }
            }
        }

    suspend fun submit(event: E) {
        events.send(event)
    }

    fun trySubmit(event: E): Boolean = events.trySend(event).isSuccess

    suspend fun isCurrentContext(): Boolean = currentCoroutineContext()[LoopContext]?.owner === this

    val isActive: Boolean
        get() = job.isActive

    suspend fun closeAndJoin(timeoutMs: Long = DEFAULT_CLOSE_TIMEOUT_MS): Boolean {
        cancelLoop()
        return withTimeoutOrNull(timeoutMs) {
            job.join()
            true
        } ?: false
    }

    override fun close() {
        cancelLoop()
    }

    private fun cancelLoop() {
        val cause = CancellationException(CLOSED_MESSAGE)
        events.cancel(cause)
        job.cancel(cause)
    }

    private companion object {
        const val DEFAULT_CLOSE_TIMEOUT_MS = 2_000L
        const val CLOSED_MESSAGE = "Serialized event loop closed"
    }
}
