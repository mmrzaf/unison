package com.darius.unison.storage

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.darius.unison.model.TrackId
import kotlinx.coroutines.CancellationException

/** Removes expired temporary sources without deleting a file still referenced by another source. */
class CacheCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val database = UnisonDatabase.create(applicationContext)
        val store = ManagedFileStore(applicationContext)
        val artwork = ArtworkStore(applicationContext.cacheDir)
        return try {
            val now = System.currentTimeMillis()
            store.cleanupAbandonedFiles(now - STALE_PARTIAL_AGE_MS)
            artwork.cleanup(now - STALE_ARTWORK_AGE_MS)
            database.trackSourceDao().managedSources().forEach { source ->
                val trackId = TrackId(source.trackId)
                if (!store.finalFile(trackId).isFile) {
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
            store.storedTrackFiles()
                .filter { (trackId, file) -> trackId.value !in referenced && file.lastModified() in 1 until now - STALE_PARTIAL_AGE_MS }
                .keys
                .forEach(store::delete)
            database.trackSourceDao().expired(now).forEach { candidate ->
                var deleteManagedBytes = false
                database.withTransaction {
                    val current = database.trackSourceDao().get(candidate.sourceId) ?: return@withTransaction
                    if (current.expiresAt == null || current.expiresAt > now) return@withTransaction
                    database.trackSourceDao().delete(current)
                    val remainingSources = database.trackSourceDao().countForTrack(current.trackId)
                    val remainingManagedSources = database.trackSourceDao().managedCountForTrack(current.trackId)
                    deleteManagedBytes = current.managedRelativePath != null && remainingManagedSources == 0
                    if (remainingSources == 0) database.trackDao().delete(current.trackId)
                }
                if (deleteManagedBytes) store.delete(TrackId(candidate.trackId))
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        } finally {
            database.close()
        }
    }

    private companion object {
        const val STALE_PARTIAL_AGE_MS = 48L * 60 * 60 * 1000
        const val STALE_ARTWORK_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
