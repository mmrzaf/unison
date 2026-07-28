package com.darius.unison.room

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Bounded single-consumer event loop used as the ownership boundary for mutable room-session state.
 * Critical producers use [submit] and therefore receive backpressure. Replaceable telemetry may use
 * [trySubmit] and explicitly handle a false result.
 */
class SerializedEventLoop<E>(
    scope: CoroutineScope,
    capacity: Int,
    private val handler: suspend (E) -> Unit,
    private val onFailure: (E, Throwable) -> Unit = { _, _ -> },
) : AutoCloseable {
    private class LoopContext(val owner: Any) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<LoopContext>
    }

    private val events = Channel<E>(capacity)
    private val job: Job = scope.launch(LoopContext(this@SerializedEventLoop)) {
        for (event in events) {
            try {
                handler(event)
            } catch (cancelled: CancellationException) {
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

    suspend fun isCurrentContext(): Boolean =
        currentCoroutineContext()[LoopContext]?.owner === this

    val isActive: Boolean get() = job.isActive

    override fun close() {
        events.close()
        job.cancel()
    }
}
