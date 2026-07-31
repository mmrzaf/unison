package com.darius.unison.storage

import android.content.Context
import com.darius.unison.model.TrackId
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.io.SyncFailedException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class ManagedFileLeaseReason {
    ROOM_QUEUE,
    PLAYBACK,
    TRANSFER_DOWNLOAD,
    TRANSFER_UPLOAD,
    IMPORT,
    INDEXING,
    PENDING_SIDE_EFFECT,
}

interface ManagedFileLease : AutoCloseable {
    val trackId: TrackId
    val reason: ManagedFileLeaseReason
}

class ManagedFileStore(filesDir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val root = File(filesDir, "tracks").apply { mkdirs() }
    private val commitLocks = Array(COMMIT_LOCK_STRIPES) { Any() }
    private val leaseCounts = ConcurrentHashMap<LeaseKey, Int>()
    private val verificationCache = ConcurrentHashMap<TrackId, VerificationStamp>()
    private val fullVerificationCount = AtomicLong(0L)

    fun finalFile(trackId: TrackId): File = fileFor(trackId, suffix = "")

    fun partialFile(trackId: TrackId): File = fileFor(trackId, suffix = ".part")

    /** Metadata-only check for bounded background cleanup; playback still uses [hasVerified]. */
    fun hasStoredFile(trackId: TrackId, expectedSize: Long? = null): Boolean {
        val file = finalFile(trackId)
        return file.isFile && (expectedSize == null || file.length() == expectedSize)
    }

    private fun fileFor(trackId: TrackId, suffix: String): File {
        require(TRACK_ID_PATTERN.matches(trackId.value)) { "Invalid SHA-256 track ID" }
        val directory = File(root, trackId.value.take(2)).apply { mkdirs() }
        return File(directory, trackId.value + suffix)
    }

    /**
     * Returns a cryptographically verified file without rereading the complete song on every queue
     * refresh. A successful full verification is cached against size, modification time and a small
     * start/middle/end fingerprint. Any metadata or sampled-byte change forces another full hash.
     */
    fun hasVerified(trackId: TrackId, expectedSize: Long? = null): Boolean {
        val file = finalFile(trackId)
        if (!file.isFile) {
            verificationCache.remove(trackId)
            return false
        }
        if (expectedSize != null && file.length() != expectedSize) {
            verificationCache.remove(trackId)
            discardCorruptFinal(trackId, file)
            return false
        }
        val stamp = runCatching { verificationStamp(file) }.getOrNull() ?: return false
        if (verificationCache[trackId] == stamp) return true
        val valid = runCatching { sha256Hex(file) == trackId.value }.getOrDefault(false)
        if (valid) {
            verificationCache[trackId] = stamp
        } else {
            verificationCache.remove(trackId)
            discardCorruptFinal(trackId, file)
        }
        return valid
    }

    /** Explicit deep verification for maintenance or corruption investigation. */
    suspend fun deepVerify(trackId: TrackId, expectedSize: Long? = null): Boolean =
        withContext(Dispatchers.IO) {
            verificationCache.remove(trackId)
            hasVerified(trackId, expectedSize)
        }

    internal fun fullVerificationCountForTests(): Long = fullVerificationCount.get()

    fun acquireLease(trackId: TrackId, reason: ManagedFileLeaseReason): ManagedFileLease {
        val key = LeaseKey(trackId, reason)
        synchronized(commitLock(trackId)) {
            leaseCounts.compute(key) { _, count -> (count ?: 0) + 1 }
        }
        return object : ManagedFileLease {
            override val trackId: TrackId = trackId
            override val reason: ManagedFileLeaseReason = reason
            private val closed = AtomicBoolean(false)

            override fun close() {
                if (!closed.compareAndSet(false, true)) return
                synchronized(commitLock(trackId)) {
                    leaseCounts.compute(key) { _, count ->
                        when {
                            count == null || count <= 1 -> null
                            else -> count - 1
                        }
                    }
                }
            }
        }
    }

    fun isLeased(trackId: TrackId): Boolean =
        ManagedFileLeaseReason.entries.any { reason ->
            leaseCounts[LeaseKey(trackId, reason)]?.let { it > 0 } == true
        }

    private fun isReplacementProtected(trackId: TrackId): Boolean =
        REPLACEMENT_PROTECTING_LEASES.any { reason ->
            leaseCounts[LeaseKey(trackId, reason)]?.let { it > 0 } == true
        }

    private fun commitLock(trackId: TrackId): Any =
        commitLocks[(trackId.value.hashCode() and Int.MAX_VALUE) % commitLocks.size]

    private fun discardCorruptFinal(trackId: TrackId, file: File) {
        if (isLeased(trackId)) return
        synchronized(commitLock(trackId)) {
            if (!isLeased(trackId)) {
                verificationCache.remove(trackId)
                file.delete()
            }
        }
    }

    suspend fun hash(input: InputStream, maxBytes: Long = Long.MAX_VALUE): HashResult =
        withContext(Dispatchers.IO) {
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
                commitVerifiedStaging(staging, target, size, id)
                CopyResult(id, target, size)
            } catch (error: Throwable) {
                staging.delete()
                throw error
            }
        }

    private fun commitVerifiedStaging(
        staging: File,
        target: File,
        expectedSize: Long,
        trackId: TrackId,
    ) {
        synchronized(commitLock(trackId)) {
            if (target.isFile && hasVerified(trackId, expectedSize)) {
                staging.delete()
                return
            }
            verificationCache.remove(trackId)
            if (target.exists()) {
                check(!isReplacementProtected(trackId)) {
                    "Stored audio is currently in use and cannot be replaced"
                }
                if (!target.delete()) error("Could not replace stored audio")
            }
            if (staging.renameTo(target)) {
                verificationCache[trackId] = verificationStamp(target)
                return
            }

            // Some filesystems reject rename across directories. Hash the fallback while copying,
            // rather than copying and then rereading the complete song a second time.
            val replacement = File.createTempFile("commit-", ".tmp", target.parentFile)
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                writeFile(replacement) { output ->
                    staging.inputStream().buffered(BUFFER_SIZE).use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }
                check(replacement.length() == expectedSize && digest.hex() == trackId.value) {
                    "Could not verify stored audio"
                }
                check(replacement.renameTo(target)) { "Could not commit stored audio" }
            } finally {
                replacement.delete()
                staging.delete()
            }
            check(target.isFile && target.length() == expectedSize) { "Could not store audio" }
            verificationCache[trackId] = verificationStamp(target)
        }
    }

    /**
     * The flush is mandatory. fsync is best-effort because a few Android filesystems/providers
     * reject it even for a valid descriptor; that must not turn a completed import into a failure.
     */
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
            // Bytes were flushed and the staging/rename protocol still prevents partial final
            // files.
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
    ): Long = receivePartialAndHash(trackId, offset, expectedSize, input, onProgress).totalBytes

    /**
     * Hashes the existing resume prefix once, then hashes new bytes while writing them. A completed
     * transfer can therefore be committed without rereading the full file from storage.
     */
    suspend fun receivePartialAndHash(
        trackId: TrackId,
        offset: Long,
        expectedSize: Long,
        input: InputStream,
        onProgress: suspend (Long) -> Unit = {},
    ): PartialReceiveResult =
        withContext(Dispatchers.IO) {
            require(offset in 0..expectedSize) { "Invalid transfer offset" }
            val partial = partialFile(trackId)
            val existing = partial.takeIf(File::isFile)?.length() ?: 0L
            require(existing == offset) { "Partial file changed" }

            val digest = MessageDigest.getInstance("SHA-256")
            if (offset > 0) {
                partial.inputStream().buffered(BUFFER_SIZE).use { prefix ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var remaining = offset
                    while (remaining > 0) {
                        currentCoroutineContext().ensureActive()
                        val read =
                            prefix.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) error("Partial file ended before resume offset")
                        if (read == 0) continue
                        digest.update(buffer, 0, read)
                        remaining -= read
                    }
                }
            }

            var total = offset
            FileOutputStream(partial, offset > 0).use { raw ->
                val output = BufferedOutputStream(raw, BUFFER_SIZE)
                try {
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (total < expectedSize) {
                        currentCoroutineContext().ensureActive()
                        val read =
                            input.read(
                                buffer,
                                0,
                                minOf(buffer.size.toLong(), expectedSize - total).toInt(),
                            )
                        currentCoroutineContext().ensureActive()
                        if (read < 0) error("Transfer ended early")
                        if (read == 0) continue
                        digest.update(buffer, 0, read)
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
            PartialReceiveResult(total, digest.hex())
        }

    fun commitPartialWithDigest(
        trackId: TrackId,
        expectedSize: Long,
        computedSha256: String,
    ): Boolean {
        val partial = partialFile(trackId)
        if (!partial.isFile || partial.length() != expectedSize || computedSha256 != trackId.value)
            return false
        commitVerifiedStaging(partial, finalFile(trackId), expectedSize, trackId)
        return true
    }

    suspend fun verifyPartial(trackId: TrackId, expectedSize: Long): Boolean =
        withContext(Dispatchers.IO) {
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
            commitVerifiedStaging(partial, finalFile(trackId), expectedSize, trackId)
            true
        }

    fun storedTrackFiles(): Map<TrackId, File> = buildMap {
        root.listFiles().orEmpty().filter(File::isDirectory).forEach { directory ->
            directory.listFiles().orEmpty().forEach { file ->
                val name = file.name
                if (file.isFile && TRACK_ID_PATTERN.matches(name)) put(TrackId(name), file)
            }
        }
    }

    fun cleanupAbandonedFiles(olderThanEpochMs: Long, maxFiles: Int = Int.MAX_VALUE): Int {
        require(maxFiles >= 0) { "Cleanup limit must not be negative" }
        var removed = 0
        root.walkTopDown().filter(File::isFile).forEach { file ->
            if (removed >= maxFiles) return removed
            val temporary = file.name.startsWith("staging-") || file.name.endsWith(".part")
            val partialTrackId =
                file.name.removeSuffix(".part").takeIf(TRACK_ID_PATTERN::matches)?.let(::TrackId)
            val protected = partialTrackId?.let(::isLeased) == true
            if (
                temporary &&
                    !protected &&
                    file.lastModified() in 1 until olderThanEpochMs &&
                    file.delete()
            )
                removed++
        }
        return removed
    }

    /** Explicit transfer-owner discard. Background cleanup never calls this method. */
    fun discardPartial(trackId: TrackId): Boolean = partialFile(trackId).delete()

    fun delete(trackId: TrackId): Boolean {
        if (isLeased(trackId)) return false
        val target = finalFile(trackId)
        return synchronized(commitLock(trackId)) {
            if (isLeased(trackId)) {
                false
            } else {
                verificationCache.remove(trackId)
                target.delete() or partialFile(trackId).delete()
            }
        }
    }

    private fun sha256Hex(file: File): String {
        fullVerificationCount.incrementAndGet()
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

    private fun verificationStamp(file: File): VerificationStamp {
        val length = file.length()
        val digest = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(file, "r").use { random ->
            val offsets =
                longArrayOf(
                        0L,
                        (length / 2L - QUICK_SAMPLE_BYTES / 2L).coerceAtLeast(0L),
                        (length - QUICK_SAMPLE_BYTES).coerceAtLeast(0L),
                    )
                    .distinct()
            val buffer = ByteArray(QUICK_SAMPLE_BYTES)
            offsets.forEach { offset ->
                random.seek(offset)
                val wanted = minOf(buffer.size.toLong(), length - offset).coerceAtLeast(0L).toInt()
                if (wanted > 0) {
                    val read = random.read(buffer, 0, wanted)
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
        }
        return VerificationStamp(length, file.lastModified(), digest.hex())
    }

    private fun MessageDigest.hex(): String {
        val bytes = digest()
        val chars = CharArray(bytes.size * 2)
        bytes.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            chars[index * 2] = HEX_CHARS[value ushr 4]
            chars[index * 2 + 1] = HEX_CHARS[value and 0x0f]
        }
        return String(chars)
    }

    private data class VerificationStamp(
        val length: Long,
        val lastModified: Long,
        val quickFingerprint: String,
    )

    private data class LeaseKey(
        val trackId: TrackId,
        val reason: ManagedFileLeaseReason,
    )

    data class HashResult(val trackId: TrackId, val sizeBytes: Long)

    data class CopyResult(val trackId: TrackId, val file: File, val sizeBytes: Long)

    data class PartialReceiveResult(val totalBytes: Long, val sha256Hex: String)

    private companion object {
        val REPLACEMENT_PROTECTING_LEASES =
            setOf(
                ManagedFileLeaseReason.ROOM_QUEUE,
                ManagedFileLeaseReason.PLAYBACK,
                ManagedFileLeaseReason.TRANSFER_UPLOAD,
                ManagedFileLeaseReason.INDEXING,
                ManagedFileLeaseReason.PENDING_SIDE_EFFECT,
            )
        const val BUFFER_SIZE = 128 * 1024
        const val QUICK_SAMPLE_BYTES = 4 * 1024
        const val COMMIT_LOCK_STRIPES = 32
        val TRACK_ID_PATTERN = Regex("[0-9a-f]{64}")
        val HEX_CHARS = "0123456789abcdef".toCharArray()
    }
}
