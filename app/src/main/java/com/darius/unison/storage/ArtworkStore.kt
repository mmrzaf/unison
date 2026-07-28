package com.darius.unison.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.core.graphics.scale
import com.darius.unison.model.TrackId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Lazy, coalesced, memory-bounded, and disk-bounded artwork extraction. */
class ArtworkStore(cacheDir: File) {
    private val root = File(cacheDir, "artwork").apply { mkdirs() }
    private val locks = Array(ARTWORK_LOCK_STRIPES) { Mutex() }
    private val memory = object : LruCache<String, Bitmap>(MEMORY_CACHE_KIB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    suspend fun fileFor(trackId: TrackId, audioFile: File): File? = withContext(Dispatchers.IO) {
        locks[stripe(trackId)].withLock {
            val target = artworkFile(trackId)
            val noArtwork = noArtworkMarker(trackId)
            val retryMarker = retryMarker(trackId)
            if (target.isFile && target.length() > 0) {
                if (hasDecodableBounds(target)) {
                    target.setLastModified(System.currentTimeMillis())
                    return@withLock target
                }
                target.delete()
                memory.remove(trackId.value)
            }
            if (noArtwork.isFile) return@withLock null
            if (remainingRetryDelayMs(retryMarker) != null) return@withLock null

            try {
                val bytes = embeddedPicture(audioFile)
                if (bytes == null || bytes.size > MAX_EMBEDDED_ART_BYTES) {
                    noArtwork.touch()
                    retryMarker.delete()
                    return@withLock null
                }
                val bitmap = decodeScaled(bytes, MAX_ARTWORK_EDGE_PX)
                if (bitmap == null) {
                    noArtwork.touch()
                    retryMarker.delete()
                    return@withLock null
                }
                val staging = File.createTempFile("art-", ".tmp", root)
                try {
                    FileOutputStream(staging).use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output))
                        output.flush()
                        output.fd.sync()
                    }
                    if (target.exists()) target.delete()
                    if (!staging.renameTo(target)) {
                        staging.copyTo(target, overwrite = true)
                        staging.delete()
                    }
                    noArtwork.delete()
                    retryMarker.delete()
                    memory.put(trackId.value, bitmap)
                    enforceDiskBudget()
                    target.takeIf { it.isFile && it.length() > 0 }
                } catch (error: Throwable) {
                    staging.delete()
                    if (memory.get(trackId.value) !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                    throw error
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                memory.evictAll()
                recordTransientFailure(retryMarker)
                null
            } catch (_: Exception) {
                // Provider/decoder failures can be transient for newly received files. Exponential
                // backoff prevents a failed cover from being decoded on every recomposition.
                recordTransientFailure(retryMarker)
                null
            }
        }
    }

    suspend fun bitmapFor(trackId: TrackId, audioFile: File): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(trackId.value)?.takeUnless { it.isRecycled }?.let { return@withContext it }
        val first = fileFor(trackId, audioFile) ?: return@withContext null
        memory.get(trackId.value)?.takeUnless { it.isRecycled }?.let { return@withContext it }
        decodeCached(trackId, first)?.let { return@withContext it }

