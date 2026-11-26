package com.darius.unison.storage

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.darius.unison.model.TrackId
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.CancellationException

/** Removes expired temporary sources in bounded batches and never competes with an active room. */
class CacheCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
    private val database: UnisonDatabase,
    private val store: ManagedFileStore,
    private val roomActive: () -> Boolean,
    private val log: DiagnosticLog,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        try {
            if (roomActive()) return Result.success()
            val now = System.currentTimeMillis()
            val referencedTrackIds =
                database.trackSourceDao().referencedTrackIds().mapTo(hashSetOf(), ::TrackId)
            store.cleanupPendingDeletes(MAX_PENDING_DELETES_PER_RUN, referencedTrackIds)
            store.cleanupAbandonedFiles(now - STALE_PARTIAL_AGE_MS, MAX_TEMPORARY_FILES_PER_RUN)

            // Cleanup validates metadata (presence and size) only. Cryptographic verification
            // happens at import, transfer completion, and first playback after a change, not in a
            // periodic scan that could make the whole phone feel slow.
            var afterSourceId = ""
            while (true) {
                if (roomActive()) return Result.success()
                val page = database.trackSourceDao().sourcesAfter(afterSourceId, DATABASE_PAGE_SIZE)
                if (page.isEmpty()) break
                afterSourceId = page.last().sourceId
                page.forEach { source -> dropSourceIfFileMissing(source) }
            }

            val referenced = database.trackSourceDao().referencedTrackIds().toHashSet()
            store
                .storedTrackFiles()
                .asSequence()
                .filter { (trackId, file) ->
                    trackId.value !in referenced &&
                        !store.isLeased(trackId) &&
                        file.lastModified() in 1 until now - STALE_PARTIAL_AGE_MS
                }
                .take(MAX_ORPHAN_FILES_PER_RUN)
                .map { it.key }
                .forEach(store::delete)

            afterSourceId = ""
            while (true) {
                if (roomActive()) return Result.success()
                val page =
                    database.trackSourceDao().expiredAfter(now, afterSourceId, DATABASE_PAGE_SIZE)
                if (page.isEmpty()) break
                afterSourceId = page.last().sourceId
                page.forEach { candidate -> expireSource(candidate, now) }
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val retry = runAttemptCount < MAX_RETRY_ATTEMPTS
            log.warn(
                TAG,
                DiagnosticCategory.STORAGE,
                "storage.cleanup.failed",
                attributes =
                    mapOf("cleanup.attempt" to runAttemptCount + 1, "cleanup.retry" to retry),
                throwable = error,
            )
            if (retry) Result.retry() else Result.failure()
        }

    private suspend fun dropSourceIfFileMissing(source: TrackSourceEntity) {
        val trackId = TrackId(source.trackId)
        if (store.isLeased(trackId)) return
        val expectedSize = database.trackDao().get(source.trackId)?.sizeBytes
        if (store.hasStoredFile(trackId, expectedSize)) return
        database.withTransaction {
            database.trackSourceDao().get(source.sourceId)?.let {
                database.trackSourceDao().delete(it)
            }
            if (database.trackSourceDao().countForTrack(source.trackId) == 0) {
                database.trackDao().delete(source.trackId)
            }
        }
    }

    private suspend fun expireSource(candidate: TrackSourceEntity, now: Long) {
        var deleteBytes = false
        database.withTransaction {
            val current =
                database.trackSourceDao().get(candidate.sourceId) ?: return@withTransaction
            if (current.expiresAt == null || current.expiresAt > now) return@withTransaction
            database.trackSourceDao().delete(current)
            deleteBytes = database.trackSourceDao().countForTrack(current.trackId) == 0
            if (deleteBytes) database.trackDao().delete(current.trackId)
        }
        if (deleteBytes) store.requestDelete(TrackId(candidate.trackId))
    }

    private companion object {
        const val TAG = "CacheCleanupWorker"
        const val STALE_PARTIAL_AGE_MS = 48L * 60 * 60 * 1000
        const val MAX_RETRY_ATTEMPTS = 3
        const val DATABASE_PAGE_SIZE = 250
        const val MAX_ORPHAN_FILES_PER_RUN = 100
        const val MAX_TEMPORARY_FILES_PER_RUN = 250
        const val MAX_PENDING_DELETES_PER_RUN = 250
    }
}
