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
        return try {
            val now = System.currentTimeMillis()
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
        } catch (_: Throwable) {
            Result.retry()
        } finally {
            database.close()
        }
    }
}
