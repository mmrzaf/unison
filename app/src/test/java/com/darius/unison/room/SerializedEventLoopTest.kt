package com.darius.unison.room

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializedEventLoopTest {
    @Test
    fun processesEventsSequentiallyInSubmissionOrder() = runBlocking {
        val processed = mutableListOf<Int>()
        val done = CompletableDeferred<Unit>()
        val loop =
            SerializedEventLoop<Int>(
                scope = this,
                capacity = 8,
                handler = { value ->
                    if (value == 1) delay(10)
                    processed += value
                    if (processed.size == 3) done.complete(Unit)
                },
            )
        loop.submit(1)
        loop.submit(2)
        loop.submit(3)
        done.await()
        assertEquals(listOf(1, 2, 3), processed)
        loop.close()
    }

    @Test
    fun boundedTrySubmitReportsPressure() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val loop =
            SerializedEventLoop<Int>(
                scope = this,
                capacity = 1,
                handler = {
                    started.complete(Unit)
                    gate.await()
                },
            )
        loop.submit(1)
        started.await()
        assertTrue(loop.trySubmit(2))
        assertFalse(loop.trySubmit(3))
        gate.complete(Unit)
        loop.close()
    }

    @Test
    fun closeAndJoinCompletesTheConsumerJob() = runBlocking {
        val loop =
            SerializedEventLoop<Int>(
                scope = this,
                capacity = 1,
                handler = {},
            )

        assertTrue(loop.closeAndJoin())
        assertFalse(loop.isActive)
    }

    @Test
    fun cancellationReportsCurrentAndQueuedAcceptedEvents() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CompletableDeferred<Unit>()
        val blocker = CompletableDeferred<Unit>()
        val dropped = CopyOnWriteArrayList<Int>()
        try {
            val loop =
                SerializedEventLoop<Int>(
                    scope = scope,
                    capacity = 2,
                    handler = { value ->
                        if (value == 1) {
                            started.complete(Unit)
                            blocker.await()
                        }
                    },
                    onDropped = { value, _ -> dropped += value },
                )
            loop.submit(1)
            started.await()
            loop.submit(2)

            assertTrue(loop.closeAndJoin())
            assertEquals(setOf(1, 2), dropped.toSet())
        } finally {
            blocker.complete(Unit)
            scope.cancel()
        }
    }

    @Test
    fun handlerCancellationWhileOwnerActiveReportsFailureAndContinues() = runBlocking {
        val failure = CompletableDeferred<Throwable>()
        val processedAfterFailure = CompletableDeferred<Unit>()
        val loop =
            SerializedEventLoop<Int>(
                scope = this,
                capacity = 2,
                handler = { value ->
                    if (value == 1) {
                        throw CancellationException("business-logic cancellation")
                    }
                    processedAfterFailure.complete(Unit)
                },
                onFailure = { _, error -> failure.complete(error) },
            )

        loop.submit(1)
        assertTrue(withTimeout(1_000) { failure.await() } is CancellationException)
        assertTrue(loop.isActive)

        loop.submit(2)
        withTimeout(1_000) { processedAfterFailure.await() }
        assertTrue(loop.isActive)
        assertTrue(loop.closeAndJoin())
    }

    @Test
    fun eventLocalTimeoutCancellationDoesNotKillOwnerLoop() = runBlocking {
        val failure = CompletableDeferred<Throwable>()
        val processedAfterFailure = CompletableDeferred<Unit>()
        val loop =
            SerializedEventLoop<Int>(
                scope = this,
                capacity = 2,
                handler = { value ->
                    if (value == 1) {
                        withTimeout(10) { delay(10_000) }
                    } else {
                        processedAfterFailure.complete(Unit)
                    }
                },
                onFailure = { _, error -> failure.complete(error) },
            )

        loop.submit(1)
        withTimeout(1_000) { failure.await() }
        assertTrue(loop.isActive)

        loop.submit(2)
        withTimeout(1_000) { processedAfterFailure.await() }
        assertTrue(loop.isActive)
        assertTrue(loop.closeAndJoin())
    }

    @Test
    fun reportsHandlerDurationAfterProcessing() = runBlocking {
        val handled = CompletableDeferred<Pair<Int, Long>>()
        val loop =
            SerializedEventLoop<Int>(
                scope = this,
                capacity = 1,
                handler = { delay(2) },
                onHandled = { value, durationNs -> handled.complete(value to durationNs) },
            )

        loop.submit(7)
        val (value, durationNs) = handled.await()
        assertEquals(7, value)
        assertTrue(durationNs > 0L)
        loop.close()
    }

}
