package com.darius.unison.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Small real-Android filesystem abuse test; no device farm or custom network simulator required. */
@RunWith(AndroidJUnit4::class)
class ManagedFileStoreAndroidStressTest {
    @Test
    fun repeatedInterruptedResumeCommitsExactlyOneVerifiedFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = context.cacheDir.resolve("transfer-stress-${System.nanoTime()}").apply { mkdirs() }
        try {
            val store = ManagedFileStore(root)
            val bytes = ByteArray(1_000_000) { ((it * 31) % 251).toByte() }
            val trackId = store.hash(ByteArrayInputStream(bytes)).trackId
            var offset = 0
            val chunkSize = 41_003

            while (offset + chunkSize < bytes.size) {
                var interrupted = false
                try {
                    store.receivePartialAndHash(
                        trackId = trackId,
                        offset = offset.toLong(),
                        expectedSize = bytes.size.toLong(),
                        input = ByteArrayInputStream(bytes, offset, chunkSize),
                    )
                } catch (_: IllegalStateException) {
                    interrupted = true
                }
                assertTrue(interrupted)
                offset += chunkSize
                assertEquals(offset.toLong(), store.partialFile(trackId).length())
            }

            val completed =
                store.receivePartialAndHash(
                    trackId = trackId,
                    offset = offset.toLong(),
                    expectedSize = bytes.size.toLong(),
                    input = ByteArrayInputStream(bytes, offset, bytes.size - offset),
                )
            assertTrue(store.commitPartialWithDigest(trackId, bytes.size.toLong(), completed.sha256Hex))
            assertTrue(store.hasVerified(trackId, bytes.size.toLong()))
            assertTrue(store.finalFile(trackId).readBytes().contentEquals(bytes))
        } finally {
            root.deleteRecursively()
        }
    }
}
