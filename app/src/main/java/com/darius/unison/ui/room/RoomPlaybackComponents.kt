package com.darius.unison.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.darius.unison.R
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferProgress
import com.darius.unison.model.TransportAction
import com.darius.unison.model.TransportCommandPhase
import com.darius.unison.model.TransportCommandStatus

@Composable
internal fun CompactQueueSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                "Search queue",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, "Clear queue search", Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
internal fun RoomSeekSlider(
    playbackPositionState: State<Long>,
    durationMs: Long,
    enabled: Boolean,
    transportStatus: TransportCommandStatus?,
    onSeek: (Long) -> Unit,
) {
    val playbackPositionMs by playbackPositionState
    var seekPreview by remember { mutableFloatStateOf(playbackPositionMs.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var submittedPositionMs by remember { mutableStateOf<Long?>(null) }
    val canonicalSeekStatus = transportStatus?.takeIf { it.action == TransportAction.SEEK }
    val seekPending = canonicalSeekStatus?.active == true

    LaunchedEffect(playbackPositionMs, durationMs, dragging, seekPending, submittedPositionMs) {
        if (!dragging && !seekPending && submittedPositionMs == null) {
            seekPreview = playbackPositionMs.coerceIn(0, durationMs).toFloat()
        }
    }
    LaunchedEffect(canonicalSeekStatus?.commandId, canonicalSeekStatus?.phase) {
        when (canonicalSeekStatus?.phase) {
            TransportCommandPhase.SUBMITTED,
            TransportCommandPhase.ACCEPTED,
            TransportCommandPhase.SCHEDULED,
            TransportCommandPhase.EXECUTING ->
                canonicalSeekStatus.requestedPositionMs?.let { requested ->
                    submittedPositionMs = requested
                    if (!dragging) seekPreview = requested.coerceIn(0, durationMs).toFloat()
                }

            TransportCommandPhase.SETTLED,
            TransportCommandPhase.SUPERSEDED,
            TransportCommandPhase.REJECTED -> {
                submittedPositionMs = null
                if (!dragging)
                    seekPreview = playbackPositionState.value.coerceIn(0, durationMs).toFloat()
            }

            null -> if (!seekPending && !dragging) submittedPositionMs = null
        }
    }

    Slider(
        value = seekPreview.coerceIn(0f, durationMs.toFloat()),
        onValueChange = {
            dragging = true
            seekPreview = it
        },
        onValueChangeFinished = {
            dragging = false
            val requested = seekPreview.toLong()
            submittedPositionMs = requested
            onSeek(requested)
        },
        valueRange = 0f..durationMs.toFloat(),
        enabled = enabled,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration(seekPreview.toLong()), style = MaterialTheme.typography.labelSmall)
        Text(
            if (enabled) formatDuration(durationMs) else "—:—",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun TransportControlButton(
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val controlScale by
        animateFloatAsState(
            targetValue =
                when {
                    pressed -> 0.86f
                    active -> 0.94f
                    else -> 1f
                },
            label = "transport control feedback",
        )
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = controlScale
                    scaleY = controlScale
                },
        ) {
            Box(Modifier.semantics { this.contentDescription = contentDescription }) { content() }
        }
        if (active) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
internal fun TransportPlayPauseButton(
    isPlaying: Boolean,
    pending: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val controlScale by
        animateFloatAsState(
            targetValue =
                when {
                    pressed -> 0.86f
                    pending -> 0.95f
                    else -> 1f
                },
            label = "play pause feedback",
        )
    val outerSize = if (compact) 48.dp else 64.dp
    val buttonSize = if (compact) 44.dp else 58.dp
    val iconSize = if (compact) 24.dp else 32.dp
    val contentDescription =
        when {
            isPlaying && pending -> "Pause; play is scheduled"
            isPlaying -> "Pause"
            pending -> "Play; pause is scheduled"
            else -> "Play"
        }
    Box(Modifier.size(outerSize), contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier =
                Modifier.size(buttonSize).graphicsLayer {
                    scaleX = controlScale
                    scaleY = controlScale
                },
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription,
                Modifier.size(iconSize),
            )
        }
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(outerSize),
                strokeWidth = if (compact) 2.dp else 2.5.dp,
            )
        }
    }
}

