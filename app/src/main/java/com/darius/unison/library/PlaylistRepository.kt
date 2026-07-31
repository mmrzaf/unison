package com.darius.unison.library

import androidx.room.withTransaction
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.storage.PlaylistEntity
import com.darius.unison.storage.PlaylistEntryEntity
import com.darius.unison.storage.PlaylistSummary
import com.darius.unison.storage.UnisonDatabase
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class PlaylistDetail(
    val playlistId: String,
    val name: String,
    val tracks: List<TrackDescriptor>,
)

class PlaylistRepository(
    private val database: UnisonDatabase,
    private val tracks: TrackRepository,
) {
    val playlists: Flow<List<PlaylistSummary>> = database.playlistDao().observeAll()

    suspend fun create(name: String, trackIds: List<TrackId>): String {
        val normalizedTrackIds = validateTrackIds(trackIds)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            database.playlistDao().upsert(PlaylistEntity(id, normalizeName(name), now, now))
            replaceTracksInTransaction(id, normalizedTrackIds, now)
        }
        return id
    }

    suspend fun rename(playlistId: String, name: String) {
        val current = database.playlistDao().get(playlistId) ?: return
        database
            .playlistDao()
            .upsert(
                current.copy(
                    name = normalizeName(name, current.name),
                    updatedAt = System.currentTimeMillis(),
                )
            )
    }

    suspend fun replaceTracks(playlistId: String, trackIds: List<TrackId>) {
        val normalizedTrackIds = validateTrackIds(trackIds)
        database.withTransaction {
            checkNotNull(database.playlistDao().get(playlistId)) { "Playlist does not exist" }
            replaceTracksInTransaction(playlistId, normalizedTrackIds, System.currentTimeMillis())
        }
    }

    suspend fun appendTracks(playlistId: String, trackIds: List<TrackId>) {
        if (trackIds.isEmpty()) return
        val dao = database.playlistDao()
        database.withTransaction {
            val playlist = checkNotNull(dao.get(playlistId)) { "Playlist does not exist" }
            val startPosition = dao.entryCount(playlistId)
            require(startPosition + trackIds.size <= MAX_PLAYLIST_TRACKS) {
                "A playlist can contain at most $MAX_PLAYLIST_TRACKS tracks"
            }
            tracks.keepMany(trackIds)
            dao.insertEntries(
                trackIds.mapIndexed { index, trackId ->
                    PlaylistEntryEntity(
                        entryId = UUID.randomUUID().toString(),
                        playlistId = playlistId,
                        trackId = trackId.value,
                        position = startPosition + index,
                    )
                }
            )
            dao.upsert(playlist.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun moveTrack(playlistId: String, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val dao = database.playlistDao()
        database.withTransaction {
            val playlist = checkNotNull(dao.get(playlistId)) { "Playlist does not exist" }
            val entries = dao.entries(playlistId)
            require(fromIndex in entries.indices && toIndex in entries.indices) {
                "Invalid playlist position"
            }
            val mutable = entries.toMutableList()
            val direction = if (toIndex > fromIndex) 1 else -1
            var position = fromIndex
            while (position != toIndex) {
                val next = position + direction
                swapEntryPositions(dao, mutable[position], mutable[next])
                val moved = mutable[position]
                mutable[position] = mutable[next]
                mutable[next] = moved
                position = next
            }
            dao.upsert(playlist.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun removeTracksAt(playlistId: String, indices: Collection<Int>) {
        if (indices.isEmpty()) return
        val dao = database.playlistDao()
        database.withTransaction {
            val playlist = checkNotNull(dao.get(playlistId)) { "Playlist does not exist" }
            val count = dao.entryCount(playlistId)
            val normalized = indices.asSequence().distinct().sortedDescending().toList()
            require(normalized.all { it in 0 until count }) { "Invalid playlist position" }
            normalized.forEach { position ->
                dao.deleteEntryAt(playlistId, position)
                dao.parkEntriesAfterRemoval(playlistId, position)
                dao.restoreEntriesAfterRemoval(playlistId)
            }
            dao.upsert(playlist.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    private suspend fun swapEntryPositions(
        dao: com.darius.unison.storage.PlaylistDao,
        first: PlaylistEntryEntity,
        second: PlaylistEntryEntity,
    ) {
        dao.updateEntryPosition(first.entryId, TEMPORARY_POSITION)
        dao.updateEntryPosition(second.entryId, first.position)
        dao.updateEntryPosition(first.entryId, second.position)
    }

    private suspend fun replaceTracksInTransaction(
        playlistId: String,
        trackIds: List<TrackId>,
        updatedAt: Long,
    ) {
        tracks.keepMany(trackIds)
        database
            .playlistDao()
            .replaceEntries(
                playlistId,
                trackIds.mapIndexed { index, id ->
                    PlaylistEntryEntity(
                        UUID.randomUUID().toString(),
                        playlistId,
                        id.value,
                        index,
                    )
                },
            )
        val playlist =
            checkNotNull(database.playlistDao().get(playlistId)) { "Playlist does not exist" }
        database.playlistDao().upsert(playlist.copy(updatedAt = updatedAt))
    }

    private fun validateTrackIds(trackIds: List<TrackId>): List<TrackId> {
        require(trackIds.size <= MAX_PLAYLIST_TRACKS) {
            "A playlist can contain at most $MAX_PLAYLIST_TRACKS tracks"
        }
        return trackIds
    }

    private fun normalizeName(name: String, fallback: String = "Playlist"): String =
        name
            .filterNot { it.isISOControl() }
            .trim()
            .take(MAX_PLAYLIST_NAME_LENGTH)
            .ifBlank { fallback.take(MAX_PLAYLIST_NAME_LENGTH) }

    suspend fun get(playlistId: String): PlaylistDetail? {
        val entity = database.playlistDao().get(playlistId) ?: return null
        val entries = database.playlistDao().entries(playlistId)
        val descriptorsById =
            tracks.getMany(entries.map { TrackId(it.trackId) }).associateBy { it.trackId }
        return PlaylistDetail(
            entity.playlistId,
            entity.name,
            entries.mapNotNull { descriptorsById[TrackId(it.trackId)] },
        )
    }

    suspend fun delete(playlistId: String) = database.playlistDao().delete(playlistId)

    private companion object {
        const val MAX_PLAYLIST_TRACKS = 10_000
        const val MAX_PLAYLIST_NAME_LENGTH = 128
        const val TEMPORARY_POSITION = -1
    }
}
