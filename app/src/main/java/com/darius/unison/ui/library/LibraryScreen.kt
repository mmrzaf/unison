package com.darius.unison.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.darius.unison.library.LibrarySort
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId

@Composable
internal fun LibraryScreen(
    state: MainUiState,
    allMusicOpen: Boolean,
    onOpenAllMusic: () -> Unit,
    tracks: LazyPagingItems<TrackDescriptor>,
    pickerTracks: LazyPagingItems<TrackDescriptor>,
    pickerQuery: String,
    onQueryChange: (String) -> Unit,
    onPickerQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onChooseFiles: () -> Unit,
    onImportM3u: () -> Unit,
    onEditName: () -> Unit,
    onAddTrackToRoom: (TrackId) -> Unit,
    onAddTracksToRoom: (List<TrackId>) -> Unit,
    onPlayNext: (TrackId) -> Unit,
    onKeepTrack: (TrackId) -> Unit,
    onRemoveTemporaryTrack: (TrackId) -> Unit,
    onClearTemporaryMusic: () -> Unit,
    onCreatePlaylist: (String, List<TrackId>) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onAddPlaylistToRoom: (String) -> Unit,
    onAddTrackToPlaylist: (String, TrackId) -> Unit,
    onAddTracksToPlaylist: (String, List<TrackId>) -> Unit,
    onSelectAll: (String, (Set<TrackId>) -> Unit) -> Unit,
    onAddAllToRoom: (String) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var storageDialog by remember { mutableStateOf(false) }
    var confirmClearTemporary by remember { mutableStateOf(false) }
    var createPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var playlistSelection by remember { mutableStateOf(setOf<TrackId>()) }
    var selectingMusic by rememberSaveable { mutableStateOf(false) }
    var musicSelection by remember { mutableStateOf(setOf<TrackId>()) }
    var playlistTarget by remember { mutableStateOf<Set<TrackId>?>(null) }
    val musicListState = rememberLazyListState()
    val startPlaylistCreation = {
        createPlaylist = true
        playlistSelection = emptySet()
        playlistName = ""
        onPickerQueryChange("")
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = if (allMusicOpen) onChooseFiles else startPlaylistCreation) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(if (allMusicOpen) "Add music" else "New playlist")
            }
            Spacer(Modifier.weight(1f))
            if (!allMusicOpen) {
                IconButton(onClick = onChooseFiles) { Icon(Icons.Default.AudioFile, "Add music") }
            }
            IconButton(onClick = { storageDialog = true }) {
                Icon(Icons.Default.Storage, "Storage")
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (allMusicOpen) {
                        DropdownMenuItem(
                            text = { Text("New playlist") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                            onClick = {
                                menu = false
                                startPlaylistCreation()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Import M3U") },
                        leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                        onClick = {
                            menu = false
                            onImportM3u()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Change your name") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = {
                            menu = false
                            onEditName()
                        },
                    )
                }
            }
        }

        if (!allMusicOpen) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionTitle("Your playlists")
                Text(
                    "Choose a collection to manage it or add it to a room.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all-music") {
                    Card(
                        onClick = {
                            onQueryChange("")
                            selectingMusic = false
                            musicSelection = emptySet()
                            onOpenAllMusic()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.LibraryMusic,
                                null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("All music", style = MaterialTheme.typography.titleMedium)
                                val songCount =
                                    "${state.libraryTotalCount} " +
                                        if (state.libraryTotalCount == 1) "song" else "songs"
                                Text(
                                    "$songCount · Default playlist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.room.snapshot != null) {
                                IconButton(
                                    onClick = {
                                        onSelectAll("") { all -> onAddTracksToRoom(all.toList()) }
                                    }
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.PlaylistAdd,
                                        "Add all music to room",
                                    )
                                }
                            }
                        }
                    }
                }
                items(state.playlists, key = { it.playlistId }) { playlist ->
                    Card(
                        onClick = { onOpenPlaylist(playlist.playlistId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${playlist.trackCount} ${if (playlist.trackCount == 1) "song" else "songs"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.room.snapshot != null) {
                                IconButton(onClick = { onAddPlaylistToRoom(playlist.playlistId) }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.PlaylistAdd,
                                        "Add playlist to room",
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.playlists.isEmpty()) {
                    item(key = "empty-playlists") {
                        Card(onClick = startPlaylistCreation, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Create your first playlist",
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Group songs before sharing them with a room.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("All music")
                Spacer(Modifier.weight(1f))
                if (state.room.snapshot != null) {
                    FilledTonalButton(
                        onClick = { onAddAllToRoom("") },
                        enabled = state.libraryTotalCount > 0,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add all")
                    }
                }
                TextButton(
                    onClick = {
                        selectingMusic = !selectingMusic
                        if (!selectingMusic) musicSelection = emptySet()
                    },
                    enabled = tracks.itemCount > 0,
                ) {
                    Text(if (selectingMusic) "Done" else "Select")
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.libraryQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search title, artist, or album") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (state.libraryQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        }
                    },
                    singleLine = true,
                )
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        LibrarySort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.displayName()) },
                                trailingIcon = {
                                    if (sort == state.librarySort) Icon(Icons.Default.Check, null)
                                },
                                onClick = {
                                    sortMenu = false
                                    onSortChange(sort)
                                },
                            )
                        }
                    }
                }
            }
            if (selectingMusic) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "${musicSelection.size} selected",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            if (musicSelection.size == state.libraryVisibleCount) {
                                musicSelection = emptySet()
                            } else {
                                onSelectAll(state.libraryQuery) { musicSelection = it }
                            }
                        }
                    ) {
                        Text(
                            if (musicSelection.size == state.libraryVisibleCount) "Clear"
                            else "Select all"
                        )
                    }
                    IconButton(
                        onClick = { playlistTarget = musicSelection },
                        enabled = musicSelection.isNotEmpty(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, "Add selection to playlist")
                    }
                    if (state.room.snapshot != null) {
                        FilledTonalButton(
                            onClick = {
                                onAddTracksToRoom(musicSelection.toList())
                                musicSelection = emptySet()
                                selectingMusic = false
                            },
                            enabled = musicSelection.isNotEmpty(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Room")
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    tracks.loadState.refresh is LoadState.Loading && tracks.itemCount == 0 -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    tracks.loadState.refresh is LoadState.Error && tracks.itemCount == 0 -> {
                        LoadError(onRetry = tracks::retry)
                    }

                    tracks.itemCount == 0 -> {
                        EmptyState(
                            title =
                                if (state.libraryQuery.isBlank()) "No music yet" else "No matches",
                            text =
                                if (state.libraryQuery.isBlank()) "Add local audio files to start."
                                else "Try another search.",
                            icon = Icons.Default.AudioFile,
                            actionLabel = if (state.libraryQuery.isBlank()) "Add music" else null,
                            onAction = onChooseFiles,
                        )
                    }

                    else -> {
                        LazyColumn(
                            state = musicListState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                count = tracks.itemCount,
                                key = tracks.itemKey { it.trackId.value },
                            ) { index ->
                                tracks[index]?.let { track ->
                                    TrackRow(
                                        track = track,
                                        temporary = track.trackId in state.temporaryTrackIds,
                                        roomActive = state.room.snapshot != null,
                                        selectionMode = selectingMusic,
                                        selected = track.trackId in musicSelection,
                                        onSelectionChange = { checked ->
                                            musicSelection =
                                                if (checked) {
                                                    musicSelection + track.trackId
                                                } else {
                                                    musicSelection - track.trackId
                                                }
                                        },
                                        onAddToRoom = { onAddTrackToRoom(track.trackId) },
                                        onPlayNext = { onPlayNext(track.trackId) },
                                        onKeep = { onKeepTrack(track.trackId) },
                                        onRemove = { onRemoveTemporaryTrack(track.trackId) },
                                        onAddToPlaylist = { playlistTarget = setOf(track.trackId) },
                                    )
                                    HorizontalDivider(Modifier.padding(start = 56.dp))
                                }
                            }
                            when (tracks.loadState.append) {
                                is LoadState.Loading ->
                                    item {
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
                                    item {
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
            }
        }
    }

    playlistTarget?.let { trackIds ->
        AlertDialog(
            onDismissRequest = { playlistTarget = null },
            title = {
                Text(
                    if (trackIds.size == 1) "Add to playlist"
                    else "Add ${trackIds.size} songs to playlist"
                )
            },
            text = {
                if (state.playlists.isEmpty()) {
                    Text("Create a playlist first.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(state.playlists, key = { it.playlistId }) { playlist ->
                            TextButton(
                                onClick = {
                                    if (trackIds.size == 1) {
                                        onAddTrackToPlaylist(playlist.playlistId, trackIds.first())
                                    } else {
                                        onAddTracksToPlaylist(
                                            playlist.playlistId,
                                            trackIds.toList(),
                                        )
                                    }
                                    playlistTarget = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(playlist.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (state.playlists.isEmpty()) {
                    Button(
                        onClick = {
                            playlistSelection = trackIds
                            playlistName = ""
                            onPickerQueryChange("")
                            playlistTarget = null
                            createPlaylist = true
                        }
                    ) {
                        Text("Create playlist")
                    }
                } else {
                    TextButton(onClick = { playlistTarget = null }) { Text("Cancel") }
                }
            },
        )
    }

    if (storageDialog) {
        AlertDialog(
            onDismissRequest = { storageDialog = false },
            icon = { Icon(Icons.Default.Storage, null) },
            title = { Text("Storage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageLine("Used by Unison", state.storageSummary.totalBytes)
                    StorageLine("Kept music", state.storageSummary.keptBytes)
                    StorageLine("Temporary music", state.storageSummary.temporaryBytes)
                    Text(
                        "Temporary music is removed automatically when it is no longer needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { storageDialog = false }) { Text("Done") } },
            dismissButton = {
                TextButton(
                    onClick = {
                        storageDialog = false
                        confirmClearTemporary = true
                    },
                    enabled = state.storageSummary.temporaryBytes > 0,
                ) {
                    Text("Clear temporary")
                }
            },
        )
    }

    if (confirmClearTemporary) {
        AlertDialog(
            onDismissRequest = { confirmClearTemporary = false },
            title = { Text("Clear temporary music?") },
            text = { Text("Songs used by the active room will stay available.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClearTemporary = false
                        onClearTemporaryMusic()
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearTemporary = false }) { Text("Cancel") }
            },
        )
    }

    if (createPlaylist) {
        AlertDialog(
            onDismissRequest = { createPlaylist = false },
            title = { Text("New playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it.take(60) },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = pickerQuery,
                        onValueChange = onPickerQueryChange,
                        placeholder = { Text("Search music") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${playlistSelection.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                onSelectAll(pickerQuery) { all ->
                                    playlistSelection =
                                        if (playlistSelection.size == all.size) emptySet() else all
                                }
                            }
                        ) {
                            Text("Select all")
                        }
                    }
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(
                            count = pickerTracks.itemCount,
                            key = pickerTracks.itemKey { it.trackId.value },
                        ) { index ->
                            pickerTracks[index]?.let { track ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = track.trackId in playlistSelection,
                                        onCheckedChange = { checked ->
                                            playlistSelection =
                                                if (checked) {
                                                    playlistSelection + track.trackId
                                                } else {
                                                    playlistSelection - track.trackId
                                                }
                                        },
                                    )
                                    Text(
                                        track.displayTitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePlaylist(playlistName, playlistSelection.toList())
                        createPlaylist = false
                    },
                    enabled = playlistName.isNotBlank(),
                ) {
                    Text("Create")
                }
            },
            dismissButton = { TextButton(onClick = { createPlaylist = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun TrackRow(
    track: TrackDescriptor,
    temporary: Boolean,
    roomActive: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    onAddToRoom: () -> Unit,
    onPlayNext: () -> Unit,
    onKeep: () -> Unit,
    onRemove: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    ListItem(
        modifier =
            if (selectionMode) {
                Modifier.clickable { onSelectionChange(!selected) }
            } else {
                Modifier
            },
        headlineContent = {
            Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                listOfNotNull(
                        track.artist?.takeIf(String::isNotBlank),
                        formatDuration(track.durationMs),
                    )
                    .joinToString(" • "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onSelectionChange,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (roomActive) {
                        IconButton(onClick = onAddToRoom) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to room")
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, "More")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (roomActive) {
                                DropdownMenuItem(
                                    text = { Text("Play next") },
                                    onClick = {
                                        menu = false
                                        onPlayNext()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Add to playlist") },
                                onClick = {
                                    menu = false
                                    onAddToPlaylist()
                                },
                            )
                            if (temporary) {
                                DropdownMenuItem(
                                    text = { Text("Keep on this phone") },
                                    onClick = {
                                        menu = false
                                        onKeep()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove temporary copy") },
                                    onClick = {
                                        menu = false
                                        confirmRemove = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this song?") },
            text = { Text("The temporary copy will be deleted from this phone.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemove = false
                        onRemove()
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            },
        )
    }
}
