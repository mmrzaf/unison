package com.darius.unison.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darius.unison.model.TrackId
import com.darius.unison.storage.ManagedFileStore
import com.darius.unison.storage.PlaylistEntity
import com.darius.unison.storage.PlaylistEntryEntity
import com.darius.unison.storage.TrackEntity
import com.darius.unison.storage.UnisonDatabase
import com.darius.unison.util.DiagnosticLog
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistRepositoryAndroidTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var database: UnisonDatabase
    private lateinit var log: DiagnosticLog
    private lateinit var repository: PlaylistRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        root =
            File(context.filesDir, "playlist-repository-test-${UUID.randomUUID()}").apply {
                mkdirs()
            }
        log = DiagnosticLog(File(root, "diagnostics.ndjson"))
        database = newInMemoryDatabase()
        repository = newRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(PERSISTENCE_DATABASE_NAME)
        log.close()
        root.deleteRecursively()
    }

    @Test
    fun longDistanceMovesPreserveUniqueContiguousPositions() = runBlocking {
        seedPlaylist(5)

        repository.moveTrack(PLAYLIST_ID, 0, 4)
        assertOrder(listOf("track-1", "track-2", "track-3", "track-4", "track-0"))

        repository.moveTrack(PLAYLIST_ID, 4, 1)
        assertOrder(listOf("track-1", "track-0", "track-2", "track-3", "track-4"))

        repository.moveTrack(PLAYLIST_ID, 2, 3)
        assertOrder(listOf("track-1", "track-0", "track-3", "track-2", "track-4"))

        assertContiguousPositions(5)
    }

    @Test
    fun reorderPersistsAcrossDatabaseCloseAndReopen() = runBlocking {
        database.close()
        context.deleteDatabase(PERSISTENCE_DATABASE_NAME)
        database = newPersistentDatabase()
        repository = newRepository(database)
        seedPlaylist(6)

        repository.moveTrack(PLAYLIST_ID, 5, 1)
        repository.moveTrack(PLAYLIST_ID, 0, 4)
        val expected = listOf("track-5", "track-1", "track-2", "track-3", "track-0", "track-4")
        assertOrder(expected)
        database.close()

        database = newPersistentDatabase()
        repository = newRepository(database)
        assertOrder(expected)
        assertContiguousPositions(6)
    }

    @Test
    fun appendAndRemoveMaintainOrderAndContiguousPositions() = runBlocking {
        seedTracks(5)
        val playlistId =
            repository.create(
                "  Integration Playlist  ",
                listOf(TrackId("track-0"), TrackId("track-1"), TrackId("track-2")),
            )

        repository.appendTracks(playlistId, listOf(TrackId("track-3"), TrackId("track-4")))
        repository.removeTracksAt(playlistId, listOf(1, 3))

        assertEquals(
            listOf("track-0", "track-2", "track-4"),
            database.playlistDao().entries(playlistId).map { it.trackId },
        )
        assertEquals(
            listOf(0, 1, 2),
            database.playlistDao().entries(playlistId).map { it.position },
        )
        assertEquals("Integration Playlist", database.playlistDao().get(playlistId)?.name)
    }

    @Test
    fun samePositionMoveIsNoOp() = runBlocking {
        seedPlaylist(3)
        repository.moveTrack(PLAYLIST_ID, 1, 1)
        assertOrder(listOf("track-0", "track-1", "track-2"))
    }

    @Test
    fun invalidMoveRollsBackWithoutChangingOrder() = runBlocking {
        seedPlaylist(3)
        runCatching { repository.moveTrack(PLAYLIST_ID, 0, 3) }
        assertOrder(listOf("track-0", "track-1", "track-2"))
        assertContiguousPositions(3)
    }

    private fun newInMemoryDatabase(): UnisonDatabase =
        Room.inMemoryDatabaseBuilder(context, UnisonDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun newPersistentDatabase(): UnisonDatabase =
        Room.databaseBuilder(context, UnisonDatabase::class.java, PERSISTENCE_DATABASE_NAME)
            .allowMainThreadQueries()
            .build()

    private fun newRepository(database: UnisonDatabase): PlaylistRepository =
        PlaylistRepository(
            database,
            TrackRepository(
                context = context,
                database = database,
                fileStore = ManagedFileStore(root),
                log = log,
            ),
        )

    private suspend fun seedPlaylist(count: Int) {
        seedTracks(count)
        val now = 1L
        database.playlistDao().upsert(PlaylistEntity(PLAYLIST_ID, "Test", now, now))
        database
            .playlistDao()
            .insertEntries(
                List(count) { index ->
                    PlaylistEntryEntity(
                        entryId = "entry-$index",
                        playlistId = PLAYLIST_ID,
                        trackId = TrackId("track-$index").value,
                        position = index,
                    )
                }
            )
    }

    private suspend fun seedTracks(count: Int) {
        val now = 1L
        repeat(count) { index ->
            database
                .trackDao()
                .upsert(
                    TrackEntity(
                        trackId = "track-$index",
                        sizeBytes = 1L,
                        mimeType = "audio/test",
                        durationMs = 1L,
                        title = "Track $index",
                        artist = null,
                        album = null,
                        originalFileName = "track-$index.test",
                        searchText = "track $index",
                        sortTitle = "track $index",
                        sortArtist = "",
                        sortAlbum = "",
                        recentSortAt = now,
                        createdAt = now,
                        lastPlayedAt = null,
                    )
                )
        }
    }

    private suspend fun assertOrder(expected: List<String>) {
        assertEquals(expected, database.playlistDao().entries(PLAYLIST_ID).map { it.trackId })
    }

    private suspend fun assertContiguousPositions(count: Int) {
        assertEquals(
            (0 until count).toList(),
            database.playlistDao().entries(PLAYLIST_ID).map { it.position },
        )
    }

    private companion object {
        const val PLAYLIST_ID = "playlist-test"
        const val PERSISTENCE_DATABASE_NAME = "playlist-repository-integration.db"
    }
}
