package com.darius.unison.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.darius.unison.library.LibrarySort
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.storage.PlaylistSummary

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AllMusicScreen(
    tracks: LazyPagingItems<TrackDescriptor>,
    query: String,
    sort: LibrarySort,
    totalCount: Int,
    temporaryTrackIds: Set<TrackId>,
    playlists: List<PlaylistSummary>,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onChooseFiles: () -> Unit,
    onAddTracksToPlaylists: (Set<String>, List<TrackId>, String?) -> Unit,
    onKeepTracks: (Set<TrackId>) -> Unit,
    onRemoveTemporaryTracks: (Set<TrackId>) -> Unit,
    onSelectAll: (String, (Set<TrackId>) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<TrackId>()) }
    var sortOpen by remember { mutableStateOf(false) }
    var playlistPickerOpen by remember { mutableStateOf(false) }
    var rowMenuTrack by remember { mutableStateOf<TrackDescriptor?>(null) }
    var stableTracks by remember { mutableStateOf(tracks.itemSnapshotList.items) }
    val currentSnapshot = tracks.itemSnapshotList.items
    val refreshState = tracks.loadState.refresh

    // Keep the last rendered result set while Paging swaps generations. The list never blanks or
    // flashes during search/database invalidation; it is replaced only when the new generation is
    // ready.
    LaunchedEffect(currentSnapshot, refreshState) {
        if (refreshState !is LoadState.Loading) stableTracks = currentSnapshot
    }

    BackHandler {
        if (selected.isNotEmpty()) {
            selected = emptySet()
        } else {
            onQueryChange("")
            onBack()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "All Music",
            subtitle = "$totalCount ${if (totalCount == 1) "song" else "songs"}",
            onBack = {
                onQueryChange("")
                onBack()
            },
        ) {
            IconButton(onClick = onChooseFiles) {
                Icon(Icons.Default.AudioFile, "Add audio files")
            }
        }

            if (selected.isNotEmpty()) {
                SelectionToolbar(
                    selectedCount = selected.size,
                    allSelected = selected.size == totalCount && totalCount > 0,
                    hasTemporary = selected.any { it in temporaryTrackIds },
                    onClose = { selected = emptySet() },
                    onSelectAll = {
                        if (selected.size == totalCount) selected = emptySet()
                        else onSelectAll(query) { selected = it }
                    },
                    onAddToPlaylist = { playlistPickerOpen = true },
                    onKeep = {
                        onKeepTracks(selected)
                        selected = emptySet()
                    },
                    onRemoveTemporary = {
                        val removable =
                            selected.filterTo(mutableSetOf()) { it in temporaryTrackIds }
                        onRemoveTemporaryTracks(removable)
                        selected = emptySet()
                    },
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    UnisonSearchField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = "Search music",
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { sortOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, "Sort music")
                        }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            LibrarySort.entries.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.label()) },
                                    trailingIcon = {
                                        if (item == sort) Icon(Icons.Default.Check, null)
                                    },
                                    onClick = {
                                        sortOpen = false
                                        onSortChange(item)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (refreshState is LoadState.Loading && stableTracks.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            when {
                stableTracks.isEmpty() && refreshState is LoadState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                stableTracks.isEmpty() && refreshState is LoadState.Error ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = tracks::retry) { Text("Try loading again") }
                    }
                stableTracks.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = if (query.isBlank()) "No music yet" else "No songs found",
                            text =
                                if (query.isBlank()) "Add audio files to build your Unison library."
                                else "Try a different title, artist, or album.",
                            icon = if (query.isBlank()) Icons.Default.MusicNote else Icons.Default.SearchOff,
                            actionLabel = "Add audio files".takeIf { query.isBlank() },
                            onAction = onChooseFiles,
                        )
                    }
                else ->
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(stableTracks, key = { it.trackId.value }) { track ->
                            val isSelected = track.trackId in selected
                            ListItem(
                                modifier =
                                    Modifier.combinedClickable(
                                        onClick = {
                                            if (selected.isNotEmpty()) {
                                                selected =
                                                    if (isSelected) selected - track.trackId
                                                    else selected + track.trackId
                                            } else {
                                                rowMenuTrack = track
                                            }
                                        },
                                        onLongClick = {
                                            selected =
                                                if (isSelected) selected - track.trackId
                                                else selected + track.trackId
                                        },
                                    ),
                                headlineContent = {
                                    Text(
                                        track.displayTitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                                track.artist?.takeIf(String::isNotBlank),
                                                formatDuration(track.durationMs),
                                                "Temporary"
                                                    .takeIf { track.trackId in temporaryTrackIds },
                                            )
                                            .joinToString(" • "),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingContent = {
                                    if (selected.isNotEmpty()) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selected =
                                                    if (checked) selected + track.trackId
                                                    else selected - track.trackId
                                            },
                                        )
                                    } else {
                                        TonalIcon(Icons.Default.MusicNote, null)
                                    }
                                },
                                trailingContent = {
                                    if (selected.isEmpty()) {
                                        IconButton(onClick = { rowMenuTrack = track }) {
                                            Icon(Icons.Default.MoreVert, "Song actions")
                                        }
                                    }
                                },
                            )
                        }
                        when (tracks.loadState.append) {
                            is LoadState.Loading ->
                                item("append-loading") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            Modifier.size(22.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                }
                            is LoadState.Error ->
                                item("append-error") {
                                    TextButton(
                                        onClick = tracks::retry,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Try loading more")
                                    }
                                }
                            else -> Unit
                        }
                    }
            }
        }

    rowMenuTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { rowMenuTrack = null },
            title = { Text(track.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Add to playlist") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                        modifier =
                            Modifier.combinedClickable(
                                onClick = {
                                    selected = setOf(track.trackId)
                                    rowMenuTrack = null
                                    playlistPickerOpen = true
                                },
                                onLongClick = {},
                            ),
                    )
                    ListItem(
                        headlineContent = { Text("Keep on this phone") },
                        leadingContent = { Icon(Icons.Default.DownloadDone, null) },
                        modifier =
                            Modifier.combinedClickable(
                                onClick = {
                                    rowMenuTrack = null
                                    onKeepTracks(setOf(track.trackId))
                                },
                                onLongClick = {},
                            ),
                    )
                    if (track.trackId in temporaryTrackIds) {
                        ListItem(
                            headlineContent = { Text("Remove temporary copy") },
                            leadingContent = { Icon(Icons.Default.DeleteOutline, null) },
                            modifier =
                                Modifier.combinedClickable(
                                    onClick = {
                                        rowMenuTrack = null
                                        onRemoveTemporaryTracks(setOf(track.trackId))
                                    },
                                    onLongClick = {},
                                ),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { rowMenuTrack = null }) { Text("Done") } },
        )
    }

    if (playlistPickerOpen) {
        PlaylistPickerSheet(
            playlists = playlists,
            title = "Add to playlist",
            onDismiss = { playlistPickerOpen = false },
            onConfirm = { playlistIds, newPlaylistName ->
                playlistPickerOpen = false
                onAddTracksToPlaylists(playlistIds, selected.toList(), newPlaylistName)
                selected = emptySet()
            },
        )
    }

}

@Composable
private fun SelectionToolbar(
    selectedCount: Int,
    allSelected: Boolean,
    hasTemporary: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onKeep: () -> Unit,
    onRemoveTemporary: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Exit selection") }
                Text(
                    "$selectedCount selected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSelectAll) {
                    Text(if (allSelected) "Clear all" else "Select all")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = onAddToPlaylist, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Playlist")
                }
                FilledTonalButton(onClick = onKeep, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DownloadDone, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Keep")
                }
                FilledTonalButton(
                    onClick = onRemoveTemporary,
                    enabled = hasTemporary,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove")
                }
            }
        }
    }
}

private fun LibrarySort.label(): String =
    when (this) {
        LibrarySort.RECENT -> "Recently added"
        LibrarySort.TITLE -> "Title"
        LibrarySort.ARTIST -> "Artist"
        LibrarySort.ALBUM -> "Album"
    }
