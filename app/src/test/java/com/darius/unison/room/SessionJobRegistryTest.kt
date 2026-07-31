package com.darius.unison.room

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionJobRegistryTest {
    @Test
    fun repeatedRoomCyclesLeaveNoTrackedJobs() = runBlocking {
        val parent = SupervisorJob()
        val registry = SessionJobRegistry(CoroutineScope(parent + Dispatchers.Default))
        repeat(500) {
            val started = CompletableDeferred<Unit>()
            registry.launch {
                started.complete(Unit)
                awaitCancellation()
            }
            started.await()
            registry.advanceAndCancel(1_000)
            assertEquals(0, registry.activeJobCount)
        }
        parent.cancel()
    }

    @Test
    fun callbacksFromOlderGenerationAreRejected() = runBlocking {
        val parent = SupervisorJob()
        val registry = SessionJobRegistry(CoroutineScope(parent + Dispatchers.Default))
        val old = registry.generation
        registry.advanceAndCancel(1_000)
        assertFalse(registry.isCurrent(old))
        assertTrue(registry.isCurrent(old + 1))
        parent.cancel()
    }

    @Test
    fun timedOutCancellationRemainsTrackedUntilJobActuallyFinishes() = runBlocking {
        val parent = SupervisorJob()
        val registry = SessionJobRegistry(CoroutineScope(parent + Dispatchers.Default))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = registry.launch {
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
        }
        started.await()

        registry.advanceAndCancel(timeoutMs = 1)

        assertEquals(1, registry.activeJobCount)
        release.complete(Unit)
        job.join()
        assertEquals(0, registry.activeJobCount)
        parent.cancel()
    }
}