        // A partial cache write or cleanup race self-heals by extracting once more from verified
        // audio. The striped lock coalesces concurrent callers for the same content hash.
        first.delete()
        memory.remove(trackId.value)
        val second = fileFor(trackId, audioFile) ?: return@withContext null
        decodeCached(trackId, second)
    }

    fun clearMemory() {
        memory.evictAll()
    }

    suspend fun invalidate(trackId: TrackId) = withContext(Dispatchers.IO) {
        locks[stripe(trackId)].withLock {
            memory.remove(trackId.value)
            noArtworkMarker(trackId).delete()
            retryMarker(trackId).delete()
        }
    }

    /** Removes stale markers/files and enforces the total disk budget. */
    fun cleanup(olderThanEpochMs: Long): Int {
        var removed = 0
        root.listFiles().orEmpty().forEach { file ->
            // A process death can leave staging files behind. Active staging files are fresh; stale
            // ones must not bypass cleanup forever merely because they are not cache entries.
            if (file.extension == "tmp") {
                if (file.lastModified() in 1 until olderThanEpochMs && file.delete()) removed++
                return@forEach
            }
            if (file.lastModified() in 1 until olderThanEpochMs && file.delete()) removed++
        }
        return removed + enforceDiskBudget()
    }

    fun artworkFile(trackId: TrackId): File = File(root, "${trackId.value}.jpg")

    fun transientRetryDelayMs(trackId: TrackId): Long? = remainingRetryDelayMs(retryMarker(trackId))

    private fun enforceDiskBudget(
        maxBytes: Long = DISK_CACHE_BYTES,
        maxFiles: Int = DISK_CACHE_FILES,
    ): Int {
        val candidates = root.listFiles().orEmpty()
            .filter { it.isFile && it.extension != "tmp" }
            .sortedBy(File::lastModified)
            .toMutableList()
        var bytes = candidates.sumOf { it.length().coerceAtLeast(0L) }
        var removed = 0
        while (candidates.size > maxFiles || bytes > maxBytes) {
            val oldest = candidates.removeFirstOrNull() ?: break
            val length = oldest.length().coerceAtLeast(0L)
            if (oldest.delete()) {
                bytes = (bytes - length).coerceAtLeast(0L)
                removed++
            }
        }
        return removed
    }

    private fun stripe(trackId: TrackId): Int =
        (trackId.value.hashCode() and Int.MAX_VALUE) % locks.size

    private fun retryMarker(trackId: TrackId): File = File(root, "${trackId.value}.retry-v3")

    private fun noArtworkMarker(trackId: TrackId): File = File(root, "${trackId.value}$NO_ARTWORK_SUFFIX")

    private fun remainingRetryDelayMs(marker: File): Long? {
        if (!marker.isFile) return null
        val failures = marker.readFailureCount()
        val delay = ArtworkRetryPolicy.delayMs(failures)
        val ageMs = (System.currentTimeMillis() - marker.lastModified()).coerceAtLeast(0L)
        return (delay - ageMs).takeIf { it > 0L }
    }

    private fun recordTransientFailure(marker: File) {
        val count = (marker.readFailureCount() + 1).coerceAtMost(MAX_RECORDED_FAILURES)
        marker.parentFile?.mkdirs()
        runCatching { marker.writeText(count.toString()) }
        marker.setLastModified(System.currentTimeMillis())
    }

    private fun File.readFailureCount(): Int = runCatching {
        readText().trim().toInt().coerceAtLeast(1)
    }.getOrDefault(0)

    private fun decodeCached(trackId: TrackId, file: File): Bitmap? = try {
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 },
        )?.also {
            file.setLastModified(System.currentTimeMillis())
            memory.put(trackId.value, it)
        }
    } catch (_: OutOfMemoryError) {
        memory.evictAll()
        null
    }

    private fun hasDecodableBounds(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0 &&
            options.outWidth <= MAX_CACHED_DIMENSION && options.outHeight <= MAX_CACHED_DIMENSION
    }

    private fun embeddedPicture(audioFile: File): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioFile.absolutePath)
            retriever.embeddedPicture
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeScaled(bytes: ByteArray, maxEdgePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth > MAX_SOURCE_DIMENSION || bounds.outHeight > MAX_SOURCE_DIMENSION) return null
        var sample = 1
        while (bounds.outWidth / sample > maxEdgePx * 2 || bounds.outHeight / sample > maxEdgePx * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            },
        ) ?: return null
        val scale = minOf(1f, maxEdgePx.toFloat() / maxOf(decoded.width, decoded.height))
        if (scale >= 1f) return decoded
        val scaled = decoded.scale(
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun File.touch() {
        parentFile?.mkdirs()
        if (!exists()) createNewFile() else setLastModified(System.currentTimeMillis())
    }

    private companion object {
        const val MAX_EMBEDDED_ART_BYTES = 12 * 1024 * 1024
        const val MAX_SOURCE_DIMENSION = 16_384
        const val MAX_CACHED_DIMENSION = 1_024
        const val MAX_ARTWORK_EDGE_PX = 512
        const val JPEG_QUALITY = 88
        const val MEMORY_CACHE_KIB = 8 * 1024
        const val DISK_CACHE_BYTES = 64L * 1024L * 1024L
        const val DISK_CACHE_FILES = 512
        const val ARTWORK_LOCK_STRIPES = 16
        const val MAX_RECORDED_FAILURES = 32
        const val NO_ARTWORK_SUFFIX = ".none-v3"
    }
}
