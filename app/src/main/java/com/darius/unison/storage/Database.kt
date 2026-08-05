package com.darius.unison.storage

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "tracks",
    indices =
        [
            Index("createdAt"),
            Index("lastPlayedAt"),
            Index("title"),
            Index("artist"),
            Index("album"),
            Index("originalFileName"),
            Index("searchText"),
        ],
)
data class TrackEntity(
    @PrimaryKey val trackId: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val durationMs: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val originalFileName: String?,
    val searchText: String,
    val createdAt: Long,
    val lastPlayedAt: Long?,
)

@Entity(
    tableName = "track_sources",
    foreignKeys =
        [
            ForeignKey(
                entity = TrackEntity::class,
                parentColumns = ["trackId"],
                childColumns = ["trackId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("trackId"), Index("expiresAt")],
)
data class TrackSourceEntity(
    @PrimaryKey val sourceId: String,
    val trackId: String,
    val sourceType: String,
    val uri: String?,
    val managedRelativePath: String?,
    val retentionPolicy: String,
    val verified: Boolean,
    val lastVerifiedAt: Long?,
    val expiresAt: Long?,
)

@Entity(tableName = "playlists", indices = [Index("updatedAt")])
data class PlaylistEntity(
    @PrimaryKey val playlistId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class PlaylistSummary(
    val playlistId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int,
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys =
        [
            ForeignKey(
                entity = PlaylistEntity::class,
                parentColumns = ["playlistId"],
                childColumns = ["playlistId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = TrackEntity::class,
                parentColumns = ["trackId"],
                childColumns = ["trackId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices =
        [
            Index("playlistId"),
            Index("trackId"),
            Index(value = ["playlistId", "position"], unique = true),
        ],
)
data class PlaylistEntryEntity(
    @PrimaryKey val entryId: String,
    val playlistId: String,
    val trackId: String,
    val position: Int,
)

@Dao
interface TrackDao {
    @Query(
        """
        SELECT * FROM tracks
        WHERE :query = '' OR searchText LIKE '%' || :query || '%' ESCAPE '!'
        ORDER BY COALESCE(lastPlayedAt, createdAt) DESC, trackId ASC
        """
    )
    fun pagingRecent(query: String): PagingSource<Int, TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE :query = '' OR searchText LIKE '%' || :query || '%' ESCAPE '!'
        ORDER BY LOWER(COALESCE(title, originalFileName, '')) ASC, trackId ASC
        """
    )
    fun pagingByTitle(query: String): PagingSource<Int, TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE :query = '' OR searchText LIKE '%' || :query || '%' ESCAPE '!'
        ORDER BY LOWER(COALESCE(artist, '')) ASC, LOWER(COALESCE(title, originalFileName, '')) ASC, trackId ASC
        """
    )
    fun pagingByArtist(query: String): PagingSource<Int, TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE :query = '' OR searchText LIKE '%' || :query || '%' ESCAPE '!'
        ORDER BY LOWER(COALESCE(album, '')) ASC, LOWER(COALESCE(title, originalFileName, '')) ASC, trackId ASC
        """
    )
    fun pagingByAlbum(query: String): PagingSource<Int, TrackEntity>

    @Query(
        """
        SELECT COUNT(*) FROM tracks
        WHERE :query = '' OR searchText LIKE '%' || :query || '%' ESCAPE '!'
        """
    )
    fun observeLibraryCount(query: String): Flow<Int>

    @Query(
        """
        SELECT trackId FROM tracks
        WHERE :query = '' OR searchText LIKE '%' || :query || '%' ESCAPE '!'
        ORDER BY LOWER(COALESCE(title, originalFileName, '')) ASC, trackId ASC
        LIMIT :limit
        """
    )
    suspend fun libraryTrackIds(query: String, limit: Int): List<String>

    @Query(
        """
        SELECT * FROM tracks
        WHERE LOWER(COALESCE(originalFileName, '')) = LOWER(:fileName)
           OR LOWER(COALESCE(title, '')) = LOWER(:title)
        ORDER BY createdAt DESC, trackId ASC
        """
    )
    suspend fun findReferenceCandidates(fileName: String, title: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE trackId = :trackId")
    suspend fun get(trackId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE trackId IN (:trackIds)")
    suspend fun getMany(trackIds: List<String>): List<TrackEntity>

    @Upsert suspend fun upsert(track: TrackEntity)

    @Query("UPDATE tracks SET lastPlayedAt = :timestamp WHERE trackId = :trackId")
    suspend fun markPlayed(trackId: String, timestamp: Long)

    @Query("DELETE FROM tracks WHERE trackId = :trackId") suspend fun delete(trackId: String)

    @Query("DELETE FROM tracks WHERE trackId IN (:trackIds)")
    suspend fun deleteMany(trackIds: List<String>)
}

@Dao
interface TrackSourceDao {
    @Query(
        "SELECT * FROM track_sources WHERE trackId = :trackId ORDER BY verified DESC, " +
            "CASE retentionPolicy WHEN 'KEEP_IN_LIBRARY' THEN 0 " +
            "WHEN 'TEMPORARY_24_HOURS' THEN 1 ELSE 2 END"
    )
    suspend fun getForTrack(trackId: String): List<TrackSourceEntity>

    @Query("SELECT * FROM track_sources WHERE sourceId = :sourceId")
    suspend fun get(sourceId: String): TrackSourceEntity?

    @Query("SELECT * FROM track_sources WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun expired(now: Long): List<TrackSourceEntity>

    @Query("SELECT trackId FROM track_sources WHERE retentionPolicy = 'TEMPORARY_24_HOURS'")
    fun observeTemporaryTrackIds(): Flow<List<String>>

    @Query("SELECT * FROM track_sources WHERE retentionPolicy = 'TEMPORARY_24_HOURS'")
    suspend fun temporarySources(): List<TrackSourceEntity>

    @Query(
        "SELECT DISTINCT trackId FROM track_sources " +
            "WHERE retentionPolicy = 'TEMPORARY_24_HOURS' AND trackId > :afterTrackId " +
            "ORDER BY trackId LIMIT :limit"
    )
    suspend fun temporaryTrackIdsAfter(afterTrackId: String, limit: Int): List<String>

    @Query(
        "DELETE FROM track_sources WHERE retentionPolicy = 'TEMPORARY_24_HOURS' " +
            "AND trackId IN (:trackIds)"
    )
    suspend fun deleteTemporaryForTracks(trackIds: List<String>)

    @Query("SELECT DISTINCT trackId FROM track_sources WHERE trackId IN (:trackIds)")
    suspend fun remainingTrackIds(trackIds: List<String>): List<String>

    @Query(
        "SELECT DISTINCT trackId FROM track_sources " +
            "WHERE trackId IN (:trackIds) AND managedRelativePath IS NOT NULL"
    )
    suspend fun remainingManagedTrackIds(trackIds: List<String>): List<String>

    @Query("SELECT * FROM track_sources WHERE managedRelativePath IS NOT NULL")
    suspend fun managedSources(): List<TrackSourceEntity>

    @Query("SELECT trackId FROM track_sources WHERE managedRelativePath IS NOT NULL")
    suspend fun managedTrackIds(): List<String>

    @Query(
        """
        SELECT COALESCE(SUM(sizeBytes), 0) FROM tracks
        WHERE trackId IN (
            SELECT trackId FROM track_sources
            WHERE managedRelativePath IS NOT NULL
        )
    """
    )
    fun observeManagedBytes(): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(sizeBytes), 0) FROM tracks
        WHERE trackId IN (
            SELECT trackId FROM track_sources
            WHERE managedRelativePath IS NOT NULL AND retentionPolicy = 'KEEP_IN_LIBRARY'
        )
    """
    )
    fun observeKeptBytes(): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(sizeBytes), 0) FROM tracks
        WHERE trackId IN (
            SELECT trackId FROM track_sources
            WHERE managedRelativePath IS NOT NULL AND retentionPolicy = 'TEMPORARY_24_HOURS'
        )
    """
    )
    fun observeTemporaryBytes(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(source: TrackSourceEntity)

    @Query(
        "UPDATE track_sources SET retentionPolicy = :policy, expiresAt = :expiresAt WHERE sourceId = :sourceId"
    )
    suspend fun updateRetention(sourceId: String, policy: String, expiresAt: Long?)

    @Query(
        """
        UPDATE track_sources
        SET retentionPolicy = :policy, expiresAt = NULL
        WHERE trackId IN (:trackIds) AND sourceType = :sourceType
        """
    )
    suspend fun updateRetentionForTracks(
        trackIds: List<String>,
        sourceType: String,
        policy: String,
    )

    @Query(
        "UPDATE track_sources SET expiresAt = :expiresAt WHERE trackId = :trackId AND retentionPolicy = 'TEMPORARY_24_HOURS'"
    )
    suspend fun extendTemporary(trackId: String, expiresAt: Long)

    @Delete suspend fun delete(source: TrackSourceEntity)

    @Query("SELECT COUNT(*) FROM track_sources WHERE trackId = :trackId")
    suspend fun countForTrack(trackId: String): Int

    @Query(
        "SELECT COUNT(*) FROM track_sources WHERE trackId = :trackId AND managedRelativePath IS NOT NULL"
    )
    suspend fun managedCountForTrack(trackId: String): Int
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.playlistId, p.name, p.createdAt, p.updatedAt, COUNT(e.entryId) AS trackCount
        FROM playlists AS p
        LEFT JOIN playlist_entries AS e ON e.playlistId = p.playlistId
        GROUP BY p.playlistId
        ORDER BY p.updatedAt DESC
        """
    )
    fun observeAll(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    suspend fun get(playlistId: String): PlaylistEntity?

    @Upsert suspend fun upsert(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun delete(playlistId: String)

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position")
    suspend fun entries(playlistId: String): List<PlaylistEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: String)

    @Query("SELECT COUNT(*) FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun entryCount(playlistId: String): Int

    @Query("UPDATE playlist_entries SET position = :position WHERE entryId = :entryId")
    suspend fun updateEntryPosition(entryId: String, position: Int)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND position = :position")
    suspend fun deleteEntryAt(playlistId: String, position: Int)

    @Query(
        "UPDATE playlist_entries SET position = -position - 1 " +
            "WHERE playlistId = :playlistId AND position > :removedPosition"
    )
    suspend fun parkEntriesAfterRemoval(playlistId: String, removedPosition: Int)

    @Query(
        "UPDATE playlist_entries SET position = -position - 2 " +
            "WHERE playlistId = :playlistId AND position < 0"
    )
    suspend fun restoreEntriesAfterRemoval(playlistId: String)

    @Transaction
    suspend fun replaceEntries(playlistId: String, entries: List<PlaylistEntryEntity>) {
        clearEntries(playlistId)
        insertEntries(entries)
    }
}

@Database(
    entities =
        [
            TrackEntity::class,
            TrackSourceEntity::class,
            PlaylistEntity::class,
            PlaylistEntryEntity::class,
        ],
    version = 1,
    exportSchema = true,
)
abstract class UnisonDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    abstract fun trackSourceDao(): TrackSourceDao

    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun create(context: Context): UnisonDatabase =
            Room.databaseBuilder(
                    context.applicationContext,
                    UnisonDatabase::class.java,
                    "unison-1.db",
                )
                .build()
    }
}
