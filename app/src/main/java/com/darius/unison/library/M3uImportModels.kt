package com.darius.unison.library

import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId

data class M3uResolvedEntry(
    val entryIndex: Int,
    val entry: M3uEntry,
    val track: TrackDescriptor,
)

data class M3uUnresolvedEntry(
    val entryIndex: Int,
    val entry: M3uEntry,
    val reason: String,
)

data class M3uAmbiguousEntry(
    val entryIndex: Int,
    val entry: M3uEntry,
    val candidates: List<TrackDescriptor>,
)

data class M3uImportResult(
    val playlistId: String,
    val resolvedEntries: List<M3uResolvedEntry>,
    val unresolved: List<M3uUnresolvedEntry>,
    val ambiguous: List<M3uAmbiguousEntry>,
) {
    val tracks: List<TrackDescriptor>
        get() = resolvedEntries.sortedBy(M3uResolvedEntry::entryIndex).map { it.track }
}

object M3uResolutionPolicy {
    fun choose(
        resolvedEntries: List<M3uResolvedEntry>,
        ambiguity: M3uAmbiguousEntry,
        selectedTrackId: TrackId,
    ): List<M3uResolvedEntry>? {
        val selected = ambiguity.candidates.firstOrNull { it.trackId == selectedTrackId } ?: return null
        return (resolvedEntries + M3uResolvedEntry(ambiguity.entryIndex, ambiguity.entry, selected))
            .distinctBy(M3uResolvedEntry::entryIndex)
            .sortedBy(M3uResolvedEntry::entryIndex)
    }

    fun orderedTrackIds(resolvedEntries: List<M3uResolvedEntry>): List<TrackId> =
        resolvedEntries.sortedBy(M3uResolvedEntry::entryIndex).map { it.track.trackId }
}
