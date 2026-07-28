package com.darius.unison.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    fun concurrentDuplicateImportsCommitOneCompleteFile() = runBlocking {
        val root = createTempDirectory("unison-store-").toFile()
        try {
            val store = ManagedFileStore(root)
            val bytes = ByteArray(768 * 1024) { (it % 197).toByte() }
            val results = List(8) {
                async(Dispatchers.IO) {
                    store.copyAndHash(ByteArrayInputStream(bytes))
                }
            }.awaitAll()

            assertEquals(1, results.map { it.trackId }.distinct().size)
            assertTrue(results.first().file.readBytes().contentEquals(bytes))
            assertEquals(
                0,
                root.walkTopDown().count {
                    it.isFile && (it.name.startsWith("staging-") || it.name.startsWith("commit-"))
                },
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun verifiedLookupRejectsWrongExpectedSize() = runBlocking {
        val root = createTempDirectory("unison-store-").toFile()
        try {
            val store = ManagedFileStore(root)
            val bytes = ByteArray(4096) { it.toByte() }
            val result = store.copyAndHash(ByteArrayInputStream(bytes))

            assertTrue(store.hasVerified(result.trackId, bytes.size.toLong()))
            assertTrue(!store.hasVerified(result.trackId, bytes.size.toLong() - 1))
            assertTrue(!store.hasVerified(result.trackId, 0))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun duplicateImportRepairsSameSizeCorruption() = runBlocking {
        val root = createTempDirectory("unison-store-").toFile()
        try {
            val store = ManagedFileStore(root)
            val bytes = ByteArray(256 * 1024) { (it % 211).toByte() }
            val first = store.copyAndHash(ByteArrayInputStream(bytes))
            first.file.writeBytes(ByteArray(bytes.size) { 0x5a.toByte() })

            val repaired = store.copyAndHash(ByteArrayInputStream(bytes))

            assertEquals(first.trackId, repaired.trackId)
            assertTrue(repaired.file.readBytes().contentEquals(bytes))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun verifiedLookupRejectsAndRemovesSameSizeCorruption() = runBlocking {
        val root = createTempDirectory("unison-store-").toFile()
        try {
            val store = ManagedFileStore(root)
            val bytes = ByteArray(64 * 1024) { (it % 241).toByte() }
            val result = store.copyAndHash(ByteArrayInputStream(bytes))
            result.file.writeBytes(ByteArray(bytes.size) { 0x33 })

            assertTrue(!store.hasVerified(result.trackId, bytes.size.toLong()))
            assertTrue(!result.file.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun activeLeaseBlocksDeletionUntilReleased() = runBlocking {
        val root = createTempDirectory("unison-store-").toFile()
        try {
            val store = ManagedFileStore(root)
            val bytes = "leased audio".repeat(2048).encodeToByteArray()
            val result = store.copyAndHash(ByteArrayInputStream(bytes))
            val lease = store.acquireLease(result.trackId, ManagedFileLeaseReason.PLAYBACK)

            assertTrue(store.isLeased(result.trackId))
            assertTrue(!store.delete(result.trackId))
            assertTrue(result.file.exists())

            lease.close()
            assertTrue(!store.isLeased(result.trackId))
            assertTrue(store.delete(result.trackId))
            assertTrue(!result.file.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun activeTransferLeaseProtectsStalePartialFromCleanup() = runBlocking {
        val root = createTempDirectory("unison-store-").toFile()
        try {
            val store = ManagedFileStore(root)
            val bytes = ByteArray(2048) { it.toByte() }
            val trackId = store.hash(ByteArrayInputStream(bytes)).trackId
            val partial = store.partialFile(trackId).apply {
                writeBytes(bytes)
                setLastModified(1L)
            }
            val lease = store.acquireLease(trackId, ManagedFileLeaseReason.TRANSFER_DOWNLOAD)

            assertEquals(0, store.cleanupAbandonedFiles(2L))
            assertTrue(partial.exists())
            lease.close()
            assertEquals(1, store.cleanupAbandonedFiles(2L))
            assertTrue(!partial.exists())
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
