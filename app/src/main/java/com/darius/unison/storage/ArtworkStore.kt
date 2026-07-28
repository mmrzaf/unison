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

/** Lazy, bounded artwork extraction. Audio import never decodes artwork. */
class ArtworkStore(cacheDir: File) {
    private val root = File(cacheDir, "artwork").apply { mkdirs() }
    private val locks = Array(ARTWORK_LOCK_STRIPES) { Mutex() }
    private val memory = object : LruCache<String, Bitmap>(MEMORY_CACHE_KIB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    suspend fun fileFor(trackId: TrackId, audioFile: File): File? = withContext(Dispatchers.IO) {
        locks[(trackId.value.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            val target = artworkFile(trackId)
            val noArtwork = File(root, "${trackId.value}$NO_ARTWORK_SUFFIX")
            val retryMarker = retryMarker(trackId)
            if (target.isFile && target.length() > 0) {
                if (hasDecodableBounds(target)) return@withLock target
                target.delete()
                memory.remove(trackId.value)
            }
            if (noArtwork.isFile) return@withLock null
            if (retryMarker.isRecent(TRANSIENT_FAILURE_RETRY_MS)) return@withLock null
            retryMarker.delete()

            try {
                val bytes = embeddedPicture(audioFile)
                if (bytes == null || bytes.size > MAX_EMBEDDED_ART_BYTES) {
                    noArtwork.touch()
                    return@withLock null
                }
                val bitmap = decodeScaled(bytes, MAX_ARTWORK_EDGE_PX)
                if (bitmap == null) {
                    noArtwork.touch()
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
                    target.takeIf { it.isFile && it.length() > 0 }
                } catch (error: Throwable) {
                    staging.delete()
                    bitmap.recycle()
                    throw error
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Decoder/provider failures can be transient on newly received files or under memory
                // pressure. Do not permanently classify the immutable track as artwork-free.
                retryMarker.touch()
                null
            }
        }
    }

    suspend fun bitmapFor(trackId: TrackId, audioFile: File): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(trackId.value)?.takeUnless { it.isRecycled }?.let { return@withContext it }
        val first = fileFor(trackId, audioFile) ?: return@withContext null
        memory.get(trackId.value)?.takeUnless { it.isRecycled }?.let { return@withContext it }
        decodeCached(trackId, first)?.let { return@withContext it }

        // A partial cache write or storage cleanup race must self-heal instead of leaving a broken
        // JPEG forever. Re-extract once from the verified audio file.
        first.delete()
        memory.remove(trackId.value)
        val second = fileFor(trackId, audioFile) ?: return@withContext null
        decodeCached(trackId, second)
    }

    fun clearMemory() {
        memory.evictAll()
    }

    /**
     * Clears transient and negative results after a verified transfer completes. A valid cached
     * artwork file is preserved; [fileFor] already validates it and self-heals corrupt entries.
     */
    suspend fun invalidate(trackId: TrackId) = withContext(Dispatchers.IO) {
        locks[(trackId.value.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            memory.remove(trackId.value)
            File(root, "${trackId.value}$NO_ARTWORK_SUFFIX").delete()
            retryMarker(trackId).delete()
        }
    }

    fun cleanup(olderThanEpochMs: Long): Int {
        var removed = 0
        root.listFiles().orEmpty().forEach { file ->
            if (file.lastModified() in 1 until olderThanEpochMs && file.delete()) removed++
        }
        return removed
    }

    fun artworkFile(trackId: TrackId): File = File(root, "${trackId.value}.jpg")

    /** Remaining delay before a transient extraction failure should be retried, or null when the
     * track is not awaiting retry. Safe for UI/runtime scheduling; extraction remains lock-guarded. */
    fun transientRetryDelayMs(trackId: TrackId): Long? {
        val marker = retryMarker(trackId)
        if (!marker.isFile) return null
        val ageMs = (System.currentTimeMillis() - marker.lastModified()).coerceAtLeast(0L)
        return (TRANSIENT_FAILURE_RETRY_MS - ageMs).coerceAtLeast(0L)
    }

    private fun retryMarker(trackId: TrackId): File = File(root, "${trackId.value}.retry")

    private fun decodeCached(trackId: TrackId, file: File): Bitmap? =
        BitmapFactory.decodeFile(file.absolutePath)?.also { memory.put(trackId.value, it) }

    private fun hasDecodableBounds(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
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

    private fun File.isRecent(maxAgeMs: Long): Boolean =
        isFile && System.currentTimeMillis() - lastModified() in 0 until maxAgeMs

    private fun File.touch() {
        parentFile?.mkdirs()
        if (!exists()) createNewFile() else setLastModified(System.currentTimeMillis())
    }

    private companion object {
        const val MAX_EMBEDDED_ART_BYTES = 12 * 1024 * 1024
        const val MAX_ARTWORK_EDGE_PX = 512
        const val JPEG_QUALITY = 88
        const val MEMORY_CACHE_KIB = 8 * 1024
        const val ARTWORK_LOCK_STRIPES = 16
        const val TRANSIENT_FAILURE_RETRY_MS = 30_000L

        // Versioned suffix intentionally invalidates legacy markers that classified every decoder
        // exception as "no artwork" and could permanently hide valid cover art.
        const val NO_ARTWORK_SUFFIX = ".none-v2"
    }
}
