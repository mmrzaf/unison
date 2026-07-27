package com.darius.unison.storage

import android.content.Context
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
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val trackId: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val durationMs: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val originalFileName: String?,
    val createdAt: Long,
    val lastPlayedAt: Long?,
)

@Entity(
    tableName = "track_sources",
    foreignKeys = [ForeignKey(
        entity = TrackEntity::class,
        parentColumns = ["trackId"],
        childColumns = ["trackId"],
        onDelete = ForeignKey.CASCADE,
    )],
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

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("playlistId"), Index("trackId"), Index(value = ["playlistId", "position"], unique = true)],
)
data class PlaylistEntryEntity(
    @PrimaryKey val entryId: String,
    val playlistId: String,
    val trackId: String,
    val position: Int,
)

@Entity(tableName = "room_snapshots")
data class RoomSnapshotEntity(
    @PrimaryKey val roomId: String,
    val serializedSnapshot: String,
    val updatedAt: Long,
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY COALESCE(lastPlayedAt, createdAt) DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE trackId = :trackId")
    suspend fun get(trackId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE trackId IN (:trackIds)")
    suspend fun getMany(trackIds: List<String>): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(track: TrackEntity): Long

    @Update
    suspend fun update(track: TrackEntity)

    @Upsert
    suspend fun upsert(track: TrackEntity)

    @Query("UPDATE tracks SET lastPlayedAt = :timestamp WHERE trackId = :trackId")
    suspend fun markPlayed(trackId: String, timestamp: Long)

    @Query("DELETE FROM tracks WHERE trackId = :trackId")
    suspend fun delete(trackId: String)
}

@Dao
interface TrackSourceDao {
    @Query("SELECT * FROM track_sources WHERE trackId = :trackId ORDER BY verified DESC, CASE retentionPolicy WHEN 'KEEP_IN_LIBRARY' THEN 0 WHEN 'TEMPORARY_24_HOURS' THEN 1 ELSE 2 END")
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: TrackSourceEntity)

    @Query("UPDATE track_sources SET retentionPolicy = :policy, expiresAt = :expiresAt WHERE sourceId = :sourceId")
    suspend fun updateRetention(sourceId: String, policy: String, expiresAt: Long?)

    @Query("UPDATE track_sources SET expiresAt = :expiresAt WHERE trackId = :trackId AND retentionPolicy = 'TEMPORARY_24_HOURS'")
    suspend fun extendTemporary(trackId: String, expiresAt: Long)

    @Delete
    suspend fun delete(source: TrackSourceEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM track_sources WHERE trackId = :trackId AND verified = 1)")
    suspend fun hasVerified(trackId: String): Boolean

    @Query("SELECT COUNT(*) FROM track_sources WHERE trackId = :trackId")
    suspend fun countForTrack(trackId: String): Int

    @Query("SELECT COUNT(*) FROM track_sources WHERE trackId = :trackId AND managedRelativePath IS NOT NULL")
    suspend fun managedCountForTrack(trackId: String): Int
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    suspend fun get(playlistId: String): PlaylistEntity?

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun delete(playlistId: String)

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position")
    suspend fun entries(playlistId: String): List<PlaylistEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: String)

    @Transaction
    suspend fun replaceEntries(playlistId: String, entries: List<PlaylistEntryEntity>) {
        clearEntries(playlistId)
        insertEntries(entries)
    }
}

@Dao
interface RoomSnapshotDao {
    @Query("SELECT * FROM room_snapshots WHERE roomId = :roomId")
    suspend fun get(roomId: String): RoomSnapshotEntity?

    @Query("SELECT * FROM room_snapshots ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(): RoomSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: RoomSnapshotEntity)

    @Query("DELETE FROM room_snapshots WHERE roomId = :roomId")
    suspend fun delete(roomId: String)
}

@Database(
    entities = [TrackEntity::class, TrackSourceEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class, RoomSnapshotEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class UnisonDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun trackSourceDao(): TrackSourceDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun roomSnapshotDao(): RoomSnapshotDao

    companion object {
        fun create(context: Context): UnisonDatabase = Room.databaseBuilder(
            context.applicationContext,
            UnisonDatabase::class.java,
            "unison.db",
        ).build()
    }
}
