package com.darius.unison.room

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializedEventLoopTest {
    @Test
    fun processesEventsSequentiallyInSubmissionOrder() = runBlocking {
        val processed = mutableListOf<Int>()
        val done = CompletableDeferred<Unit>()
        val loop = SerializedEventLoop<Int>(
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
        val loop = SerializedEventLoop<Int>(
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
}
