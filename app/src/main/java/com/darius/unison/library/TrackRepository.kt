package com.darius.unison.library

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TrackSourceType
import com.darius.unison.storage.ManagedFileStore
import com.darius.unison.storage.TrackEntity
import com.darius.unison.storage.TrackSourceEntity
import com.darius.unison.storage.UnisonDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

data class StorageSummary(
    val totalBytes: Long = 0L,
    val keptBytes: Long = 0L,
    val temporaryBytes: Long = 0L,
)

class TrackRepository(
    private val context: Context,
    private val database: UnisonDatabase,
    private val fileStore: ManagedFileStore,
) {
    val tracks: Flow<List<TrackDescriptor>> =
        database.trackDao().observeAll().map { entities -> entities.map(TrackEntity::toDescriptor) }
    val temporaryTrackIds: Flow<Set<TrackId>> = database.trackSourceDao().observeTemporaryTrackIds()
        .map { ids -> ids.mapTo(mutableSetOf(), ::TrackId) }
    val storageSummary: Flow<StorageSummary> = combine(
        database.trackSourceDao().observeManagedBytes(),
        database.trackSourceDao().observeKeptBytes(),
        database.trackSourceDao().observeTemporaryBytes(),
    ) { total, kept, temporary ->
        StorageSummary(
            totalBytes = total.coerceAtLeast(0L),
            keptBytes = kept.coerceAtLeast(0L),
            temporaryBytes = temporary.coerceAtLeast(0L),
        )
    }

    suspend fun get(trackId: TrackId): TrackDescriptor? = database.trackDao().get(trackId.value)?.toDescriptor()

    suspend fun getMany(trackIds: List<TrackId>): List<TrackDescriptor> {
        if (trackIds.isEmpty()) return emptyList()
        val byId = database.trackDao().getMany(trackIds.map { it.value })
            .associate { TrackId(it.trackId) to it.toDescriptor() }
        return trackIds.mapNotNull(byId::get)
    }

    suspend fun hasVerifiedSource(trackId: TrackId): Boolean {
        val expectedSize = database.trackDao().get(trackId.value)?.sizeBytes
        return fileStore.hasVerified(trackId, expectedSize) || bestReadableUri(trackId) != null
    }

    /** Best-effort portable M3U reference. App-private managed paths are intentionally never
     * exported because another app or device cannot read them. The caller falls back to the
     * original filename, producing a relative playlist entry without exposing private storage. */
    suspend fun exportReference(trackId: TrackId): String? = withContext(Dispatchers.IO) {
        database.trackSourceDao().getForTrack(trackId.value).firstNotNullOfOrNull { source ->
            source.uri?.toUri()?.takeIf(::isReadable)?.toString()
        }
    }

    suspend fun bestReadableUri(trackId: TrackId): Uri? = withContext(Dispatchers.IO) {
        if (fileStore.hasVerified(trackId)) return@withContext Uri.fromFile(fileStore.finalFile(trackId))
        database.trackSourceDao().getForTrack(trackId.value).firstNotNullOfOrNull { source ->
            when {
                source.managedRelativePath != null -> fileStore.finalFile(trackId).takeIf(File::isFile)
                    ?.let(Uri::fromFile)

                source.uri != null -> source.uri.toUri().takeIf { isReadable(it) }
                else -> null
            }
        }
    }

    suspend fun requireReadableFile(trackId: TrackId): File? = withContext(Dispatchers.IO) {
        val expectedSize = database.trackDao().get(trackId.value)?.sizeBytes
        val managed = fileStore.finalFile(trackId)
        if (fileStore.hasVerified(trackId, expectedSize)) return@withContext managed
        if (managed.exists()) managed.delete()
        run {
            val uri = bestReadableUri(trackId) ?: return@withContext null
            requireSupportedSize(context.contentResolver, uri)
            context.contentResolver.openInputStream(uri)?.use { input ->
                val result = fileStore.copyAndHash(input, MAX_TRACK_BYTES)
                if (result.trackId != trackId) {
                    result.file.delete()
                    return@withContext null
                }
                val now = System.currentTimeMillis()
                val existingManaged = database.trackSourceDao().get("managed:${trackId.value}")
                val retention = strongerRetention(
                    existingManaged?.retentionPolicy?.let(::retentionPolicyOrNull),
                    RetentionPolicy.TEMPORARY_24_HOURS,
                )
                database.trackSourceDao().upsert(
                    TrackSourceEntity(
                        sourceId = "managed:${trackId.value}",
                        trackId = trackId.value,
                        sourceType = TrackSourceType.APP_MANAGED_FILE.name,
                        uri = null,
                        managedRelativePath = result.file.relativeTo(context.filesDir).path,
                        retentionPolicy = retention.name,
                        verified = true,
                        lastVerifiedAt = now,
                        expiresAt = retention.expiryFrom(now),
                    )
                )
                result.file
            }
        }
    }

    suspend fun registerManagedFile(
        descriptor: TrackDescriptor,
        retentionPolicy: RetentionPolicy,
    ) {
        val now = System.currentTimeMillis()
        upsertTrack(descriptor, now)
        val sourceId = "managed:${descriptor.trackId.value}"
        val existing = database.trackSourceDao().get(sourceId)
        val effectiveRetention = strongerRetention(
            existing?.retentionPolicy?.let(::retentionPolicyOrNull),
            retentionPolicy,
        )
        database.trackSourceDao().upsert(
            TrackSourceEntity(
                sourceId = sourceId,
                trackId = descriptor.trackId.value,
                sourceType = TrackSourceType.APP_MANAGED_FILE.name,
                uri = null,
                managedRelativePath = fileStore.finalFile(descriptor.trackId).relativeTo(context.filesDir).path,
                retentionPolicy = effectiveRetention.name,
                verified = true,
                lastVerifiedAt = now,
                expiresAt = effectiveRetention.expiryFrom(now),
            )
        )
    }

    /**
     * Imports audio into Unison's app-owned content-addressed store. File pickers, the Android share
     * sheet, M3U resolution, playlists, and room additions all use this same path so playback never
     * depends on a short-lived provider permission or a file that can move underneath the room.
     */
    suspend fun importUri(
        uri: Uri,
        retentionPolicy: RetentionPolicy = RetentionPolicy.KEEP_IN_LIBRARY,
    ): TrackDescriptor = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = queryDisplayName(resolver, uri)
        val mime = resolver.getType(uri)
        val metadata = extractMetadata(uri)
        validateAudioCandidate(name, mime ?: metadata.mimeType, metadata.durationMs)
        requireSupportedSize(resolver, uri)

        val result = resolver.openInputStream(uri)?.use { input ->
            fileStore.copyAndHash(input, MAX_TRACK_BYTES)
        } ?: error("Unable to open selected file")

        val descriptor = TrackDescriptor(
            trackId = result.trackId,
            sizeBytes = result.sizeBytes,
            mimeType = mime ?: metadata.mimeType,
            durationMs = metadata.durationMs,
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            originalFileName = name,
        )
        registerManagedFile(descriptor, retentionPolicy)
        descriptor
    }


    suspend fun keep(trackId: TrackId) {
        database.trackSourceDao().getForTrack(trackId.value)
            .filter { it.sourceType == TrackSourceType.APP_MANAGED_FILE.name }
            .forEach {
                database.trackSourceDao().updateRetention(it.sourceId, RetentionPolicy.KEEP_IN_LIBRARY.name, null)
            }
    }

    suspend fun touchTemporary(trackId: TrackId) {
        database.trackSourceDao().extendTemporary(
            trackId.value,
            System.currentTimeMillis() + TEMPORARY_RETENTION_MS,
        )
    }

    suspend fun deleteTemporary(trackId: TrackId) {
        val temporary = database.trackSourceDao().getForTrack(trackId.value)
            .filter { it.retentionPolicy == RetentionPolicy.TEMPORARY_24_HOURS.name }
        for (source in temporary) database.trackSourceDao().delete(source)
        if (database.trackSourceDao().managedCountForTrack(trackId.value) == 0) fileStore.delete(trackId)
        if (database.trackSourceDao().countForTrack(trackId.value) == 0) database.trackDao().delete(trackId.value)
    }

    suspend fun clearTemporary(excludedTrackIds: Set<TrackId> = emptySet()): Int = withContext(Dispatchers.IO) {
        val excluded = excludedTrackIds.mapTo(hashSetOf()) { it.value }
        val candidates = database.trackSourceDao().temporarySources()
            .asSequence()
            .map { TrackId(it.trackId) }
            .filter { it.value !in excluded }
            .distinct()
            .toList()
        candidates.forEach { deleteTemporary(it) }
        candidates.size
    }

    suspend fun descriptorFromManagedFile(trackId: TrackId): TrackDescriptor? {
        val existing = get(trackId)
        if (existing != null) return existing
        val file = fileStore.finalFile(trackId).takeIf(File::isFile) ?: return null
        val metadata = extractMetadata(Uri.fromFile(file))
        val descriptor = TrackDescriptor(
            trackId = trackId,
            sizeBytes = file.length(),
            mimeType = metadata.mimeType,
            durationMs = metadata.durationMs,
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            originalFileName = trackId.value,
        )
        registerManagedFile(descriptor, RetentionPolicy.TEMPORARY_24_HOURS)
        return descriptor
    }


    private fun strongerRetention(existing: RetentionPolicy?, requested: RetentionPolicy): RetentionPolicy = when {
        existing == RetentionPolicy.KEEP_IN_LIBRARY || requested == RetentionPolicy.KEEP_IN_LIBRARY -> RetentionPolicy.KEEP_IN_LIBRARY
        existing == RetentionPolicy.TEMPORARY_24_HOURS || requested == RetentionPolicy.TEMPORARY_24_HOURS -> RetentionPolicy.TEMPORARY_24_HOURS
        else -> requested
    }

    private fun retentionPolicyOrNull(value: String): RetentionPolicy? =
        runCatching { RetentionPolicy.valueOf(value) }.getOrNull()

    private fun RetentionPolicy.expiryFrom(now: Long): Long? =
        if (this == RetentionPolicy.TEMPORARY_24_HOURS) now + TEMPORARY_RETENTION_MS else null

    private suspend fun upsertTrack(descriptor: TrackDescriptor, now: Long) {
        val existing = database.trackDao().get(descriptor.trackId.value)
        database.trackDao().upsert(
            descriptor.toEntity(existing?.createdAt ?: now).copy(lastPlayedAt = existing?.lastPlayedAt)
        )
    }


    @Suppress("UsableSpace") // Require space already available; do not evict other apps' caches.
    private fun requireSupportedSize(resolver: ContentResolver, uri: Uri) {
        val declaredSize = querySize(resolver, uri) ?: return
        require(declaredSize in 0..MAX_TRACK_BYTES) { "Audio files larger than 1 GiB are not supported" }
        require(context.filesDir.usableSpace >= declaredSize + MIN_FREE_SPACE_BYTES) { "Not enough storage space" }
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun validateAudioCandidate(name: String?, mimeType: String?, durationMs: Long) {
        val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val looksLikeAudio = mimeType?.startsWith("audio/", ignoreCase = true) == true ||
            extension in SUPPORTED_AUDIO_EXTENSIONS || durationMs > 0
        require(looksLikeAudio) { "The selected file is not recognized as audio" }
    }

    private fun isReadable(uri: Uri): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun extractMetadata(uri: Uri): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            AudioMetadata(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: 0,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
            )
        } catch (_: Throwable) {
            AudioMetadata()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private data class AudioMetadata(
        val durationMs: Long = 0,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val mimeType: String? = null,
    )

    companion object {
        const val TEMPORARY_RETENTION_MS = 24 * 60 * 60 * 1000L
        private const val MAX_TRACK_BYTES = 1L shl 30
        private const val MIN_FREE_SPACE_BYTES = 32L * 1024L * 1024L
        private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "3gp", "mp4"
        )
    }
}

internal fun TrackEntity.toDescriptor() = TrackDescriptor(
    trackId = TrackId(trackId),
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    durationMs = durationMs,
    title = title,
    artist = artist,
    album = album,
    originalFileName = originalFileName,
)

internal fun TrackDescriptor.toEntity(now: Long) = TrackEntity(
    trackId = trackId.value,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    durationMs = durationMs,
    title = title,
    artist = artist,
    album = album,
    originalFileName = originalFileName,
    createdAt = now,
    lastPlayedAt = null,
)
