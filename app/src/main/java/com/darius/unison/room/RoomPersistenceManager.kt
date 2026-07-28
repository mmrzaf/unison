package com.darius.unison.room

import com.darius.unison.storage.RoomSnapshotDao
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.CancellationException

/**
 * Removes room snapshots written by older builds.
 *
 * A room cannot be restored safely without its ephemeral room secret, authenticated sockets,
 * coordinator term context, and player state. Persisting only the canonical snapshot created a
 * misleading half-restoration path, so current builds deliberately keep sessions in memory only.
 */
internal class RoomPersistenceManager(
    private val dao: RoomSnapshotDao,
    private val log: DiagnosticLog,
) {
    suspend fun discardLegacySnapshots() {
        try {
            dao.deleteAll()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.w(TAG, "Legacy room snapshot cleanup failed", error)
        }
    }

    private companion object {
        const val TAG = "RoomPersistence"
    }
}