@Composable
internal fun transportStatusText(
    status: TransportCommandStatus?,
    queue: List<com.darius.unison.model.QueueItem>,
): String? {
    if (status == null || status.phase == TransportCommandPhase.SUPERSEDED) return null
    val targetTitle =
        status.queueItemId?.let { id ->
            queue.firstOrNull { it.queueItemId == id }?.track?.displayTitle
        }
    @Composable
    fun targetOrFallback(withTarget: Int, fallback: Int): String =
        targetTitle?.let { stringResource(withTarget, it) } ?: stringResource(fallback)

    return when (status.phase) {
        TransportCommandPhase.SUBMITTED ->
            when (status.action) {
                TransportAction.PLAY -> stringResource(R.string.transport_sending_play)
                TransportAction.PAUSE -> stringResource(R.string.transport_sending_pause)
                TransportAction.SEEK -> stringResource(R.string.transport_sending_seek)
                TransportAction.NEXT ->
                    targetOrFallback(
                        R.string.transport_next_target,
                        R.string.transport_choosing_next,
                    )
                TransportAction.PREVIOUS ->
                    targetOrFallback(
                        R.string.transport_previous_target,
                        R.string.transport_choosing_previous,
                    )
                TransportAction.PLAY_ITEM ->
                    targetOrFallback(
                        R.string.transport_opening_target,
                        R.string.transport_opening_song,
                    )
            }

        TransportCommandPhase.ACCEPTED ->
            status.message ?: stringResource(R.string.transport_accepted)
        TransportCommandPhase.SCHEDULED ->
            when (status.action) {
                TransportAction.PLAY -> stringResource(R.string.transport_starting_together)
                TransportAction.PAUSE -> stringResource(R.string.transport_pausing_together)
                TransportAction.SEEK -> stringResource(R.string.transport_seeking_together)
                TransportAction.NEXT ->
                    targetOrFallback(
                        R.string.transport_next_target,
                        R.string.transport_changing_song,
                    )
                TransportAction.PREVIOUS ->
                    targetOrFallback(
                        R.string.transport_previous_target,
                        R.string.transport_changing_song,
                    )
                TransportAction.PLAY_ITEM ->
                    targetOrFallback(
                        R.string.transport_playing_target_pending,
                        R.string.transport_changing_song,
                    )
            }

        TransportCommandPhase.EXECUTING ->
            when (status.action) {
                TransportAction.PLAY -> stringResource(R.string.transport_starting)
                TransportAction.PAUSE -> stringResource(R.string.transport_pausing)
                TransportAction.SEEK -> stringResource(R.string.transport_seeking)
                TransportAction.NEXT,
                TransportAction.PREVIOUS,
                TransportAction.PLAY_ITEM ->
                    targetOrFallback(
                        R.string.transport_switching_target,
                        R.string.transport_switching_song,
                    )
            }

        TransportCommandPhase.REJECTED ->
            status.message ?: stringResource(R.string.transport_action_failed)

        TransportCommandPhase.SETTLED ->
            when (status.action) {
                TransportAction.PLAY -> stringResource(R.string.transport_playing)
                TransportAction.PAUSE -> stringResource(R.string.transport_paused)
                TransportAction.SEEK -> stringResource(R.string.transport_position_updated)
                TransportAction.NEXT ->
                    targetOrFallback(
                        R.string.transport_now_playing_target,
                        R.string.transport_next_ready,
                    )
                TransportAction.PREVIOUS ->
                    targetOrFallback(
                        R.string.transport_now_playing_target,
                        R.string.transport_previous_ready,
                    )
                TransportAction.PLAY_ITEM ->
                    targetOrFallback(
                        R.string.transport_now_playing_target,
                        R.string.transport_song_ready,
                    )
            }

        TransportCommandPhase.SUPERSEDED -> null
    }
}

