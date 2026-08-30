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
    /** Blocks deletion while a database/source publication is in flight, but not file replacement. */
    REFERENCE_PUBLICATION,
}

enum class ManagedFileDeleteResult {
    DELETED,
    DEFERRED,
    NOT_FOUND,
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

    private fun pendingDeleteFile(trackId: TrackId): File =
        fileFor(trackId, suffix = PENDING_DELETE_SUFFIX)

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

    fun acquireLease(trackId: TrackId, reason: ManagedFileLeaseReason): ManagedFileLease =
        synchronized(commitLock(trackId)) { acquireLeaseLocked(trackId, reason) }

    /**
     * Atomically proves that the already-resolved managed file still exists and establishes a lease
     * before deletion can win. This deliberately performs no deep hash while holding [commitLock].
     */
    fun acquireExistingFileLease(
        trackId: TrackId,
        expectedSize: Long?,
        reason: ManagedFileLeaseReason,
    ): LeasedManagedFile? =
        synchronized(commitLock(trackId)) {
            if (pendingDeleteFile(trackId).isFile) return@synchronized null
            val file = finalFile(trackId)
            if (!file.isFile || (expectedSize != null && file.length() != expectedSize)) {
                return@synchronized null
            }
            LeasedManagedFile(file, acquireLeaseLocked(trackId, reason))
        }

