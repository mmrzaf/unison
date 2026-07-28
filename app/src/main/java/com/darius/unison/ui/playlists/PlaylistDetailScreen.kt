package com.darius.unison.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.darius.unison.app.unisonContainer
import com.darius.unison.library.LibrarySort
import com.darius.unison.library.PlaylistDetail
import com.darius.unison.model.AppCommand
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomOptions
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun PlaylistDetailScreen(
    detail: PlaylistDetail,
    pickerTracks: LazyPagingItems<TrackDescriptor>,
    pickerQuery: String,
    onPickerQueryChange: (String) -> Unit,
    roomActive: Boolean,
    onRename: (String) -> Unit,
    onUpdateTracks: (List<TrackId>) -> Unit,
    onAddTracks: (List<TrackId>) -> Unit,
    onAddToRoom: () -> Unit,
    onAddTracksToRoom: (List<TrackId>) -> Unit,
    onSelectAll: (String, (Set<TrackId>) -> Unit) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var name by remember(detail.name) { mutableStateOf(detail.name) }
    var addSongs by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<TrackId>()) }
    var selectingPlaylist by rememberSaveable(detail.playlistId) { mutableStateOf(false) }
    var selectedIndices by remember(detail.playlistId) { mutableStateOf(setOf<Int>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${detail.tracks.size} ${if (detail.tracks.size == 1) "song" else "songs"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        selectingPlaylist = !selectingPlaylist
                        if (!selectingPlaylist) selectedIndices = emptySet()
                    },
                    enabled = detail.tracks.isNotEmpty(),
                ) {
                    Text(if (selectingPlaylist) "Done" else "Select")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Playlist actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menu = false; rename = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Export M3U") },
                            leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                            onClick = { menu = false; onExport() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
            if (selectingPlaylist) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${selectedIndices.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            selectedIndices = if (selectedIndices.size == detail.tracks.size) {
                                emptySet()
                            } else {
                                detail.tracks.indices.toSet()
                            }
                        }) {
                            Text(if (selectedIndices.size == detail.tracks.size) "Clear" else "Select all")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (roomActive) {
                            FilledTonalButton(
                                onClick = {
                                    onAddTracksToRoom(selectedIndices.sorted().map { detail.tracks[it].trackId })
                                    selectedIndices = emptySet()
                                    selectingPlaylist = false
                                },
                                enabled = selectedIndices.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add to room")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onUpdateTracks(
                                    detail.tracks.map { it.trackId }
                                        .filterIndexed { index, _ -> index !in selectedIndices }
                                )
                                selectedIndices = emptySet()
                                selectingPlaylist = false
                            },
                            enabled = selectedIndices.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.DeleteOutline, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Remove")
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            addSongs = true
                            selected = emptySet()
                            onPickerQueryChange("")
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add songs")
                    }
                    if (roomActive) {
                        OutlinedButton(
                            onClick = onAddToRoom,
                            enabled = detail.tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add to room")
                        }
                    }
                }
            }
        }
        if (detail.tracks.isEmpty()) {
            EmptyState("Empty playlist", "Add songs from your library.", Icons.AutoMirrored.Filled.QueueMusic)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(detail.tracks, key = { index, track -> "$index:${track.trackId.value}" }) { index, track ->
                    ListItem(
                        modifier = if (selectingPlaylist) {
                            Modifier.clickable {
                                selectedIndices = if (index in selectedIndices) {
                                    selectedIndices - index
                                } else {
                                    selectedIndices + index
                                }
                            }
                        } else {
                            Modifier
                        },
                        headlineContent = { Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    track.artist?.takeIf(String::isNotBlank),
                                    formatDuration(track.durationMs),
                                ).joinToString(" • "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = {
                            if (selectingPlaylist) {
                                Checkbox(
                                    checked = index in selectedIndices,
                                    onCheckedChange = { checked ->
                                        selectedIndices = if (checked) {
                                            selectedIndices + index
                                        } else {
                                            selectedIndices - index
                                        }
                                    },
                                )
                            } else {
                                Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                            }
                        },
                        trailingContent = {
                            if (!selectingPlaylist) PlaylistTrackActions(
                                canMoveUp = index > 0,
                                canMoveDown = index < detail.tracks.lastIndex,
                                onMoveUp = {
                                    val ids = detail.tracks.map { it.trackId }.toMutableList()
                                    val moved = ids.removeAt(index)
                                    ids.add(index - 1, moved)
                                    onUpdateTracks(ids)
                                },
                                onMoveDown = {
                                    val ids = detail.tracks.map { it.trackId }.toMutableList()
                                    val moved = ids.removeAt(index)
                                    ids.add(index + 1, moved)
                                    onUpdateTracks(ids)
                                },
                                onRemove = {
                                    val ids = detail.tracks.map { it.trackId }.toMutableList()
                                    ids.removeAt(index)
                                    onUpdateTracks(ids)
                                },
                            )
                        },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                }
            }
        }
    }

    if (rename) {
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename playlist") },
            text = { OutlinedTextField(name, { name = it.take(60) }, singleLine = true) },
            confirmButton = { Button(onClick = { onRename(name); rename = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } },
        )
    }
    if (addSongs) {
        AlertDialog(
            onDismissRequest = { addSongs = false },
            title = { Text("Add songs") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pickerQuery,
                        onValueChange = onPickerQueryChange,
                        placeholder = { Text("Search music") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${selected.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            onSelectAll(pickerQuery) { all ->
                                selected = if (selected.size == all.size) emptySet() else all
                            }
                        }) {
                            Text("Select all")
                        }
                    }
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(
                            count = pickerTracks.itemCount,
                            key = pickerTracks.itemKey { it.trackId.value },
                        ) { index ->
                            pickerTracks[index]?.let { track ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = track.trackId in selected,
                                        onCheckedChange = { checked ->
                                            selected =
                                                if (checked) selected + track.trackId else selected - track.trackId
                                        },
                                    )
                                    Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAddTracks(selected.toList()); addSongs = false },
                    enabled = selected.isNotEmpty()
                ) {
                    Text("Add")
                }
            },
            dismissButton = { TextButton(onClick = { addSongs = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("The music files will stay in your library.") },
            confirmButton = { Button(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun PlaylistTrackActions(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Song actions") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Move up") },
                enabled = canMoveUp,
                onClick = { menu = false; onMoveUp() },
            )
            DropdownMenuItem(
                text = { Text("Move down") },
                enabled = canMoveDown,
                onClick = { menu = false; onMoveDown() },
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                onClick = { menu = false; onRemove() },
            )
        }
    }
}
