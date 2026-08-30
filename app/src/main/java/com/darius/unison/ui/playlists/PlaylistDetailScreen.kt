package com.darius.unison.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.paging.compose.LazyPagingItems
import com.darius.unison.library.PlaylistDetail
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.room.QueueDragPolicy
import com.darius.unison.storage.PlaylistSummary
import kotlin.math.abs

@Composable
internal fun PlaylistDetailScreen(
    detail: PlaylistDetail,
    playlists: List<PlaylistSummary>,
    pickerTracks: LazyPagingItems<TrackDescriptor>,
    pickerQuery: String,
    onPickerQueryChange: (String) -> Unit,
    onRename: (String) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onRemoveTracks: (Collection<Int>) -> Unit,
    onAddTracks: (List<TrackId>) -> Unit,
    onAddTracksToPlaylists: (Set<String>, List<TrackId>, String?) -> Unit,
    onSelectAll: (String, (Set<TrackId>) -> Unit) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var name by remember(detail.name) { mutableStateOf(detail.name) }
    var addSongs by remember { mutableStateOf(false) }
    var addSelectionToPlaylist by remember { mutableStateOf(false) }
    var selectingPlaylist by rememberSaveable(detail.playlistId) { mutableStateOf(false) }
    var selectedIndices by remember(detail.playlistId) { mutableStateOf(setOf<Int>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var reordering by rememberSaveable(detail.playlistId) { mutableStateOf(false) }

    BackHandler(enabled = selectingPlaylist || reordering) {
        when {
            reordering -> reordering = false
            selectingPlaylist -> {
                selectingPlaylist = false
                selectedIndices = emptySet()
            }
        }
    }

    val listState = rememberLazyListState()
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragPointerCenterPx by remember { mutableStateOf<Float?>(null) }
    var dragAutoScrollPx by remember { mutableFloatStateOf(0f) }
    val dragEdgePx = with(LocalDensity.current) { 72.dp.toPx() }
    val maxDragScrollPx = with(LocalDensity.current) { 28.dp.toPx() }

    fun recalculateDrag() {
        if (detail.tracks.isEmpty()) return
        val origin = draggedIndex ?: return
        val pointerCenter = dragPointerCenterPx ?: return
        val layout = listState.layoutInfo
        val visible =
            layout.visibleItemsInfo.map { item ->
                QueueDragPolicy.VisibleItem(
                    queueIndex = item.index,
                    offsetPx = item.offset.toFloat(),
                    sizePx = item.size.toFloat(),
                )
            }
        dragTargetIndex =
            QueueDragPolicy.targetIndex(pointerCenter, visible, origin)
                .coerceIn(0, detail.tracks.lastIndex)
        dragAutoScrollPx =
            QueueDragPolicy.autoScrollPerFrame(
                pointerCenterPx = pointerCenter,
                viewportStartPx = layout.viewportStartOffset.toFloat(),
                viewportEndPx = layout.viewportEndOffset.toFloat(),
                edgeSizePx = dragEdgePx,
                maxScrollPx = maxDragScrollPx,
            )
    }

    fun startDrag(index: Int) {
        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        draggedIndex = index
        dragTargetIndex = index
        dragOffsetPx = 0f
        dragPointerCenterPx = visibleItem?.let { it.offset + it.size / 2f }
        dragAutoScrollPx = 0f
        recalculateDrag()
    }

    fun updateDrag(deltaY: Float) {
        dragOffsetPx += deltaY
        dragPointerCenterPx = (dragPointerCenterPx ?: 0f) + deltaY
        recalculateDrag()
    }

    fun resetDrag() {
        draggedIndex = null
        dragTargetIndex = null
        dragOffsetPx = 0f
        dragPointerCenterPx = null
        dragAutoScrollPx = 0f
    }

    fun finishDrag() {
        val origin = draggedIndex
        val target = dragTargetIndex
        resetDrag()
        if (origin != null && target != null && origin != target) onMoveTrack(origin, target)
    }

    LaunchedEffect(detail.tracks.map { it.trackId }) {
        if (draggedIndex != null) resetDrag()
    }
    LaunchedEffect(draggedIndex) {
        while (draggedIndex != null) {
            withFrameNanos {}
            val requested = dragAutoScrollPx
            if (abs(requested) < 0.5f) continue
            val consumed = listState.scrollBy(requested)
            if (abs(consumed) < 0.5f) {
                dragAutoScrollPx = 0f
                continue
            }
            dragOffsetPx += consumed
            recalculateDrag()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                reordering ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Edit order",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                )
                                Text(
                                    "Hold a drag handle and move songs where you want them.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { reordering = false }) { Text("Done") }
                        }
                    }
                selectingPlaylist ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${selectedIndices.size} selected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                                )
                                TextButton(
                                    onClick = {
                                        selectedIndices =
                                            if (selectedIndices.size == detail.tracks.size)
                                                emptySet()
                                            else detail.tracks.indices.toSet()
                                    }
                                ) {
                                    Text(
                                        if (selectedIndices.size == detail.tracks.size) "Clear"
                                        else "Select all"
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        selectingPlaylist = false
                                        selectedIndices = emptySet()
                                    }
                                ) {
                                    Text("Done")
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = { addSelectionToPlaylist = true },
                                    enabled = selectedIndices.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Playlist")
                                }
                                OutlinedButton(
                                    onClick = {
                                        onRemoveTracks(selectedIndices)
                                        selectedIndices = emptySet()
                                        selectingPlaylist = false
                                    },
                                    enabled = selectedIndices.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.DeleteOutline, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remove")
                                }
                            }
                        }
                    }
                else ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onPickerQueryChange("")
                                addSongs = true
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add songs")
                        }
                        TextButton(
                            onClick = { selectingPlaylist = true },
                            enabled = detail.tracks.isNotEmpty(),
                        ) {
                            Text("Select")
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, "Playlist actions")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = {
                                        menu = false
                                        rename = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit order") },
                                    leadingIcon = { Icon(Icons.Default.DragHandle, null) },
                                    enabled = detail.tracks.size > 1,
                                    onClick = {
                                        menu = false
                                        selectingPlaylist = false
                                        selectedIndices = emptySet()
                                        reordering = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export M3U") },
                                    leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                                    onClick = {
                                        menu = false
                                        onExport()
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        menu = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
            }
        }

        if (detail.tracks.isEmpty()) {
            EmptyState(
                title = "Empty playlist",
                text = "Add songs from your library and arrange them in the order you want.",
                icon = Icons.Default.LibraryMusic,
                actionLabel = "Add songs",
                onAction = {
                    onPickerQueryChange("")
                    addSongs = true
                },
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(
                    detail.tracks,
                    key = { index, track -> "$index:${track.trackId.value}" },
                ) { index, track ->
                    PlaylistTrackRow(
                        index = index,
                        lastIndex = detail.tracks.lastIndex,
                        track = track,
                        selected = index in selectedIndices,
                        selecting = selectingPlaylist,
                        reordering = reordering,
                        draggedIndex = draggedIndex,
                        dragTargetIndex = dragTargetIndex,
                        dragOffsetPx = if (draggedIndex == index) dragOffsetPx else 0f,
                        onToggleSelected = {
                            selectedIndices =
                                if (index in selectedIndices) selectedIndices - index
                                else selectedIndices + index
                        },
                        onDragStart = { startDrag(index) },
                        onDragDelta = ::updateDrag,
                        onDragCancel = ::resetDrag,
                        onDragEnd = ::finishDrag,
                        onMove = { onMoveTrack(index, it) },
                        onRemove = { onRemoveTracks(listOf(index)) },
                    )
                }
            }
        }
    }

    if (rename) {
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename playlist") },
            text = { OutlinedTextField(name, { name = it.take(60) }, singleLine = true) },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(name)
                        rename = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } },
        )
    }

    if (addSongs) {
        TrackPickerSheet(
            title = "Add songs to ${detail.name}",
            tracks = pickerTracks,
            query = pickerQuery,
            onQueryChange = onPickerQueryChange,
            onSelectAll = onSelectAll,
            onDismiss = {
                onPickerQueryChange("")
                addSongs = false
            },
            onConfirm = { selected ->
                onAddTracks(selected)
                onPickerQueryChange("")
                addSongs = false
            },
        )
    }

    if (addSelectionToPlaylist) {
        val selectedTrackIds = selectedIndices.sorted().map { detail.tracks[it].trackId }
        PlaylistPickerSheet(
            playlists = playlists,
            title = "Add selected songs to playlist",
            excludedPlaylistId = detail.playlistId,
            onDismiss = { addSelectionToPlaylist = false },
            onConfirm = { playlistIds, newPlaylistName ->
                addSelectionToPlaylist = false
                onAddTracksToPlaylists(playlistIds, selectedTrackIds, newPlaylistName)
                selectedIndices = emptySet()
                selectingPlaylist = false
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("The music files will stay in your library.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlaylistTrackRow(
    index: Int,
    lastIndex: Int,
    track: TrackDescriptor,
    selected: Boolean,
    selecting: Boolean,
    reordering: Boolean,
    draggedIndex: Int?,
    dragTargetIndex: Int?,
    dragOffsetPx: Float,
    onToggleSelected: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragCancel: () -> Unit,
    onDragEnd: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val dragged = draggedIndex
    val target = dragTargetIndex
    val dragActive = dragged != null && target != null
    val rowHeightPx = if (dragActive) with(LocalDensity.current) { 64.dp.toPx() } else 0f
    val displacedOffsetPx =
        if (dragged == null || target == null || dragged == index) {
            0f
        } else {
            when {
                dragged < target && index in (dragged + 1)..target -> -rowHeightPx
                dragged > target && index in target until dragged -> rowHeightPx
                else -> 0f
            }
        }
    val dragModifier =
        if (dragActive && (dragged == index || displacedOffsetPx != 0f)) {
            Modifier.zIndex(if (dragged == index) 1f else 0f).graphicsLayer {
                translationY = if (dragged == index) dragOffsetPx else displacedOffsetPx
                shadowElevation = if (dragged == index && dragOffsetPx != 0f) 8.dp.toPx() else 0f
            }
        } else {
            Modifier
        }

    ListItem(
        modifier =
            Modifier.padding(horizontal = 8.dp)
                .then(dragModifier)
                .semantics {
                    if (reordering) {
                        customActions = buildList {
                            if (index > 0) {
                                add(
                                    CustomAccessibilityAction("Move up") {
                                        onMove(index - 1)
                                        true
                                    }
                                )
                            }
                            if (index < lastIndex) {
                                add(
                                    CustomAccessibilityAction("Move down") {
                                        onMove(index + 1)
                                        true
                                    }
                                )
                            }
                        }
                    }
                }
                .then(if (selecting) Modifier.clickable(onClick = onToggleSelected) else Modifier),
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
        leadingContent = {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            } else {
                Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
            }
        },
        trailingContent = {
            when {
                reordering ->
                    Icon(
                        Icons.Default.DragHandle,
                        "Hold and drag to reorder",
                        modifier =
                            Modifier.size(44.dp).padding(10.dp).pointerInput(index, lastIndex) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { onDragStart() },
                                    onDragCancel = onDragCancel,
                                    onDragEnd = onDragEnd,
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDragDelta(dragAmount.y)
                                    },
                                )
                            },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                !selecting ->
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, "Song actions")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Remove from playlist") },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                                onClick = {
                                    menu = false
                                    onRemove()
                                },
                            )
                        }
                    }
            }
        },
    )
}
