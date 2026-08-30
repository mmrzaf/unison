package com.darius.unison.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.storage.PlaylistSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomAddMusicSheet(
    pickerTracksFlow: Flow<PagingData<TrackDescriptor>>,
    pickerQueryState: StateFlow<String>,
    playlists: List<PlaylistSummary>,
    libraryTotalCount: Int,
    onPickerQueryChange: (String) -> Unit,
    onChooseFiles: () -> Unit,
    onImportM3u: () -> Unit,
    onSelectAllTracks: (String, (Set<TrackId>) -> Unit) -> Unit,
    onAddSelection: (Boolean, List<String>, List<TrackId>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pickerQuery by pickerQueryState.collectAsStateWithLifecycle()
    val pickerTracks = pickerTracksFlow.collectAsLazyPagingItems()
    var section by remember { mutableStateOf(QueueMusicPickerSection.PLAYLISTS) }
    var playlistQuery by remember { mutableStateOf("") }
    var allMusicSelected by remember { mutableStateOf(false) }
    var selectedPlaylistIds by remember { mutableStateOf(setOf<String>()) }
    var selectedTracks by remember { mutableStateOf(setOf<TrackId>()) }
    var actionsOpen by remember { mutableStateOf(false) }
    var stableTracks by remember { mutableStateOf(pickerTracks.itemSnapshotList.items) }

    val currentSnapshot = pickerTracks.itemSnapshotList.items
    val refreshState = pickerTracks.loadState.refresh
    val playlistOptions =
        remember(playlists, playlistQuery, libraryTotalCount) {
            val query = playlistQuery.trim()
            buildList<QueuePlaylistOption> {
                if (query.isEmpty() || "All Music".contains(query, ignoreCase = true)) {
                    add(QueuePlaylistOption.AllMusic(libraryTotalCount))
                }
                playlists
                    .asSequence()
                    .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
                    .mapTo(this) { QueuePlaylistOption.Saved(it) }
            }
        }
    val visibleSavedIds =
        playlistOptions.mapNotNullTo(linkedSetOf()) { option ->
            (option as? QueuePlaylistOption.Saved)?.summary?.playlistId
        }
    val allVisibleSavedSelected =
        visibleSavedIds.isNotEmpty() && visibleSavedIds.all(selectedPlaylistIds::contains)
    val selectedSourceCount = selectedPlaylistIds.size + if (allMusicSelected) 1 else 0
    val hasSelection = selectedSourceCount > 0 || selectedTracks.isNotEmpty()

    // Paging may briefly expose an empty replacement generation. Keep the old rows until the
    // replacement is ready so the picker never flashes blank while searching or importing.
    LaunchedEffect(currentSnapshot, refreshState) {
        if (refreshState !is LoadState.Loading) stableTracks = currentSnapshot
    }
    LaunchedEffect(playlists) {
        val availableIds = playlists.mapTo(hashSetOf()) { it.playlistId }
        selectedPlaylistIds = selectedPlaylistIds.filterTo(linkedSetOf()) { it in availableIds }
    }

    fun selectAllMusic(selected: Boolean) {
        allMusicSelected = selected
        if (selected) {
            selectedPlaylistIds = emptySet()
            selectedTracks = emptySet()
        }
    }

    fun toggleSavedPlaylist(playlistId: String, selected: Boolean) {
        if (selected) allMusicSelected = false
        selectedPlaylistIds =
            if (selected) selectedPlaylistIds + playlistId else selectedPlaylistIds - playlistId
    }

    fun toggleTrack(trackId: TrackId, selected: Boolean) {
        if (selected) allMusicSelected = false
        selectedTracks = if (selected) selectedTracks + trackId else selectedTracks - trackId
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Add to queue",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (hasSelection) {
                        Text(
                            buildSelectionSummary(
                                allMusicSelected,
                                selectedPlaylistIds.size,
                                selectedTracks.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { actionsOpen = true }) {
                        Icon(Icons.Default.MoreVert, "More ways to add music")
                    }
                    DropdownMenu(
                        expanded = actionsOpen,
                        onDismissRequest = { actionsOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Choose audio files") },
                            leadingIcon = { Icon(Icons.Default.AudioFile, null) },
                            onClick = {
                                actionsOpen = false
                                onChooseFiles()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Import playlist") },
                            leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                            onClick = {
                                actionsOpen = false
                                onImportM3u()
                            },
                        )
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = section == QueueMusicPickerSection.PLAYLISTS,
                    onClick = { section = QueueMusicPickerSection.PLAYLISTS },
                    label = { Text("Playlists") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(18.dp))
                    },
                )
                FilterChip(
                    selected = section == QueueMusicPickerSection.SONGS,
                    onClick = { section = QueueMusicPickerSection.SONGS },
                    label = { Text("Songs") },
                    leadingIcon = { Icon(Icons.Default.LibraryMusic, null, Modifier.size(18.dp)) },
                )
            }

            when (section) {
                QueueMusicPickerSection.PLAYLISTS -> {
                    UnisonSearchField(
                        value = playlistQuery,
                        onValueChange = { playlistQuery = it.take(120) },
                        placeholder = "Search playlists",
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    SelectionHeader(
                        selectedCount = selectedSourceCount,
                        selectAllLabel =
                            if (allVisibleSavedSelected) "Clear shown" else "Select shown",
                        onSelectAll = {
                            allMusicSelected = false
                            selectedPlaylistIds =
                                if (allVisibleSavedSelected) selectedPlaylistIds - visibleSavedIds
                                else selectedPlaylistIds + visibleSavedIds
                        },
                        onClear = {
                            allMusicSelected = false
                            selectedPlaylistIds = emptySet()
                        },
                        enabled = visibleSavedIds.isNotEmpty(),
                    )
                    if (playlistOptions.isEmpty()) {
                        Box(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No playlists found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(playlistOptions, key = { it.key }) { option ->
                                when (option) {
                                    is QueuePlaylistOption.AllMusic -> {
                                        ListItem(
                                            headlineContent = { Text("All Music") },
                                            supportingContent = {
                                                Text(
                                                    "${option.trackCount} ${if (option.trackCount == 1) "song" else "songs"}"
                                                )
                                            },
                                            leadingContent = {
                                                Checkbox(
                                                    checked = allMusicSelected,
                                                    onCheckedChange = ::selectAllMusic,
                                                )
                                            },
                                            modifier =
                                                Modifier.clickable {
                                                    selectAllMusic(!allMusicSelected)
                                                },
                                        )
                                    }
                                    is QueuePlaylistOption.Saved -> {
                                        val playlist = option.summary
                                        val selected = playlist.playlistId in selectedPlaylistIds
                                        ListItem(
                                            headlineContent = {
                                                Text(
                                                    playlist.name,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            supportingContent = {
                                                Text(
                                                    "${playlist.trackCount} ${if (playlist.trackCount == 1) "song" else "songs"}"
                                                )
                                            },
                                            leadingContent = {
                                                Checkbox(
                                                    checked = selected,
                                                    onCheckedChange = { checked ->
                                                        toggleSavedPlaylist(
                                                            playlist.playlistId,
                                                            checked,
                                                        )
                                                    },
                                                )
                                            },
                                            modifier =
                                                Modifier.clickable {
                                                    toggleSavedPlaylist(
                                                        playlist.playlistId,
                                                        !selected,
                                                    )
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                QueueMusicPickerSection.SONGS -> {
                    UnisonSearchField(
                        value = pickerQuery,
                        onValueChange = onPickerQueryChange,
                        placeholder = "Search songs",
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    if (refreshState is LoadState.Loading && stableTracks.isNotEmpty()) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    SelectionHeader(
                        selectedCount = selectedTracks.size,
                        selectAllLabel =
                            if (pickerQuery.isBlank()) "Select all" else "Select results",
                        onSelectAll = {
                            onSelectAllTracks(pickerQuery) { ids ->
                                allMusicSelected = false
                                selectedTracks = selectedTracks + ids
                            }
                        },
                        onClear = { selectedTracks = emptySet() },
                        enabled = stableTracks.isNotEmpty() || pickerQuery.isNotBlank(),
                    )
                    when {
                        stableTracks.isEmpty() && refreshState is LoadState.Loading ->
                            Box(
                                Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        stableTracks.isEmpty() && refreshState is LoadState.Error ->
                            Box(
                                Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton(onClick = pickerTracks::retry) {
                                    Text("Try loading again")
                                }
                            }
                        stableTracks.isEmpty() ->
                            Box(
                                Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (pickerQuery.isBlank()) "No songs yet" else "No songs found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        else ->
                            LazyColumn(Modifier.weight(1f)) {
                                items(stableTracks, key = { "picker:${it.trackId.value}" }) { track
                                    ->
                                    val selected = track.trackId in selectedTracks
                                    ListItem(
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
                                                    )
                                                    .joinToString(" • "),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        leadingContent = {
                                            Checkbox(
                                                checked = selected,
                                                onCheckedChange = { checked ->
                                                    toggleTrack(track.trackId, checked)
                                                },
                                            )
                                        },
                                        modifier =
                                            Modifier.clickable {
                                                toggleTrack(track.trackId, !selected)
                                            },
                                    )
                                }
                                when (pickerTracks.loadState.append) {
                                    is LoadState.Loading ->
                                        item(key = "picker-loading") {
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
                                        item(key = "picker-error") {
                                            TextButton(
                                                onClick = pickerTracks::retry,
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

            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        onAddSelection(
                            allMusicSelected,
                            selectedPlaylistIds.toList(),
                            selectedTracks.toList(),
                            true,
                        )
                    },
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Add next")
                }
                Button(
                    onClick = {
                        onAddSelection(
                            allMusicSelected,
                            selectedPlaylistIds.toList(),
                            selectedTracks.toList(),
                            false,
                        )
                    },
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Add to queue")
                }
            }
        }
    }
}

@Composable
private fun SelectionHeader(
    selectedCount: Int,
    selectAllLabel: String,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$selectedCount selected",
            style = MaterialTheme.typography.labelLarge,
            color =
                if (selectedCount == 0) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (selectedCount > 0) TextButton(onClick = onClear) { Text("Clear") }
        TextButton(onClick = onSelectAll, enabled = enabled) { Text(selectAllLabel) }
    }
}

private fun buildSelectionSummary(
    allMusicSelected: Boolean,
    selectedPlaylistCount: Int,
    selectedTrackCount: Int,
): String =
    buildList {
            if (allMusicSelected) add("All Music")
            if (selectedPlaylistCount > 0) {
                add(
                    "$selectedPlaylistCount ${if (selectedPlaylistCount == 1) "playlist" else "playlists"}"
                )
            }
            if (selectedTrackCount > 0) {
                add("$selectedTrackCount ${if (selectedTrackCount == 1) "song" else "songs"}")
            }
        }
        .joinToString(" · ")

private sealed interface QueuePlaylistOption {
    val key: String

    data class AllMusic(val trackCount: Int) : QueuePlaylistOption {
        override val key: String = "all-music"
    }

    data class Saved(val summary: PlaylistSummary) : QueuePlaylistOption {
        override val key: String = "playlist:${summary.playlistId}"
    }
}

private enum class QueueMusicPickerSection {
    PLAYLISTS,
    SONGS,
}
