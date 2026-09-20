package com.darius.unison.room

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bounded single-consumer event loop used as the ownership boundary for mutable room-session state.
 * Critical producers use [submit] and therefore receive backpressure. Replaceable telemetry may use
 * [trySubmit] and explicitly handle a false result.
 *
 * [onDropped] is invoked for accepted events that cannot finish because the loop or its owning
 * scope is cancelled. Completion-bearing events must use it so shutdown never strands callers. A
 * [CancellationException] thrown by one handler while this loop's owner is still active is reported
 * through [onFailure] like any other event failure; only cancellation of the owner job itself is
 * allowed to terminate the persistent consumer.
 */
class SerializedEventLoop<E>(
    scope: CoroutineScope,
    capacity: Int,
    private val handler: suspend (E) -> Unit,
    private val onFailure: (E, Throwable, Timing) -> Unit = { _, _, _ -> },
    private val onDropped: (E, CancellationException) -> Unit = { _, _ -> },
    private val onHandled: (E, Long) -> Unit = { _, _ -> },
    private val onTiming: (E, Timing) -> Unit = { _, _ -> },
) : AutoCloseable {
    data class Timing(
        val submissionToStartNs: Long,
        val handlerDurationNs: Long,
    )

    private data class Queued<E>(
        val event: E,
        val submittedNs: Long,
    )

    private class LoopContext(val owner: Any) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<LoopContext>
    }

    private val events =
        Channel<Queued<E>>(
            capacity = capacity,
            onUndeliveredElement = { queued ->
                runCatching { onDropped(queued.event, CancellationException(CLOSED_MESSAGE)) }
            },
        )
    private val job: Job =
        scope.launch(LoopContext(this@SerializedEventLoop)) {
            for (queued in events) {
                val event = queued.event
                val startedNs = System.nanoTime()
                val submissionToStartNs = (startedNs - queued.submittedNs).coerceAtLeast(0L)
                fun timingAt(nowNs: Long) =
                    Timing(
                        submissionToStartNs = submissionToStartNs,
                        handlerDurationNs = (nowNs - startedNs).coerceAtLeast(0L),
                    )
                try {
                    handler(event)
                } catch (cancelled: CancellationException) {
                    if (!currentCoroutineContext().isActive) {
                        runCatching { onDropped(event, cancelled) }
                        throw cancelled
                    }
                    onFailure(event, cancelled, timingAt(System.nanoTime()))
                } catch (error: Throwable) {
                    onFailure(event, error, timingAt(System.nanoTime()))
                } finally {
                    val durationNs = (System.nanoTime() - startedNs).coerceAtLeast(0L)
                    runCatching { onHandled(event, durationNs) }
                    runCatching {
                        onTiming(
                            event,
                            Timing(
                                submissionToStartNs = submissionToStartNs,
                                handlerDurationNs = durationNs,
                            ),
                        )
                    }
                }
            }
        }

    suspend fun submit(event: E) {
        events.send(Queued(event, System.nanoTime()))
    }

    fun trySubmit(event: E): Boolean = events.trySend(Queued(event, System.nanoTime())).isSuccess

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
