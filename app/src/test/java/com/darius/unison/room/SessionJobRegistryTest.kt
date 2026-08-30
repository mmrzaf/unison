package com.darius.unison.room

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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
    fun roomSessionProvenanceRequiresBothRoomAndGeneration() {
        val current = RoomSessionProvenance(roomId = "room-a", generation = 7L)

        assertEquals(current, RoomSessionProvenance(roomId = "room-a", generation = 7L))
        assertFalse(current == RoomSessionProvenance(roomId = "room-b", generation = 7L))
        assertFalse(current == RoomSessionProvenance(roomId = "room-a", generation = 8L))
    }

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
    fun runIfCurrentMutatesOnlyCurrentGeneration() = runBlocking {
        val parent = SupervisorJob()
        val registry = SessionJobRegistry(CoroutineScope(parent + Dispatchers.Default))
        val generation = registry.generation
        var mutations = 0

        assertTrue(registry.runIfCurrent(generation) { mutations += 1 })
        assertEquals(1, mutations)

        registry.advanceAndCancel(1_000)

        assertFalse(registry.runIfCurrent(generation) { mutations += 1 })
        assertEquals(1, mutations)
        parent.cancel()
    }

    @Test
    fun generationCannotAdvanceHalfwayThroughGuardedMutation() {
        val parent = SupervisorJob()
        val registry = SessionJobRegistry(CoroutineScope(parent + Dispatchers.Default))
        val generation = registry.generation
        val mutationEntered = CountDownLatch(1)
        val allowMutationToFinish = CountDownLatch(1)
        val advanceStarted = CountDownLatch(1)
        val advanceFinished = CountDownLatch(1)
        val threadFailure = AtomicReference<Throwable?>(null)

        val mutationThread = Thread {
            try {
                assertTrue(
                    registry.runIfCurrent(generation) {
                        mutationEntered.countDown()
                        assertTrue(allowMutationToFinish.await(1, TimeUnit.SECONDS))
                        assertEquals(generation, registry.generation)
                    }
                )
            } catch (error: Throwable) {
                threadFailure.compareAndSet(null, error)
            }
        }
        val advanceThread = Thread {
            try {
                advanceStarted.countDown()
                registry.advanceAndCancelNow()
                advanceFinished.countDown()
            } catch (error: Throwable) {
                threadFailure.compareAndSet(null, error)
            }
        }

        mutationThread.start()
        assertTrue(mutationEntered.await(1, TimeUnit.SECONDS))
        advanceThread.start()
        assertTrue(advanceStarted.await(1, TimeUnit.SECONDS))
        assertFalse(advanceFinished.await(50, TimeUnit.MILLISECONDS))
        assertEquals(generation, registry.generation)

        allowMutationToFinish.countDown()
        mutationThread.join(1_000)
        advanceThread.join(1_000)

        assertFalse(mutationThread.isAlive)
        assertFalse(advanceThread.isAlive)
        threadFailure.get()?.let { throw it }
        assertEquals(generation + 1, registry.generation)
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
