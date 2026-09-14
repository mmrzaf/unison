package com.darius.unison.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.TrackId
import com.darius.unison.storage.ManagedFileStore
import com.darius.unison.storage.UnisonDatabase
import com.darius.unison.util.DiagnosticLog
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackRepositoryAndroidTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var database: UnisonDatabase
    private lateinit var fileStore: ManagedFileStore
    private lateinit var log: DiagnosticLog

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        assertEquals(
            "audio/mpeg",
            context.contentResolver.getType(TestAudioContentProvider.uri("fixture-probe")),
        )
        root =
            File(context.filesDir, "track-repository-test-${UUID.randomUUID()}").apply {
                mkdirs()
            }
        database =
            Room.inMemoryDatabaseBuilder(context, UnisonDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        fileStore = ManagedFileStore(root)
        log = DiagnosticLog(File(root, "diagnostics.ndjson"))
    }

    @After
    fun tearDown() {
        database.close()
        log.close()
        root.deleteRecursively()
    }

    @Test
    fun unknownProviderSizeImportsThroughManagedStoreAndPublishesDatabaseSource() = runBlocking {
        val repository = repositoryWithAvailableBytes(128L * 1024L * 1024L)

        val descriptor =
            repository.importUri(
                TestAudioContentProvider.uri("unknown-size"),
                RetentionPolicy.KEEP_IN_LIBRARY,
            )

        assertEquals(TestAudioContentProvider.PAYLOAD_SIZE.toLong(), descriptor.sizeBytes)
        assertEquals("unknown-size.mp3", descriptor.originalFileName)
        val entity = database.trackDao().get(descriptor.trackId.value)
        assertNotNull(entity)
        assertEquals("unknown-size.mp3", entity?.sortTitle)
        assertEquals(entity?.createdAt, entity?.recentSortAt)
        val sources = database.trackSourceDao().getForTrack(descriptor.trackId.value)
        assertEquals(1, sources.size)
        assertEquals(RetentionPolicy.KEEP_IN_LIBRARY.name, sources.single().retentionPolicy)
        assertTrue(fileStore.hasVerified(descriptor.trackId, descriptor.sizeBytes))
    }

    @Test
    fun underreportedProviderCannotCrossStorageBudgetOrPublishPartialState() = runBlocking {
        val repository = repositoryWithAvailableBytes(MIN_FREE_SPACE_BYTES_FOR_TEST + 4_096L)
        val uri = TestAudioContentProvider.uri("underreported")
        val expectedTrackId = TrackId(sha256(TestAudioContentProvider.payload("underreported")))

        val failure = runCatching { repository.importUri(uri) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("File exceeds the allowed size", failure?.message)
        assertNull(database.trackDao().get(expectedTrackId.value))
        assertTrue(database.trackSourceDao().getForTrack(expectedTrackId.value).isEmpty())
        assertFalse(fileStore.finalFile(expectedTrackId).exists())
        assertTrue(root.walkTopDown().none { it.isFile && it.name.endsWith(".part") })
    }

    @Test
    fun underreportedProviderUsesActualStreamLengthWhenCapacityIsSufficient() = runBlocking {
        val repository = repositoryWithAvailableBytes(128L * 1024L * 1024L)

        val descriptor = repository.importUri(TestAudioContentProvider.uri("underreported"))

        assertEquals(TestAudioContentProvider.PAYLOAD_SIZE.toLong(), descriptor.sizeBytes)
        assertTrue(fileStore.hasVerified(descriptor.trackId, descriptor.sizeBytes))
    }

    @Test
    fun markPlayedAdvancesTheIndexedRecentSortKey() = runBlocking {
        val repository = repositoryWithAvailableBytes(128L * 1024L * 1024L)
        val descriptor = repository.importUri(TestAudioContentProvider.uri("recent-sort"))
        val before = requireNotNull(database.trackDao().get(descriptor.trackId.value))

        repository.markPlayed(descriptor.trackId)

        val after = requireNotNull(database.trackDao().get(descriptor.trackId.value))
        assertNotNull(after.lastPlayedAt)
        assertEquals(after.lastPlayedAt, after.recentSortAt)
        assertTrue(after.recentSortAt >= before.recentSortAt)
    }

    private fun repositoryWithAvailableBytes(bytes: Long): TrackRepository =
        TrackRepository(
            context = context,
            database = database,
            fileStore = fileStore,
            log = log,
            availableStorageBytes = { bytes },
        )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

    private companion object {
        const val MIN_FREE_SPACE_BYTES_FOR_TEST = 32L * 1024L * 1024L
    }
}