    private fun acquireLeaseLocked(
        trackId: TrackId,
        reason: ManagedFileLeaseReason,
    ): ManagedFileLease {
        val key = LeaseKey(trackId, reason)
        leaseCounts.compute(key) { _, count -> (count ?: 0) + 1 }
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
                    if (!isLeasedLocked(trackId)) completePendingDeleteLocked(trackId)
                }
            }
        }
    }

    fun isLeased(trackId: TrackId): Boolean = isLeasedLocked(trackId)

    private fun isLeasedLocked(trackId: TrackId): Boolean =
        ManagedFileLeaseReason.entries.any { reason ->
            leaseCounts[LeaseKey(trackId, reason)]?.let { it > 0 } == true
        }

    fun isDeletePending(trackId: TrackId): Boolean = pendingDeleteFile(trackId).isFile

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
        copyAndHashInternal(input, maxBytes, leaseReason = null).let {
            CopyResult(it.trackId, it.file, it.sizeBytes)
        }

    /**
     * Same safe staging/verification lifecycle as [copyAndHash], but acquires [reason] inside the
     * final commit lock before the committed path is returned to its publisher.
     */
    suspend fun copyAndHashWithLease(
        input: InputStream,
        maxBytes: Long = Long.MAX_VALUE,
        reason: ManagedFileLeaseReason,
    ): LeasedCopyResult =
        copyAndHashInternal(input, maxBytes, reason).let { result ->
            LeasedCopyResult(
                trackId = result.trackId,
                file = result.file,
                sizeBytes = result.sizeBytes,
                lease = checkNotNull(result.lease),
            )
        }

    private suspend fun copyAndHashInternal(
        input: InputStream,
        maxBytes: Long,
        leaseReason: ManagedFileLeaseReason?,
    ): CopyCommitResult =
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
                val lease = commitVerifiedStaging(staging, target, size, id, leaseReason)
                CopyCommitResult(id, target, size, lease)
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
        leaseReason: ManagedFileLeaseReason? = null,
    ): ManagedFileLease? =
        synchronized(commitLock(trackId)) {
            if (target.isFile && hasVerified(trackId, expectedSize)) {
                staging.delete()
                return@synchronized leaseReason?.let { acquireLeaseLocked(trackId, it) }
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
                return@synchronized leaseReason?.let { acquireLeaseLocked(trackId, it) }
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
            leaseReason?.let { acquireLeaseLocked(trackId, it) }
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

    /** Requests logical deletion without ever deleting bytes that currently have an active lease. */
    fun requestDelete(trackId: TrackId): ManagedFileDeleteResult =
        synchronized(commitLock(trackId)) {
            writePendingDeleteMarkerLocked(trackId)
            if (isLeasedLocked(trackId)) {
                ManagedFileDeleteResult.DEFERRED
            } else {
                val existed = finalFile(trackId).exists() || partialFile(trackId).exists()
                if (completePendingDeleteLocked(trackId)) {
                    if (existed) ManagedFileDeleteResult.DELETED else ManagedFileDeleteResult.NOT_FOUND
                } else {
                    ManagedFileDeleteResult.DEFERRED
                }
            }
        }

    /** Clears a deletion obligation after a managed reference has been successfully published. */
    fun cancelPendingDelete(trackId: TrackId): Boolean =
        synchronized(commitLock(trackId)) { pendingDeleteFile(trackId).delete() }

    fun pendingDeleteTrackIds(): Set<TrackId> = buildSet {
        root.listFiles().orEmpty().filter(File::isDirectory).forEach { directory ->
            directory.listFiles().orEmpty().forEach { file ->
                val value = file.name.removeSuffix(PENDING_DELETE_SUFFIX)
                if (file.isFile && file.name.endsWith(PENDING_DELETE_SUFFIX) && TRACK_ID_PATTERN.matches(value)) {
                    add(TrackId(value))
                }
            }
        }
    }

    /**
     * Crash-recovery cleanup for durable delete markers. [protectedTrackIds] are current managed DB
     * references and therefore cancel an obsolete marker instead of deleting live content.
     */
    fun cleanupPendingDeletes(
        maxFiles: Int = Int.MAX_VALUE,
        protectedTrackIds: Set<TrackId> = emptySet(),
    ): Int {
        require(maxFiles >= 0) { "Cleanup limit must not be negative" }
        var resolved = 0
        for (trackId in pendingDeleteTrackIds()) {
            if (resolved >= maxFiles) break
            val completed =
                synchronized(commitLock(trackId)) {
                    when {
                        trackId in protectedTrackIds -> pendingDeleteFile(trackId).delete()
                        isLeasedLocked(trackId) -> false
                        else -> completePendingDeleteLocked(trackId)
                    }
                }
            if (completed) resolved++
        }
        return resolved
    }

    fun delete(trackId: TrackId): Boolean {
        if (isLeased(trackId)) return false
        return synchronized(commitLock(trackId)) {
            if (isLeasedLocked(trackId)) {
                false
            } else {
                verificationCache.remove(trackId)
                val final = finalFile(trackId)
                val partial = partialFile(trackId)
                val existed = final.exists() || partial.exists()
                val finalDeleted = deleteIfPresent(final)
                val partialDeleted = deleteIfPresent(partial)
                pendingDeleteFile(trackId).delete()
                existed && finalDeleted && partialDeleted
            }
        }
    }

    private fun writePendingDeleteMarkerLocked(trackId: TrackId) {
        writeFile(pendingDeleteFile(trackId)) { output ->
            output.write(PENDING_DELETE_MARKER_BYTES)
        }
    }

    private fun completePendingDeleteLocked(trackId: TrackId): Boolean {
        val marker = pendingDeleteFile(trackId)
        if (!marker.isFile || isLeasedLocked(trackId)) return false
        verificationCache.remove(trackId)
        val final = finalFile(trackId)
        val partial = partialFile(trackId)
        if (!deleteIfPresent(final) || !deleteIfPresent(partial)) return false
        return marker.delete() || !marker.exists()
    }

    private fun deleteIfPresent(file: File): Boolean = !file.exists() || file.delete()

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

    data class LeasedCopyResult(
        val trackId: TrackId,
        val file: File,
        val sizeBytes: Long,
        val lease: ManagedFileLease,
    )

    data class LeasedManagedFile(val file: File, val lease: ManagedFileLease)

    private data class CopyCommitResult(
        val trackId: TrackId,
        val file: File,
        val sizeBytes: Long,
        val lease: ManagedFileLease?,
    )

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
        const val PENDING_DELETE_SUFFIX = ".delete-pending"
        val PENDING_DELETE_MARKER_BYTES = "delete-pending\n".encodeToByteArray()
        val TRACK_ID_PATTERN = Regex("[0-9a-f]{64}")
        val HEX_CHARS = "0123456789abcdef".toCharArray()
    }
}
