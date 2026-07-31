package com.darius.unison.room

import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSearchIndexTest {
    private val peer = PeerId("peer")

    @Test
    fun blankQueryReturnsEveryItemInCanonicalOrder() {
        val queue =
            listOf(
                item("Alpha", artist = "One"),
                item("Beta", artist = "Two"),
            )

        val results = QueueSearchIndex(queue).search("   ")

        assertEquals(listOf(0, 1), results.map { it.originalIndex })
        assertEquals(queue.map { it.queueItemId }, results.map { it.item.queueItemId })
    }

    @Test
    fun matchesTitleArtistAlbumAndFilenameCaseInsensitively() {
        val queue =
            listOf(
                item("Midnight City", artist = "M83", album = "Hurry Up", fileName = "city.flac"),
                item("Intro", artist = "The xx", album = "xx", fileName = "opening.mp3"),
            )
        val index = QueueSearchIndex(queue)

        assertEquals(0, index.search("MIDNIGHT").single().originalIndex)
        assertEquals(0, index.search("m83").single().originalIndex)
        assertEquals(0, index.search("hurry").single().originalIndex)
        assertEquals(1, index.search("opening.mp3").single().originalIndex)
    }

    @Test
    fun multipleTermsCanMatchDifferentMetadataFieldsInAnyOrder() {
        val queue =
            listOf(
                item("The Loneliest", artist = "Måneskin", album = "Rush!"),
                item("Rush", artist = "Troye Sivan", album = "Something to Give Each Other"),
            )

        val results = QueueSearchIndex(queue).search("rush maneskin")

        assertEquals(listOf(0), results.map { it.originalIndex })
    }

    @Test
    fun matchingIgnoresDiacriticsAndRepeatedWhitespace() {
        val queue = listOf(item("Déjà Vu", artist = "Beyoncé"))
        val index = QueueSearchIndex(queue)

        assertEquals(0, index.search("  deja   beyonce ").single().originalIndex)
    }

    @Test
    fun noMatchReturnsEmptyListWithoutChangingQueue() {
        val queue = listOf(item("Song A"), item("Song B"))
        val before = queue.map { it.queueItemId }

        val results = QueueSearchIndex(queue).search("not present")

        assertTrue(results.isEmpty())
        assertEquals(before, queue.map { it.queueItemId })
    }

    @Test
    fun largeQueuePreservesOriginalIndices() {
        val queue =
            (0 until 1_000).map { index ->
                item(
                    title = "Track $index",
                    artist = if (index % 100 == 0) "Featured Artist" else "Artist $index",
                )
            }

        val results = QueueSearchIndex(queue).search("featured artist")

        assertEquals((0 until 1_000 step 100).toList(), results.map { it.originalIndex })
    }

    private fun item(
        title: String,
        artist: String? = null,
        album: String? = null,
        fileName: String? = null,
    ): QueueItem =
        QueueItem.create(
            track =
                TrackDescriptor(
                    trackId = TrackId(title.padEnd(64, '0').take(64).lowercase().replace(' ', 'a')),
                    sizeBytes = 1,
                    title = title,
                    artist = artist,
                    album = album,
                    originalFileName = fileName,
                ),
            addedBy = peer,
        )
}
