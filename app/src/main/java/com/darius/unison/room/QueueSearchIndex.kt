package com.darius.unison.room

import com.darius.unison.model.QueueItem
import java.text.Normalizer
import java.util.Locale

/**
 * Immutable search index for a room queue.
 *
 * Metadata normalization happens once when the queue changes. Querying only scans compact,
 * pre-normalized strings and preserves each item's canonical queue index.
 */
class QueueSearchIndex(queue: List<QueueItem>) {
    private data class Entry(
        val originalIndex: Int,
        val item: QueueItem,
        val searchableText: String,
    )

    private val entries: List<Entry> = queue.mapIndexed { index, item ->
        Entry(
            originalIndex = index,
            item = item,
            searchableText =
                normalizeQueueSearchText(
                    listOfNotNull(
                            item.track.displayTitle,
                            item.track.artist,
                            item.track.album,
                            item.track.originalFileName,
                        )
                        .joinToString(" ")
                ),
        )
    }

    private val allMatches: List<QueueSearchMatch> = entries.map {
        QueueSearchMatch(it.originalIndex, it.item)
    }

    /** Returns canonical queue positions in queue order. All normalized terms must match. */
    fun search(query: String): List<QueueSearchMatch> {
        val normalizedQuery = normalizeQueueSearchText(query)
        if (normalizedQuery.isEmpty()) return allMatches

        val terms = normalizedQuery.split(' ').filter(String::isNotEmpty)
        return entries
            .asSequence()
            .filter { entry ->
                terms.all { term -> entry.searchableText.contains(term) }
            }
            .map { QueueSearchMatch(it.originalIndex, it.item) }
            .toList()
    }
}

data class QueueSearchMatch(
    val originalIndex: Int,
    val item: QueueItem,
)

internal fun normalizeQueueSearchText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .trim()
        .replace(SEARCH_WHITESPACE, " ")

private val COMBINING_MARKS = Regex("\\p{M}+")
private val SEARCH_WHITESPACE = Regex("\\s+")
