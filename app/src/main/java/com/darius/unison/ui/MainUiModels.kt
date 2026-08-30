package com.darius.unison.ui

import android.net.Uri
import com.darius.unison.library.LibrarySort
import com.darius.unison.library.M3uAmbiguousEntry
import com.darius.unison.library.M3uResolvedEntry
import com.darius.unison.library.M3uUnresolvedEntry
import com.darius.unison.library.PlaylistDetail
import com.darius.unison.library.StorageSummary
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.RoomUiState
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.storage.PlaylistSummary

data class MusicDestination(
    val saveToLibrary: Boolean = true,
    val playlistIds: Set<String> = emptySet(),
    val newPlaylistName: String? = null,
    val addToRoom: Boolean = false,
) {
    val keepsInLibrary: Boolean
        get() = saveToLibrary || playlistIds.isNotEmpty() || !newPlaylistName.isNullOrBlank()

    val hasDestination: Boolean
        get() = keepsInLibrary || addToRoom
}

data class ImportProgress(
    val completed: Int,
    val total: Int,
    val headline: String = "Adding music",
    val detail: String? = null,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else completed.toFloat() / total
}

data class PendingMusicImport(
    val uris: List<Uri>,
    val isM3u: Boolean,
    val defaultSaveToLibrary: Boolean,
    val defaultAddToRoom: Boolean,
    val sharedFromAnotherApp: Boolean,
)

data class PendingM3uResolution(
    val sourceUri: Uri,
    val playlistId: String,
    val toRoom: Boolean,
    val resolvedEntries: List<M3uResolvedEntry>,
    val unresolved: List<M3uUnresolvedEntry>,
    val ambiguous: List<M3uAmbiguousEntry>,
    val manualSelections: Map<Int, TrackId> = emptyMap(),
) {
    val availableTracks: List<TrackDescriptor>
        get() = resolvedEntries.sortedBy(M3uResolvedEntry::entryIndex).map { it.track }
}

internal data class LibraryControls(
    val query: String,
    val sort: LibrarySort,
)

internal data class LibraryUiData(
    val totalCount: Int,
    val visibleCount: Int,
    val temporaryTrackIds: Set<TrackId>,
    val storageSummary: StorageSummary,
    val controls: LibraryControls,
)

internal data class OperationState(
    val busy: Boolean,
    val importProgress: ImportProgress?,
)

internal data class TransientUiState(
    val operation: OperationState,
    val message: String?,
    val pendingM3uResolution: PendingM3uResolution?,
    val selectedPlaylist: PlaylistDetail?,
    val pendingMusicImport: PendingMusicImport?,
)

data class MainUiState(
    val room: RoomUiState = RoomUiState(),
    val libraryTotalCount: Int = 0,
    val libraryVisibleCount: Int = 0,
    val libraryQuery: String = "",
    val librarySort: LibrarySort = LibrarySort.RECENT,
    val temporaryTrackIds: Set<TrackId> = emptySet(),
    val storageSummary: StorageSummary = StorageSummary(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val settingsLoaded: Boolean = false,
    val onboardingComplete: Boolean = false,
    val retentionPolicy: RetentionPolicy = RetentionPolicy.TEMPORARY_24_HOURS,
    val busy: Boolean = false,
    val importProgress: ImportProgress? = null,
    val message: String? = null,
    val pendingM3uResolution: PendingM3uResolution? = null,
    val selectedPlaylist: PlaylistDetail? = null,
    val pendingMusicImport: PendingMusicImport? = null,
)
