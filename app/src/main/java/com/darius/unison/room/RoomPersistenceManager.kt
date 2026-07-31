package com.darius.unison.room

import com.darius.unison.storage.RoomSnapshotDao
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.CancellationException

/**
 * Ensures sessions remain memory-only. A room cannot be restored safely without its ephemeral room
 * secret, authenticated sockets, coordinator term context, and player state.
 */
internal class RoomPersistenceManager(
    private val dao: RoomSnapshotDao,
    private val log: DiagnosticLog,
) {
    suspend fun discardPersistedSnapshots() {
        try {
            dao.deleteAll()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.w(TAG, "Persisted room snapshot cleanup failed", error)
        }
    }

    private companion object {
        const val TAG = "RoomPersistence"
    }
}