@Composable
internal fun TransportStatusLine(
    status: TransportCommandStatus?,
    queue: List<com.darius.unison.model.QueueItem>,
) {
    val text = transportStatusText(status, queue)
    AnimatedVisibility(visible = text != null) {
        if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (status?.phase == TransportCommandPhase.REJECTED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun CompactRoomPlayer(
    track: TrackDescriptor?,
    queue: List<com.darius.unison.model.QueueItem>,
    isPlaying: Boolean,
    transportStatus: TransportCommandStatus?,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    playPauseEnabled: Boolean = true,
) {
    Card(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    track?.displayTitle ?: "Nothing playing",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val transportText = transportStatusText(transportStatus, queue)
                Text(
                    transportText
                        ?: track?.artist?.takeIf(String::isNotBlank)
                        ?: if (isPlaying) "Playing" else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (transportStatus?.phase == TransportCommandPhase.REJECTED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            TransportPlayPauseButton(
                isPlaying = isPlaying,
                pending =
                    transportStatus?.active == true &&
                        (transportStatus.action == TransportAction.PLAY ||
                            transportStatus.action == TransportAction.PAUSE),
                onClick = onPlayPause,
                enabled = playPauseEnabled,
                compact = true,
            )
        }
    }
}

@Composable
internal fun TransferStatusCard(
    transfers: List<TransferProgress>,
    titles: Map<TrackId, String>,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            transfers.take(3).forEach { transfer ->
                val title = titles[transfer.trackId] ?: "Music"
                val status =
                    when (transfer.state) {
                        MemberTrackState.RECEIVING -> "Receiving $title"
                        MemberTrackState.VERIFYING -> "Verifying $title"
                        MemberTrackState.CANCELLED -> "Cancelled $title"
                        MemberTrackState.FAILED -> "Could not receive $title"
                        else -> title
                    }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (transfer.state == MemberTrackState.RECEIVING) {
                            Text(
                                "${(transfer.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (
                        transfer.state == MemberTrackState.CANCELLED ||
                            transfer.state == MemberTrackState.FAILED
                    ) {
                        Text(
                            "Check the Wi-Fi connection. Unison can try another source.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { transfer.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (transfers.size > 3) {
                Text(
                    "${transfers.size - 3} more transfer${if (transfers.size == 4) "" else "s"} in progress",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun QueueRow(
    index: Int,
    lastIndex: Int,
    track: TrackDescriptor,
    current: Boolean,
    playing: Boolean,
    temporary: Boolean,
    canReorder: Boolean,
    draggedIndex: Int?,
    dragTargetIndex: Int?,
    dragOffsetPx: Float,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragCancel: () -> Unit,
    onDragEnd: () -> Unit,
    onMove: (Int) -> Unit,
    playEnabled: Boolean = true,
    onPlay: () -> Unit,
    onMoveNext: () -> Unit,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val estimatedRowHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
    val displacedOffsetPx =
        when {
            draggedIndex == null || dragTargetIndex == null || draggedIndex == index -> 0f
            draggedIndex < dragTargetIndex && index in (draggedIndex + 1)..dragTargetIndex ->
                -estimatedRowHeightPx

            draggedIndex > dragTargetIndex && index in dragTargetIndex until draggedIndex ->
                estimatedRowHeightPx

            else -> 0f
        }
    val animatedDisplacementPx by
        animateFloatAsState(
            targetValue = displacedOffsetPx,
            label = "Queue drop position",
        )
    ListItem(
        modifier =
            Modifier.zIndex(if (draggedIndex == index) 1f else 0f)
                .graphicsLayer {
                    translationY =
                        if (draggedIndex == index) dragOffsetPx else animatedDisplacementPx
                    shadowElevation = if (dragOffsetPx == 0f) 0f else 8.dp.toPx()
                }
                .semantics {
                    customActions = buildList {
                        if (canReorder && index > 0) {
                            add(
                                CustomAccessibilityAction("Move up") {
                                    onMove(index - 1)
                                    true
                                }
                            )
                        }
                        if (canReorder && index < lastIndex) {
                            add(
                                CustomAccessibilityAction("Move down") {
                                    onMove(index + 1)
                                    true
                                }
                            )
                        }
                        add(
                            CustomAccessibilityAction("Play next") {
                                onMoveNext()
                                true
                            }
                        )
                        if (temporary) {
                            add(
                                CustomAccessibilityAction("Keep on this phone") {
                                    onKeep()
                                    true
                                }
                            )
                        }
                        add(
                            CustomAccessibilityAction("Remove from queue") {
                                onRemove()
                                true
                            }
                        )
                    }
                }
                .clickable(enabled = draggedIndex == null && playEnabled, onClick = onPlay),
        headlineContent = {
            Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                listOfNotNull(
                        when {
                            playing -> "Playing"
                            current -> "Paused"
                            else -> track.artist?.takeIf(String::isNotBlank)
                        },
                        "Temporary".takeIf { temporary },
                        formatDuration(track.durationMs),
                    )
                    .joinToString(" • "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canReorder) {
                    Icon(
                        Icons.Default.DragHandle,
                        "Hold and drag to reorder",
                        modifier =
                            Modifier.size(48.dp).padding(12.dp).pointerInput(index, lastIndex) {
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
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, "Queue actions")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Play next") },
                            onClick = {
                                menu = false
                                onMoveNext()
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
                        }
                        if (canReorder) {
                            DropdownMenuItem(
                                text = { Text("Move up") },
                                enabled = index > 0,
                                onClick = {
                                    menu = false
                                    onMove(index - 1)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move down") },
                                enabled = index < lastIndex,
                                onClick = {
                                    menu = false
                                    onMove(index + 1)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove from queue") },
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
