package com.darius.unison.storage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.io.path.createTempDirectory

class ManagedFileStoreTest {
    @Test
    fun copyAndHashCommitsReadableContent() = runBlocking {
        val root = createTempDirectory("unison-store-").toFile()
        try {
            val store = ManagedFileStore(root)
            val bytes = "unison audio data".repeat(4096).encodeToByteArray()
            val result = store.copyAndHash(ByteArrayInputStream(bytes))

            assertTrue(result.file.isFile)
            assertEquals(bytes.size.toLong(), result.sizeBytes)
            assertTrue(store.hasVerified(result.trackId, bytes.size.toLong()))
            assertTrue(result.file.readBytes().contentEquals(bytes))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun duplicateContentReusesContentAddressedFile() = runBlocking {
        val root = createTempDirectory("unison-store-").toFile()
        try {
            val store = ManagedFileStore(root)
            val bytes = ByteArray(512 * 1024) { (it % 251).toByte() }
            val first = store.copyAndHash(ByteArrayInputStream(bytes))
            val second = store.copyAndHash(ByteArrayInputStream(bytes))

            assertEquals(first.trackId, second.trackId)
            assertEquals(first.file.canonicalPath, second.file.canonicalPath)
            assertEquals(bytes.size.toLong(), second.file.length())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun peerTransferUsesTheSameSafeWriteLifecycleAndCanResume() = runBlocking {
        val root = createTempDirectory("unison-store-").toFile()
        try {
            val store = ManagedFileStore(root)
            val bytes = ByteArray(900_000) { (it % 239).toByte() }
            val expected = store.hash(ByteArrayInputStream(bytes)).trackId
            val split = 310_000

            store.receivePartial(
                trackId = expected,
                offset = 0,
                expectedSize = split.toLong(),
                input = ByteArrayInputStream(bytes, 0, split),
            )
            val partial = store.partialFile(expected)
            assertEquals(split.toLong(), partial.length())

            store.receivePartial(
                trackId = expected,
                offset = split.toLong(),
                expectedSize = bytes.size.toLong(),
                input = ByteArrayInputStream(bytes, split, bytes.size - split),
            )
            assertTrue(store.verifyPartial(expected, bytes.size.toLong()))
            assertTrue(store.finalFile(expected).readBytes().contentEquals(bytes))
        } finally {
            root.deleteRecursively()
        }
    }
}
