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
            store.cleanupAbandonedFiles(now - STALE_PARTIAL_AGE_MS, MAX_TEMPORARY_FILES_PER_RUN)

            // Cleanup validates metadata only. Cryptographic verification happens at import,
            // transfer
            // completion, first playback after a change, and explicit deep verification—not in a
            // broad
            // periodic scan that can make the whole phone feel slow.
            database.trackSourceDao().managedSources().take(MAX_DATABASE_ROWS_PER_RUN).forEach {
                source ->
                if (roomActive()) return Result.success()
                val trackId = TrackId(source.trackId)
                if (store.isLeased(trackId)) return@forEach
                if (!store.hasStoredFile(trackId, source.expectedSizeOrNull(database))) {
                    database.withTransaction {
                        database.trackSourceDao().get(source.sourceId)?.let { currentSource ->
                            database.trackSourceDao().delete(currentSource)
                        }
                        if (database.trackSourceDao().countForTrack(source.trackId) == 0) {
                            database.trackDao().delete(source.trackId)
                        }
                    }
                }
            }

            val referenced = database.trackSourceDao().managedTrackIds().toHashSet()
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

            database.trackSourceDao().expired(now).take(MAX_DATABASE_ROWS_PER_RUN).forEach {
                candidate ->
                if (roomActive()) return Result.success()
                val trackId = TrackId(candidate.trackId)
                if (store.isLeased(trackId)) return@forEach
                var deleteManagedBytes = false
                database.withTransaction {
                    val current =
                        database.trackSourceDao().get(candidate.sourceId) ?: return@withTransaction
                    if (current.expiresAt == null || current.expiresAt > now) return@withTransaction
                    database.trackSourceDao().delete(current)
                    val remainingSources = database.trackSourceDao().countForTrack(current.trackId)
                    val remainingManagedSources =
                        database.trackSourceDao().managedCountForTrack(current.trackId)
                    deleteManagedBytes =
                        current.managedRelativePath != null && remainingManagedSources == 0
                    if (remainingSources == 0) database.trackDao().delete(current.trackId)
                }
                if (deleteManagedBytes) store.delete(trackId)
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
                attributes = mapOf("cleanup.attempt" to runAttemptCount + 1, "cleanup.retry" to retry),
                throwable = error,
            )
            if (retry) Result.retry() else Result.failure()
        }

    private suspend fun TrackSourceEntity.expectedSizeOrNull(database: UnisonDatabase): Long? =
        database.trackDao().get(trackId)?.sizeBytes

    private companion object {
        const val TAG = "CacheCleanupWorker"
        const val STALE_PARTIAL_AGE_MS = 48L * 60 * 60 * 1000
        const val MAX_RETRY_ATTEMPTS = 3
        const val MAX_DATABASE_ROWS_PER_RUN = 250
        const val MAX_ORPHAN_FILES_PER_RUN = 100
        const val MAX_TEMPORARY_FILES_PER_RUN = 250
    }
}
