package com.darius.unison.transfer

import com.darius.unison.model.TrackId
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

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

}
