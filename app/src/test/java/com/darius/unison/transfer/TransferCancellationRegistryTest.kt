package com.darius.unison.transfer

import com.darius.unison.model.TrackId
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferCancellationRegistryTest {
    @Test
    fun cancelClosesSocketAndCancelsJob() {
        val registry = TransferCancellationRegistry()
        val trackId = TrackId("a".repeat(64))
        val job = Job()
        val closed = AtomicBoolean(false)
        val socket = Closeable { closed.set(true) }

        registry.registerJob(trackId, job)
        registry.attachSocket(trackId, socket)
        registry.cancel(trackId)

        assertTrue(closed.get())
        assertTrue(job.isCancelled)
        assertFalse(registry.hasActiveJob(trackId))
        assertFalse(registry.hasActiveSocket(trackId))
    }

    @Test
    fun detachingOldSocketCannotRemoveReplacement() {
        val registry = TransferCancellationRegistry()
        val trackId = TrackId("b".repeat(64))
        val oldClosed = AtomicBoolean(false)
        val newClosed = AtomicBoolean(false)
        val oldSocket = Closeable { oldClosed.set(true) }
        val newSocket = Closeable { newClosed.set(true) }

        registry.attachSocket(trackId, oldSocket)
        registry.attachSocket(trackId, newSocket)
        registry.detachSocket(trackId, oldSocket)

        assertTrue(oldClosed.get())
        assertTrue(registry.hasActiveSocket(trackId))
        registry.cancel(trackId)
        assertTrue(newClosed.get())
    }

    @Test
    fun cancelAllClosesEverySocketAndCancelsEveryJob() {
        val registry = TransferCancellationRegistry()
        val firstTrack = TrackId("c".repeat(64))
        val secondTrack = TrackId("d".repeat(64))
        val firstJob = Job()
        val secondJob = Job()
        val firstClosed = AtomicBoolean(false)
        val secondClosed = AtomicBoolean(false)

        registry.registerJob(firstTrack, firstJob)
        registry.registerJob(secondTrack, secondJob)
        registry.attachSocket(firstTrack, Closeable { firstClosed.set(true) })
        registry.attachSocket(secondTrack, Closeable { secondClosed.set(true) })

        registry.cancelAll()

        assertTrue(firstClosed.get())
        assertTrue(secondClosed.get())
        assertTrue(firstJob.isCancelled)
        assertTrue(secondJob.isCancelled)
        assertFalse(registry.hasActiveJob(firstTrack))
        assertFalse(registry.hasActiveSocket(secondTrack))
    }

    @Test
    fun timedOutTransferJobRemainsTrackedUntilItActuallyFinishes() = runBlocking {
        val parent = SupervisorJob()
        val registry = TransferCancellationRegistry()
        val trackId = TrackId("e".repeat(64))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job =
            CoroutineScope(parent + Dispatchers.Default).launch {
                started.complete(Unit)
                withContext(NonCancellable) { release.await() }
            }
        registry.registerJob(trackId, job)
        started.await()

        assertFalse(registry.cancelAllAndJoin(timeoutMs = 1))
        assertTrue(registry.hasActiveJob(trackId))
        assertTrue(registry.activeCount > 0)

        release.complete(Unit)
        job.join()
        assertFalse(registry.hasActiveJob(trackId))
        parent.cancel()
    }

    @Test
    fun sameTrackOperationsNeverOverlap() = runBlocking {
        val registry = TransferCancellationRegistry()
        val trackId = TrackId("f".repeat(64))
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val first = scope.launch {
                registry.withTrackOperation(trackId) {
                    synchronized(order) { order += "first-start" }
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                    synchronized(order) { order += "first-end" }
                }
            }
            firstEntered.await()
            val second = scope.launch {
                registry.withTrackOperation(trackId) {
                    synchronized(order) { order += "second" }
                }
            }
            delay(50)
            assertEquals(listOf("first-start"), synchronized(order) { order.toList() })

            releaseFirst.complete(Unit)
            first.join()
            second.join()
            assertEquals(
                listOf("first-start", "first-end", "second"),
                synchronized(order) { order.toList() },
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun activeCountRepresentsLogicalTransfersNotAttachedSockets() {
        val registry = TransferCancellationRegistry()
        val trackId = TrackId("1".repeat(64))
        val job = Job()
        registry.registerJob(trackId, job)
        registry.attachSocket(trackId, Closeable {})

        assertEquals(1, registry.activeCount)
        assertEquals(1, registry.activeResourceCount)
        registry.cancel(trackId)
    }
}
