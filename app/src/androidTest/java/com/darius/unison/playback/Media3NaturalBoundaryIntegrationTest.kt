package com.darius.unison.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.util.DiagnosticLog
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises natural-boundary attribution against the actual pinned Media3 ExoPlayer rather than a
 * callback-order fake. These tests guard the dual END_OF_MEDIA_ITEM / media-item-transition paths
 * in [Media3PlayerAdapter].
 */
@RunWith(AndroidJUnit4::class)
class Media3NaturalBoundaryIntegrationTest {
    @Test
    fun twoItemBoundaryRemainsOwnedByFirstAcrossResumeTransition() = runBlocking {
        withAdapter { fixture ->
            val first = fixture.item("first", durationMs = 1_300)
            val second = fixture.item("second", durationMs = 4_000)
            fixture.adapter.setQueue(listOf(first, second), first.queueItemId, 0)

            val startRevision = fixture.adapter.state.value.itemBoundaryRevision
            assertTrue(fixture.adapter.play())
            val boundary = fixture.awaitBoundaryAfter(startRevision)
            assertEquals(first.queueItemId, boundary.boundaryEndedQueueItemId)

            val transitionRevision = boundary.itemTransitionRevision
            assertTrue(fixture.adapter.play())
            val transitioned =
                withTimeout(BOUNDARY_TIMEOUT_MS) {
                    fixture.adapter.state.first {
                        it.queueItemId == second.queueItemId &&
                            it.itemTransitionRevision > transitionRevision
                    }
                }
            assertEquals(second.queueItemId, transitioned.queueItemId)

            // The resume transition may report AUTO for the item that already ended, but must not
            // manufacture a second boundary or misattribute that boundary to the newly selected
            // item.
            delay(NO_DUPLICATE_SETTLE_MS)
            assertEquals(
                boundary.itemBoundaryRevision,
                fixture.adapter.state.value.itemBoundaryRevision,
            )
            assertEquals(first.queueItemId, fixture.adapter.state.value.boundaryEndedQueueItemId)
        }
    }

    @Test
    fun finalItemProducesExactlyOneBoundary() = runBlocking {
        withAdapter { fixture ->
            val only = fixture.item("only", durationMs = 1_300)
            fixture.adapter.setQueue(listOf(only), only.queueItemId, 0)

            val startRevision = fixture.adapter.state.value.itemBoundaryRevision
            assertTrue(fixture.adapter.play())
            val boundary = fixture.awaitBoundaryAfter(startRevision)
            assertEquals(only.queueItemId, boundary.boundaryEndedQueueItemId)

            delay(NO_DUPLICATE_SETTLE_MS)
            assertEquals(
                boundary.itemBoundaryRevision,
                fixture.adapter.state.value.itemBoundaryRevision,
            )
            assertEquals(only.queueItemId, fixture.adapter.state.value.boundaryEndedQueueItemId)
        }
    }

    @Test
    fun repeatOneBoundaryStaysOnRepeatedItemAndCanRearm() = runBlocking {
        withAdapter { fixture ->
            val repeated = fixture.item("repeat", durationMs = 1_400)
            fixture.adapter.setQueue(listOf(repeated), repeated.queueItemId, 0)
            fixture.adapter.setRepeatCurrentItem(true)

            assertTrue(fixture.adapter.play())
            val firstBoundary = fixture.awaitBoundaryAfter(0)
            assertEquals(repeated.queueItemId, firstBoundary.boundaryEndedQueueItemId)

            // Canonical replay/repeat work positions the item back before the re-arm margin. The
            // same physical item must then be allowed to produce one future natural boundary.
            fixture.adapter.seekTo(0)
            assertTrue(fixture.adapter.play())
            val secondBoundary = fixture.awaitBoundaryAfter(firstBoundary.itemBoundaryRevision)
            assertEquals(repeated.queueItemId, secondBoundary.boundaryEndedQueueItemId)
            assertEquals(
                firstBoundary.itemBoundaryRevision + 1,
                secondBoundary.itemBoundaryRevision,
            )
        }
    }

    @Test
    fun explicitReplaySeekRearmsFinalBoundary() = runBlocking {
        withAdapter { fixture ->
            val only = fixture.item("replay", durationMs = 1_400)
            fixture.adapter.setQueue(listOf(only), only.queueItemId, 0)

            assertTrue(fixture.adapter.play())
            val firstBoundary = fixture.awaitBoundaryAfter(0)
            assertEquals(only.queueItemId, firstBoundary.boundaryEndedQueueItemId)

            fixture.adapter.seekTo(0)
            assertTrue(fixture.adapter.play())
            val secondBoundary = fixture.awaitBoundaryAfter(firstBoundary.itemBoundaryRevision)
            assertEquals(only.queueItemId, secondBoundary.boundaryEndedQueueItemId)
            assertEquals(
                firstBoundary.itemBoundaryRevision + 1,
                secondBoundary.itemBoundaryRevision,
            )
        }
    }

