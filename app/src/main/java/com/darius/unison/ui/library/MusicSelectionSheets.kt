package com.darius.unison.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.storage.PlaylistSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicDestinationSheet(
    pending: PendingMusicImport,
    playlists: List<PlaylistSummary>,
    roomActive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (MusicDestination) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var saveToLibrary by remember(pending) { mutableStateOf(pending.defaultSaveToLibrary) }
    var addToRoom by remember(pending) { mutableStateOf(pending.defaultAddToRoom && roomActive) }
    var selectedPlaylistIds by remember(pending) { mutableStateOf(emptySet<String>()) }
    var newPlaylistName by remember(pending) { mutableStateOf("") }
    var playlistQuery by remember(pending) { mutableStateOf("") }

    val playlistRequiresLibrary =
        selectedPlaylistIds.isNotEmpty() || newPlaylistName.trim().isNotEmpty() || pending.isM3u
    val effectiveSaveToLibrary = saveToLibrary || playlistRequiresLibrary
    val destination =
        MusicDestination(
            saveToLibrary = effectiveSaveToLibrary,
            playlistIds = if (pending.isM3u) emptySet() else selectedPlaylistIds,
            newPlaylistName =
                if (pending.isM3u) null else newPlaylistName.trim().takeIf(String::isNotEmpty),
            addToRoom = addToRoom && roomActive,
        )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(
                title = if (pending.isM3u) "Import playlist" else "Add music",
                subtitle =
                    when {
                        pending.isM3u -> "Choose whether this playlist should also join the room."
                        pending.sharedFromAnotherApp -> "Choose where shared music should go."
                        else -> "Choose where this music should go."
                    },
                onClose = onDismiss,
            )

            ListItem(
                headlineContent = {
                    Text(if (pending.isM3u) "Keep playlist in library" else "Keep in my library")
                },
                supportingContent = {
                    Text(
                        if (pending.isM3u) {
                            "Imported playlist songs stay available on this phone."
                        } else if (playlistRequiresLibrary) {
                            "Playlist songs are kept in your library automatically."
                        } else {
                            "Keep these songs on this phone after the room ends."
                        }
                    )
                },
                leadingContent = {
                    Checkbox(
                        checked = effectiveSaveToLibrary,
                        onCheckedChange =
                            if (playlistRequiresLibrary) null
                            else ({ checked -> saveToLibrary = checked }),
                    )
                },
                modifier =
                    if (playlistRequiresLibrary) Modifier
                    else Modifier.clickable { saveToLibrary = !saveToLibrary },
            )

            if (roomActive) {
                ListItem(
                    headlineContent = { Text("Add to room queue") },
                    supportingContent = {
                        Text("Make these songs available to everyone in the room.")
                    },
                    leadingContent = {
                        Checkbox(checked = addToRoom, onCheckedChange = { addToRoom = it })
                    },
                    modifier = Modifier.clickable { addToRoom = !addToRoom },
                )
            }

            if (!pending.isM3u) {
                HorizontalDivider()
                Text(
                    "Add to playlists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                PlaylistPickerContent(
                    playlists = playlists,
                    query = playlistQuery,
                    onQueryChange = { playlistQuery = it },
                    selectedPlaylistIds = selectedPlaylistIds,
                    onSelectionChange = { selectedPlaylistIds = it },
                    newPlaylistName = newPlaylistName,
                    onNewPlaylistNameChange = { newPlaylistName = it },
                    modifier = Modifier.heightIn(max = 440.dp),
                )
            }

            Button(
                onClick = { onConfirm(destination) },
                enabled = destination.hasDestination,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Text(if (pending.isM3u) "Import" else "Add")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistPickerSheet(
    playlists: List<PlaylistSummary>,
    title: String,
    excludedPlaylistId: String? = null,
    initialSelection: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (Set<String>, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var selected by remember(initialSelection) { mutableStateOf(initialSelection) }
    var newPlaylistName by remember { mutableStateOf("") }
    val hasTarget = selected.isNotEmpty() || newPlaylistName.trim().isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetHeader(title = title, onClose = onDismiss)
            PlaylistPickerContent(
                playlists = playlists.filterNot { it.playlistId == excludedPlaylistId },
                query = query,
                onQueryChange = { query = it },
                selectedPlaylistIds = selected,
                onSelectionChange = { selected = it },
                newPlaylistName = newPlaylistName,
                onNewPlaylistNameChange = { newPlaylistName = it },
                modifier = Modifier.heightIn(max = 520.dp),
            )
            Button(
                onClick = {
                    onConfirm(selected, newPlaylistName.trim().takeIf(String::isNotEmpty))
                },
                enabled = hasTarget,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Text("Add")
            }
        }
    }
}

@Composable
private fun PlaylistPickerContent(
    playlists: List<PlaylistSummary>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedPlaylistIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    newPlaylistName: String,
    onNewPlaylistNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedQuery = query.trim()
    val filtered =
        remember(playlists, normalizedQuery) {
            if (normalizedQuery.isEmpty()) playlists
            else playlists.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
        }
    val recentIds = remember(playlists) { playlists.take(3).mapTo(linkedSetOf()) { it.playlistId } }
    val recent = filtered.filter { it.playlistId in recentIds }
    val remaining = filtered.filterNot { it.playlistId in recentIds }

    var creatingPlaylist by remember { mutableStateOf(newPlaylistName.isNotBlank()) }
    val showSearch = playlists.size >= 5 || query.isNotBlank()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showSearch) {
            UnisonSearchField(
                value = query,
                onValueChange = { onQueryChange(it.take(120)) },
                placeholder = "Search playlists",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (playlists.isEmpty()) {
            Text(
                "No playlists yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (creatingPlaylist) {
            OutlinedTextField(
                value = newPlaylistName,
                onValueChange = { onNewPlaylistNameChange(it.take(128)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Playlist name") },
                placeholder = { Text("New playlist") },
                leadingIcon = { Icon(Icons.Default.Add, null) },
                singleLine = true,
            )
        } else {
            FilledTonalButton(onClick = { creatingPlaylist = true }) {
                Icon(Icons.Default.Add, null)
                Text("Create playlist", modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (playlists.isEmpty()) {
            // There is no list to render until the first playlist is created.
        } else if (filtered.isEmpty()) {
            Text(
                "No playlists match this search.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (recent.isNotEmpty() && normalizedQuery.isEmpty()) {
                    item("recent-label") {
                        Text(
                            "Recent",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                items(recent, key = { "recent:${it.playlistId}" }) { playlist ->
                    PlaylistChoiceRow(playlist, playlist.playlistId in selectedPlaylistIds) {
                        onSelectionChange(selectedPlaylistIds.toggle(playlist.playlistId))
                    }
                }
                if (remaining.isNotEmpty()) {
                    if (recent.isNotEmpty() && normalizedQuery.isEmpty()) {
                        item("all-label") {
                            Text(
                                "All playlists",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(remaining, key = { "all:${it.playlistId}" }) { playlist ->
                        PlaylistChoiceRow(playlist, playlist.playlistId in selectedPlaylistIds) {
                            onSelectionChange(selectedPlaylistIds.toggle(playlist.playlistId))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistChoiceRow(
    playlist: PlaylistSummary,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text("${playlist.trackCount} ${if (playlist.trackCount == 1) "song" else "songs"}")
        },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle() }) },
        modifier = Modifier.clickable(onClick = onToggle),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackPickerSheet(
    title: String,
    tracks: LazyPagingItems<TrackDescriptor>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelectAll: (String, (Set<TrackId>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (List<TrackId>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(setOf<TrackId>()) }
    var stableTracks by remember { mutableStateOf(tracks.itemSnapshotList.items) }
    val snapshot = tracks.itemSnapshotList.items
    val refreshState = tracks.loadState.refresh

    LaunchedEffect(snapshot, refreshState) {
        if (refreshState !is LoadState.Loading) stableTracks = snapshot
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            SheetHeader(title = title, onClose = onDismiss)
            UnisonSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "Search music",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${selected.size} selected",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onSelectAll(query) { all ->
                            selected =
                                if (selected.size == all.size && all.isNotEmpty()) emptySet()
                                else all
                        }
                    }
                ) {
                    Text("Select all")
                }
            }
            if (refreshState is LoadState.Loading && stableTracks.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Box(Modifier.weight(1f)) {
                when {
                    refreshState is LoadState.Loading && stableTracks.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Loading music…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    refreshState is LoadState.Error && stableTracks.isEmpty() ->
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Could not load your music")
                            FilledTonalButton(onClick = tracks::retry) { Text("Try again") }
                        }
                    stableTracks.isEmpty() ->
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Default.LibraryMusic, null)
                            Text(
                                if (query.isBlank()) "No music in your library"
                                else "No songs match this search",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    else ->
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(stableTracks, key = { it.trackId.value }) { track ->
                                val checked = track.trackId in selected
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            track.displayTitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    supportingContent = {
                                        track.artist?.takeIf(String::isNotBlank)?.let {
                                            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    },
                                    leadingContent = {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = {
                                                selected = selected.toggle(track.trackId)
                                            },
                                        )
                                    },
                                    modifier =
                                        Modifier.clickable {
                                            selected = selected.toggle(track.trackId)
                                        },
                                )
                            }
                            when (tracks.loadState.append) {
                                is LoadState.Loading ->
                                    item("append-loading") {
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
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
            Button(
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Text("Add ${selected.size.takeIf { it > 0 } ?: ""}".trim())
            }
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
