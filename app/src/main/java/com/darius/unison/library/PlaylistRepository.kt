package com.darius.unison.library

import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.storage.PlaylistEntity
import com.darius.unison.storage.PlaylistEntryEntity
import com.darius.unison.storage.UnisonDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class PlaylistSummary(val playlistId: String, val name: String, val updatedAt: Long)
data class PlaylistDetail(val playlistId: String, val name: String, val tracks: List<TrackDescriptor>)

class PlaylistRepository(
    private val database: UnisonDatabase,
    private val tracks: TrackRepository,
) {
    val playlists: Flow<List<PlaylistEntity>> = database.playlistDao().observeAll()

    suspend fun create(name: String, trackIds: List<TrackId>): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        database.playlistDao().upsert(PlaylistEntity(id, name.trim().ifBlank { "Playlist" }, now, now))
        replaceTracks(id, trackIds)
        return id
    }

    suspend fun rename(playlistId: String, name: String) {
        val current = database.playlistDao().get(playlistId) ?: return
        database.playlistDao()
            .upsert(current.copy(name = name.trim().ifBlank { current.name }, updatedAt = System.currentTimeMillis()))
    }

    suspend fun replaceTracks(playlistId: String, trackIds: List<TrackId>) {
        trackIds.distinct().forEach { tracks.keep(it) }
        database.playlistDao().replaceEntries(
            playlistId,
            trackIds.mapIndexed { index, id ->
                PlaylistEntryEntity(
                    UUID.randomUUID().toString(),
                    playlistId,
                    id.value,
                    index
                )
            }
        )
        database.playlistDao().get(playlistId)
            ?.let { database.playlistDao().upsert(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    suspend fun get(playlistId: String): PlaylistDetail? {
        val entity = database.playlistDao().get(playlistId) ?: return null
        val entries = database.playlistDao().entries(playlistId)
        val descriptorsById = tracks.getMany(entries.map { TrackId(it.trackId) }).associateBy { it.trackId }
        return PlaylistDetail(
            entity.playlistId,
            entity.name,
            entries.mapNotNull { descriptorsById[TrackId(it.trackId)] })
    }

    suspend fun delete(playlistId: String) = database.playlistDao().delete(playlistId)
}
