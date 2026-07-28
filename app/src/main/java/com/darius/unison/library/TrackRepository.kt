package com.darius.unison.library

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
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
import androidx.paging.map as mapPaging

data class StorageSummary(
    val totalBytes: Long = 0L,
    val keptBytes: Long = 0L,
    val temporaryBytes: Long = 0L,
)

enum class LibrarySort { RECENT, TITLE, ARTIST, ALBUM }

class TrackRepository(
    private val context: Context,
    private val database: UnisonDatabase,
    private val fileStore: ManagedFileStore,
) {
    fun pagedLibrary(query: String, sort: LibrarySort): Flow<PagingData<TrackDescriptor>> =
        Pager(
            config = PagingConfig(
                pageSize = LIBRARY_PAGE_SIZE,
                initialLoadSize = LIBRARY_INITIAL_LOAD_SIZE,
                prefetchDistance = LIBRARY_PREFETCH_DISTANCE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                val normalizedQuery = query.trim()
                when (sort) {
                    LibrarySort.RECENT -> database.trackDao().pagingRecent(normalizedQuery)
                    LibrarySort.TITLE -> database.trackDao().pagingByTitle(normalizedQuery)
                    LibrarySort.ARTIST -> database.trackDao().pagingByArtist(normalizedQuery)
                    LibrarySort.ALBUM -> database.trackDao().pagingByAlbum(normalizedQuery)
                }
            },
        ).flow.map { pagingData -> pagingData.mapPaging(TrackEntity::toDescriptor) }

    fun observeLibraryCount(query: String): Flow<Int> =
        database.trackDao().observeLibraryCount(query.trim())

    suspend fun libraryTrackIds(query: String): Set<TrackId> =
        database.trackDao().libraryTrackIds(query.trim()).mapTo(linkedSetOf(), ::TrackId)

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

    suspend fun markPlayed(trackId: TrackId) {
        database.trackDao().markPlayed(trackId.value, System.currentTimeMillis())
    }

    suspend fun findReferenceCandidates(fileName: String, title: String): List<TrackDescriptor> =
        database.trackDao().findReferenceCandidates(fileName, title).map(TrackEntity::toDescriptor)

    suspend fun getMany(trackIds: List<TrackId>): List<TrackDescriptor> {
        if (trackIds.isEmpty()) return emptyList()
        // Android's SQLite bind-variable limit varies by platform build. Large imported
        // playlists are valid, so query in conservative chunks and restore caller order.
        val idChunks = trackIds
            .asSequence()
            .map(TrackId::value)
            .distinct()
            .chunked(SQLITE_BIND_CHUNK_SIZE)
            .toList()
        val entities = mutableListOf<TrackEntity>()
        for (chunk in idChunks) entities += database.trackDao().getMany(chunk)
        val byId = entities
            .associate { TrackId(it.trackId) to it.toDescriptor() }
        return trackIds.mapNotNull(byId::get)
    }

    suspend fun hasVerifiedSource(trackId: TrackId): Boolean {
        val expectedSize = database.trackDao().get(trackId.value)?.sizeBytes
        return verifiedManagedFile(trackId, expectedSize) != null || bestReadableUri(trackId) != null
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
        val expectedSize = database.trackDao().get(trackId.value)?.sizeBytes
        verifiedManagedFile(trackId, expectedSize)?.let { return@withContext Uri.fromFile(it) }
        database.trackSourceDao().getForTrack(trackId.value).firstNotNullOfOrNull { source ->
            when {
                source.managedRelativePath != null -> verifiedManagedFile(trackId, expectedSize)?.let(Uri::fromFile)

                source.uri != null -> source.uri.toUri().takeIf { isReadable(it) }
                else -> null
            }
        }
    }

    suspend fun requireReadableFile(trackId: TrackId): File? = withContext(Dispatchers.IO) {
        val expectedSize = database.trackDao().get(trackId.value)?.sizeBytes
        val managed = fileStore.finalFile(trackId)
        verifiedManagedFile(trackId, expectedSize)?.let { return@withContext it }
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
        val normalized = normalizeDescriptor(descriptor)
        check(
            fileStore.hasVerified(
                normalized.trackId,
                normalized.sizeBytes
            )
        ) { "Managed audio is missing or incomplete" }
        val now = System.currentTimeMillis()
        database.withTransaction {
            upsertTrack(normalized, now)
            val sourceId = "managed:${normalized.trackId.value}"
            val existing = database.trackSourceDao().get(sourceId)
            val effectiveRetention = strongerRetention(
                existing?.retentionPolicy?.let(::retentionPolicyOrNull),
                retentionPolicy,
            )
            database.trackSourceDao().upsert(
                TrackSourceEntity(
                    sourceId = sourceId,
                    trackId = normalized.trackId.value,
                    sourceType = TrackSourceType.APP_MANAGED_FILE.name,
                    uri = null,
                    managedRelativePath = fileStore.finalFile(normalized.trackId).relativeTo(context.filesDir).path,
                    retentionPolicy = effectiveRetention.name,
                    verified = true,
                    lastVerifiedAt = now,
                    expiresAt = effectiveRetention.expiryFrom(now),
                )
            )
        }
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
        requireSupportedSize(resolver, uri)
        val name = queryDisplayName(resolver, uri)
        val mime = resolver.getType(uri)
        val metadata = extractMetadata(uri)
        validateAudioCandidate(name, mime ?: metadata.mimeType, metadata.durationMs)

        val result = resolver.openInputStream(uri)?.use { input ->
            fileStore.copyAndHash(input, MAX_TRACK_BYTES)
        } ?: error("Unable to open selected file")

        if (result.sizeBytes !in 1..MAX_TRACK_BYTES) {
            result.file.delete()
            error("The selected audio file is empty or too large")
        }
        val descriptor = normalizeDescriptor(
            TrackDescriptor(
                trackId = result.trackId,
                sizeBytes = result.sizeBytes,
                mimeType = mime ?: metadata.mimeType,
                durationMs = metadata.durationMs,
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album,
                originalFileName = name,
            )
        )
        registerManagedFile(descriptor, retentionPolicy)
        descriptor
    }

    suspend fun keep(trackId: TrackId) {
        keepMany(listOf(trackId))
    }

    suspend fun keepMany(trackIds: Collection<TrackId>) {
        trackIds
            .asSequence()
            .map(TrackId::value)
            .distinct()
            .chunked(SQLITE_BIND_CHUNK_SIZE)
            .forEach { chunk ->
                database.trackSourceDao().updateRetentionForTracks(
                    trackIds = chunk,
                    sourceType = TrackSourceType.APP_MANAGED_FILE.name,
                    policy = RetentionPolicy.KEEP_IN_LIBRARY.name,
                )
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

    private suspend fun verifiedManagedFile(trackId: TrackId, expectedSize: Long?): File? {
        val file = fileStore.finalFile(trackId)
        if (fileStore.hasVerified(trackId, expectedSize)) return file
        val managedSourceId = "managed:${trackId.value}"
        database.withTransaction {
            val trackSourceDao = database.trackSourceDao()

            trackSourceDao.get(managedSourceId)?.let { source ->
                trackSourceDao.delete(source)
            }

            if (trackSourceDao.countForTrack(trackId.value) == 0) {
                database.trackDao().delete(trackId.value)
            }
        }
        return null
    }

    private fun strongerRetention(existing: RetentionPolicy?, requested: RetentionPolicy): RetentionPolicy = when {
        existing == RetentionPolicy.KEEP_IN_LIBRARY || requested == RetentionPolicy.KEEP_IN_LIBRARY -> RetentionPolicy.KEEP_IN_LIBRARY
        existing == RetentionPolicy.TEMPORARY_24_HOURS ||
            requested == RetentionPolicy.TEMPORARY_24_HOURS -> RetentionPolicy.TEMPORARY_24_HOURS

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

    @Suppress("UsableSpace")
    private fun requireSupportedSize(resolver: ContentResolver, uri: Uri) {
        val declaredSize = querySize(resolver, uri) ?: return
        require(declaredSize in 1..MAX_TRACK_BYTES) { "Audio files must be between 1 byte and 1 GiB" }
        val storageManager = context.getSystemService(StorageManager::class.java)
        val allocatableBytes = runCatching {
            storageManager.getAllocatableBytes(storageManager.getUuidForPath(context.filesDir))
        }.getOrNull()
        val availableBytes = allocatableBytes ?: context.filesDir.usableSpace
        require(availableBytes >= declaredSize + MIN_FREE_SPACE_BYTES) { "Not enough storage space" }
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun normalizeDescriptor(descriptor: TrackDescriptor): TrackDescriptor {
        require(descriptor.trackId.value.matches(TRACK_ID_PATTERN)) { "Invalid audio identity" }
        require(descriptor.sizeBytes in 1..MAX_TRACK_BYTES) { "Invalid audio size" }
        return descriptor.copy(
            mimeType = descriptor.mimeType.cleanText(MAX_MIME_LENGTH),
            durationMs = descriptor.durationMs.coerceIn(0, MAX_TRACK_DURATION_MS),
            title = descriptor.title.cleanText(MAX_METADATA_LENGTH),
            artist = descriptor.artist.cleanText(MAX_METADATA_LENGTH),
            album = descriptor.album.cleanText(MAX_METADATA_LENGTH),
            originalFileName = descriptor.originalFileName.cleanText(MAX_FILENAME_LENGTH),
        )
    }

    private fun String?.cleanText(maxLength: Int): String? = this
        ?.filterNot { it.isISOControl() }
        ?.trim()
        ?.take(maxLength)
        ?.ifBlank { null }

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
        } catch (_: Exception) {
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
        private const val MAX_TRACK_DURATION_MS = 30L * 24 * 60 * 60 * 1000
        private const val MAX_METADATA_LENGTH = 256
        private const val MAX_FILENAME_LENGTH = 512
        private const val MAX_MIME_LENGTH = 128
        private val TRACK_ID_PATTERN = Regex("[0-9a-f]{64}")
        private const val LIBRARY_PAGE_SIZE = 60
        private const val LIBRARY_INITIAL_LOAD_SIZE = 120
        private const val LIBRARY_PREFETCH_DISTANCE = 20
        private const val SQLITE_BIND_CHUNK_SIZE = 900
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
