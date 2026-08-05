package com.darius.unison.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TransportAction

@Composable
internal fun ParticipantStatus(
    name: String,
    connected: Boolean,
    trackState: MemberTrackState,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Default.Circle,
            contentDescription = memberStatusLabel(connected, trackState),
            modifier = Modifier.size(10.dp),
            tint = memberStatusColor(connected, trackState),
        )
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun memberStatusColor(connected: Boolean, trackState: MemberTrackState): Color =
    when {
        !connected -> MaterialTheme.colorScheme.outline
        trackState == MemberTrackState.FAILED || trackState == MemberTrackState.CANCELLED ->
            MaterialTheme.colorScheme.error
        trackState == MemberTrackState.RECEIVING ||
            trackState == MemberTrackState.VERIFYING ||
            trackState == MemberTrackState.PREPARING_PLAYER ||
            trackState == MemberTrackState.CHECKING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

internal fun memberStatusLabel(connected: Boolean, trackState: MemberTrackState): String =
    when {
        !connected -> "Offline"
        trackState == MemberTrackState.RECEIVING || trackState == MemberTrackState.VERIFYING ->
            "Receiving music"
        trackState == MemberTrackState.PREPARING_PLAYER ||
            trackState == MemberTrackState.CHECKING -> "Getting ready"
        trackState == MemberTrackState.FAILED || trackState == MemberTrackState.CANCELLED ->
            "Needs attention"
        else -> "Ready"
    }

@Composable
internal fun SharedCompactPlayer(
    track: TrackDescriptor?,
    isPlaying: Boolean,
    pendingAction: TransportAction?,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    navigationEnabled: Boolean,
    playPauseEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
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
                    track?.artist?.takeIf(String::isNotBlank)
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
            TransportPlayPauseButton(
                isPlaying = isPlaying,
                pending =
                    pendingAction == TransportAction.PLAY || pendingAction == TransportAction.PAUSE,
                onClick = onPlayPause,
                enabled = playPauseEnabled,
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
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    queueEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Surface(
            modifier = Modifier.weight(1f).height(44.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    null,
                    Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BasicTextField(
                    value = query,
                    onValueChange = { onQueryChange(it.take(80)) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    singleLine = true,
                    textStyle =
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty())
                                Text(
                                    "Search queue",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, "Clear queue search", Modifier.size(18.dp))
                    }
                }
            }
        }
        IconButton(onClick = onShuffle, enabled = queueEnabled) {
            Icon(
                Icons.Default.Shuffle,
                if (shuffleEnabled) "Turn shuffle off" else "Shuffle queue",
                tint =
                    if (shuffleEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRepeat, enabled = queueEnabled) {
            Icon(
                if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                when (repeatMode) {
                    RepeatMode.OFF -> "Repeat off"
                    RepeatMode.ALL -> "Repeat queue"
                    RepeatMode.ONE -> "Repeat one"
                },
                tint =
                    if (repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onClear, enabled = queueEnabled) {
            Icon(Icons.Default.DeleteOutline, "Clear queue")
        }
    }
}
