package com.darius.unison.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TransportAction

@Composable
internal fun SharedCompactPlayer(
    track: TrackDescriptor?,
    isPlaying: Boolean,
    primaryControl: RoomPlaybackUiPolicy.PrimaryControl,
    pendingAction: TransportAction?,
    onPrevious: () -> Unit,
    onPrimary: () -> Unit,
    onNext: () -> Unit,
    navigationEnabled: Boolean,
    primaryEnabled: Boolean,
    modifier: Modifier = Modifier,
    statusText: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
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
                Text(
                    statusText
                        ?: track?.artist?.takeIf(String::isNotBlank)
                        ?: if (isPlaying) "Playing" else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TransportControlButton(
                active = pendingAction == TransportAction.PREVIOUS,
                enabled = navigationEnabled,
                onClick = onPrevious,
                contentDescription = "Previous",
            ) {
                Icon(Icons.Default.SkipPrevious, null)
            }
            TransportPrimaryButton(
                control = primaryControl,
                pending =
                    pendingAction == TransportAction.PLAY || pendingAction == TransportAction.PAUSE,
                onClick = onPrimary,
                enabled = primaryEnabled,
                compact = true,
            )
            TransportControlButton(
                active = pendingAction == TransportAction.NEXT,
                enabled = navigationEnabled,
                onClick = onNext,
                contentDescription = "Next",
            ) {
                Icon(Icons.Default.SkipNext, null)
            }
        }
    }
}

@Composable
internal fun RoomQueueToolbar(
    query: String,
    repeatMode: RepeatMode,
    queueEnabled: Boolean,
    shuffleAvailable: Boolean,
    canSaveQueue: Boolean,
    canClearPlayed: Boolean,
    reorderMode: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleReorder: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSaveQueue: () -> Unit,
    onClearPlayed: () -> Unit,
    onClearQueue: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        UnisonSearchField(
            value = query,
            onValueChange = { onQueryChange(it.take(80)) },
            placeholder = "Search queue",
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, "Queue options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Shuffle upcoming songs") },
                    enabled = shuffleAvailable,
                    onClick = {
                        menuOpen = false
                        onShuffle()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            when (repeatMode) {
                                RepeatMode.OFF -> "Repeat: Off"
                                RepeatMode.ALL -> "Repeat: Queue"
                                RepeatMode.ONE -> "Repeat: One song"
                            }
                        )
                    },
                    enabled = queueEnabled,
                    onClick = {
                        menuOpen = false
                        onRepeat()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (reorderMode) "Done reordering" else "Reorder queue") },
                    enabled = queueEnabled,
                    onClick = {
                        menuOpen = false
                        onToggleReorder()
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Save queue as playlist") },
                    enabled = canSaveQueue,
                    onClick = {
                        menuOpen = false
                        onSaveQueue()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Clear played songs") },
                    enabled = canClearPlayed,
                    onClick = {
                        menuOpen = false
                        onClearPlayed()
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Clear queue", color = MaterialTheme.colorScheme.error) },
                    enabled = queueEnabled,
                    onClick = {
                        menuOpen = false
                        onClearQueue()
                    },
                )
            }
        }
    }
}