    @Test
    fun playlistMutationBeforeBoundaryDoesNotInventEndedItem() = runBlocking {
        withAdapter { fixture ->
            val first = fixture.item("mutating-first", durationMs = 1_600)
            val second = fixture.item("mutating-second", durationMs = 4_000)
            val inserted = fixture.item("inserted", durationMs = 4_000)
            fixture.adapter.setQueue(listOf(first, second), first.queueItemId, 0)
            assertTrue(fixture.adapter.play())

            delay(QUEUE_MUTATION_LEAD_MS)
            val livePosition = fixture.adapter.samplePlayback().positionMs
            fixture.adapter.setQueue(
                listOf(first, inserted, second),
                first.queueItemId,
                livePosition,
            )

            // PLAYLIST_CHANGED must not itself look like a natural end.
            delay(QUEUE_MUTATION_SETTLE_MS)
            assertEquals(0L, fixture.adapter.state.value.itemBoundaryRevision)

            val boundary = fixture.awaitBoundaryAfter(0)
            assertEquals(first.queueItemId, boundary.boundaryEndedQueueItemId)
        }
    }

    private suspend fun withAdapter(block: suspend (Fixture) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val log = DiagnosticLog(context)
        val adapter =
            withContext(Dispatchers.Main.immediate) {
                Media3PlayerAdapter(context, scope, log).also { it.exoPlayer.volume = 0f }
            }
        val fixture = Fixture(context, adapter)
        try {
            block(fixture)
        } finally {
            withContext(Dispatchers.Main.immediate) { adapter.close() }
            scope.cancel()
            log.close()
            fixture.files.forEach(File::delete)
        }
    }

    private class Fixture(
        private val context: Context,
        val adapter: Media3PlayerAdapter,
    ) {
        val files = mutableListOf<File>()
        private var trackOrdinal = 1

        fun item(name: String, durationMs: Long): LocalPlayableItem {
            val file = File(context.cacheDir, "unison-boundary-$name-${System.nanoTime()}.wav")
            writeSilentPcmWav(file, durationMs)
            files += file
            val digit = Integer.toHexString(trackOrdinal++).last()
            return LocalPlayableItem(
                queueItemId = QueueItemId("boundary-$name"),
                track =
                    TrackDescriptor(
                        trackId = TrackId(digit.toString().repeat(64)),
                        sizeBytes = file.length(),
                        mimeType = "audio/wav",
                        durationMs = durationMs,
                        title = name,
                        originalFileName = file.name,
                    ),
                file = file,
            )
        }

        suspend fun awaitBoundaryAfter(revision: Long): PlayerState =
            withTimeout(BOUNDARY_TIMEOUT_MS) {
                adapter.state.first { it.itemBoundaryRevision > revision }
            }
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 8_000
        const val BITS_PER_SAMPLE = 16
        const val CHANNELS = 1
        const val BOUNDARY_TIMEOUT_MS = 12_000L
        const val NO_DUPLICATE_SETTLE_MS = 300L
        const val QUEUE_MUTATION_LEAD_MS = 350L
        const val QUEUE_MUTATION_SETTLE_MS = 150L

        fun writeSilentPcmWav(file: File, durationMs: Long) {
            val sampleCount = ((SAMPLE_RATE_HZ.toLong() * durationMs) / 1_000L).toInt()
            val bytesPerSample = BITS_PER_SAMPLE / 8
            val dataSize = sampleCount * CHANNELS * bytesPerSample
            val byteRate = SAMPLE_RATE_HZ * CHANNELS * bytesPerSample
            val blockAlign = CHANNELS * bytesPerSample

            FileOutputStream(file).use { out ->
                out.write("RIFF".toByteArray(Charsets.US_ASCII))
                out.writeLittleEndianInt(36 + dataSize)
                out.write("WAVE".toByteArray(Charsets.US_ASCII))
                out.write("fmt ".toByteArray(Charsets.US_ASCII))
                out.writeLittleEndianInt(16)
                out.writeLittleEndianShort(1) // PCM
                out.writeLittleEndianShort(CHANNELS)
                out.writeLittleEndianInt(SAMPLE_RATE_HZ)
                out.writeLittleEndianInt(byteRate)
                out.writeLittleEndianShort(blockAlign)
                out.writeLittleEndianShort(BITS_PER_SAMPLE)
                out.write("data".toByteArray(Charsets.US_ASCII))
                out.writeLittleEndianInt(dataSize)
                repeat(dataSize) { out.write(0) }
            }
        }

        fun FileOutputStream.writeLittleEndianInt(value: Int) {
            write(value and 0xff)
            write((value ushr 8) and 0xff)
            write((value ushr 16) and 0xff)
            write((value ushr 24) and 0xff)
        }

        fun FileOutputStream.writeLittleEndianShort(value: Int) {
            write(value and 0xff)
            write((value ushr 8) and 0xff)
        }
    }
}
