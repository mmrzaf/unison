package com.darius.unison.storage

import android.content.Context
import com.darius.unison.model.TrackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.InputStream
import java.io.SyncFailedException
import java.security.MessageDigest

class ManagedFileStore(filesDir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val root = File(filesDir, "tracks").apply { mkdirs() }
    private val commitLocks = Array(COMMIT_LOCK_STRIPES) { Any() }

    fun finalFile(trackId: TrackId): File = fileFor(trackId, suffix = "")
    fun partialFile(trackId: TrackId): File = fileFor(trackId, suffix = ".part")

    private fun fileFor(trackId: TrackId, suffix: String): File {
        require(trackId.value.matches(Regex("[0-9a-f]{64}"))) { "Invalid SHA-256 track ID" }
        val directory = File(root, trackId.value.take(2)).apply { mkdirs() }
        return File(directory, trackId.value + suffix)
    }

    fun hasVerified(trackId: TrackId, expectedSize: Long? = null): Boolean {
        val file = finalFile(trackId)
        return file.isFile && (expectedSize == null || file.length() == expectedSize)
    }

    suspend fun hash(input: InputStream, maxBytes: Long = Long.MAX_VALUE): HashResult = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            currentCoroutineContext().ensureActive()
            if (read < 0) break
            if (read == 0) continue
            digest.update(buffer, 0, read)
            size += read
            require(size <= maxBytes) { "File exceeds the allowed size" }
        }
        HashResult(TrackId(digest.hex()), size)
    }

    /**
     * Copies into an app-owned staging file, hashes while copying, flushes while the descriptor is
     * still open, then atomically commits to the content-addressed destination.
     */
    suspend fun copyAndHash(input: InputStream, maxBytes: Long = Long.MAX_VALUE): CopyResult =
        withContext(Dispatchers.IO) {
            val staging = File.createTempFile("staging-", ".part", root)
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            try {
                writeFile(staging) { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        currentCoroutineContext().ensureActive()
                        if (read < 0) break
                        if (read == 0) continue
                        size += read
                        require(size <= maxBytes) { "File exceeds the allowed size" }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
                val id = TrackId(digest.hex())
                val target = finalFile(id)
                commitVerifiedStaging(staging, target, size)
                CopyResult(id, target, size)
            } catch (error: Throwable) {
                staging.delete()
                throw error
            }
        }

    private fun commitVerifiedStaging(staging: File, target: File, expectedSize: Long) {
        synchronized(commitLocks[(target.name.hashCode() and Int.MAX_VALUE) % commitLocks.size]) {
            if (target.isFile && target.length() == expectedSize && sha256Hex(target) == target.name) {
                staging.delete()
                return
            }
            if (target.exists() && !target.delete()) error("Could not replace stored audio")
            if (staging.renameTo(target)) return

            // A few filesystems reject a rename across directories even within app storage. Keep
            // the fallback hidden beside the destination and rename only after it is complete and
            // verified, so readers can never observe a half-written content-addressed final file.
            val replacement = File.createTempFile("commit-", ".tmp", target.parentFile)
            try {
                writeFile(replacement) { output ->
                    staging.inputStream().buffered(BUFFER_SIZE).use { input ->
                        input.copyTo(output, BUFFER_SIZE)
                    }
                }
                check(
                    replacement.length() == expectedSize && sha256Hex(replacement) == target.name
                ) { "Could not verify stored audio" }
                check(replacement.renameTo(target)) { "Could not commit stored audio" }
            } finally {
                replacement.delete()
                staging.delete()
            }
            check(target.isFile && target.length() == expectedSize) { "Could not store audio" }
        }
    }

    /** The flush is mandatory. fsync is best-effort because a few Android filesystems/providers
     * reject it even for a valid descriptor; that must not turn a completed import into a failure. */
    private inline fun writeFile(file: File, write: (BufferedOutputStream) -> Unit) {
        FileOutputStream(file).use { raw ->
            val buffered = BufferedOutputStream(raw, BUFFER_SIZE)
            try {
                write(buffered)
                buffered.flush()
                syncBestEffort(raw.fd)
            } finally {
                runCatching { buffered.close() }
            }
        }
    }

    private fun syncBestEffort(descriptor: FileDescriptor) {
        if (!descriptor.valid()) return
        try {
            descriptor.sync()
        } catch (_: SyncFailedException) {
            // Bytes were flushed and the staging/rename protocol still prevents partial final files.
        }
    }

    /**
     * Appends a peer transfer into the content-addressed partial file. All persistent audio writes
     * go through this store so flush/sync/close ordering is identical for imports and transfers.
     */
    suspend fun receivePartial(
        trackId: TrackId,
        offset: Long,
        expectedSize: Long,
        input: InputStream,
        onProgress: suspend (Long) -> Unit = {},
    ): Long = withContext(Dispatchers.IO) {
        require(offset in 0..expectedSize) { "Invalid transfer offset" }
        val partial = partialFile(trackId)
        val existing = partial.takeIf(File::isFile)?.length() ?: 0L
        require(existing == offset) { "Partial file changed" }

        var total = offset
        FileOutputStream(partial, offset > 0).use { raw ->
            val output = BufferedOutputStream(raw, BUFFER_SIZE)
            try {
                val buffer = ByteArray(BUFFER_SIZE)
                while (total < expectedSize) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), expectedSize - total).toInt())
                    currentCoroutineContext().ensureActive()
                    if (read < 0) error("Transfer ended early")
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    total += read
                    onProgress(total)
                }
                output.flush()
                syncBestEffort(raw.fd)
            } finally {
                runCatching { output.close() }
            }
        }
        check(partial.length() == expectedSize) { "Transfer size does not match" }
        total
    }

    suspend fun verifyPartial(trackId: TrackId, expectedSize: Long): Boolean = withContext(Dispatchers.IO) {
        val partial = partialFile(trackId)
        if (!partial.isFile || partial.length() != expectedSize) return@withContext false
        val digest = MessageDigest.getInstance("SHA-256")
        partial.inputStream().buffered(BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                currentCoroutineContext().ensureActive()
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        if (digest.hex() != trackId.value) return@withContext false
        commitVerifiedStaging(partial, finalFile(trackId), expectedSize)
        true
    }

    fun storedTrackFiles(): Map<TrackId, File> = buildMap {
        root.listFiles().orEmpty().filter(File::isDirectory).forEach { directory ->
            directory.listFiles().orEmpty().forEach { file ->
                val name = file.name
                if (file.isFile && name.matches(Regex("[0-9a-f]{64}"))) put(TrackId(name), file)
            }
        }
    }

    fun cleanupAbandonedFiles(olderThanEpochMs: Long): Int {
        var removed = 0
        root.walkTopDown().filter(File::isFile).forEach { file ->
            val temporary = file.name.startsWith("staging-") || file.name.endsWith(".part")
            if (temporary && file.lastModified() in 1 until olderThanEpochMs && file.delete()) removed++
        }
        return removed
    }

    fun discardPartial(trackId: TrackId): Boolean = partialFile(trackId).delete()
    fun delete(trackId: TrackId): Boolean {
        val target = finalFile(trackId)
        return synchronized(commitLocks[(target.name.hashCode() and Int.MAX_VALUE) % commitLocks.size]) {
            target.delete() or partialFile(trackId).delete()
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.hex()
    }

    private fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it) }

    data class HashResult(val trackId: TrackId, val sizeBytes: Long)
    data class CopyResult(val trackId: TrackId, val file: File, val sizeBytes: Long)

    private companion object {
        const val BUFFER_SIZE = 128 * 1024
        const val COMMIT_LOCK_STRIPES = 32
    }
}
