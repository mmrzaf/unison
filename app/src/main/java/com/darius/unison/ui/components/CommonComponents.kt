package com.darius.unison.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.darius.unison.library.LibrarySort
import com.darius.unison.model.RepeatMode
import java.util.Locale

private val SAFE_FILE_NAME_CHARS = Regex("[^a-z0-9._-]+")

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun OperationBanner(
    progress: ImportProgress?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (progress == null) {
                Text(
                    "Working…",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(progress.headline, style = MaterialTheme.typography.labelLarge)
                        progress.detail?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (progress.total > 0) {
                            Text(
                                "${progress.completed} of ${progress.total}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text("Cancel")
                    }
                }
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
internal fun LoadError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Music could not be loaded", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
internal fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun StorageLine(label: String, bytes: Long) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(formatBytes(bytes), fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun EmptyState(
    title: String,
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
                Text(actionLabel)
            }
        }
    }
}

internal fun RepeatMode.next(): RepeatMode =
    when (this) {
        RepeatMode.OFF -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
    }

internal fun LibrarySort.displayName(): String =
    when (this) {
        LibrarySort.RECENT -> "Recently added"
        LibrarySort.TITLE -> "Title"
        LibrarySort.ARTIST -> "Artist"
        LibrarySort.ALBUM -> "Album"
    }

internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.getDefault(), hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.getDefault(), minutes, seconds)
    }
}

internal fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(Locale.getDefault(), bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(Locale.getDefault(), bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(Locale.getDefault(), bytes / 1_000.0)
        else -> "$bytes B"
    }

internal fun String.safeFileName(): String =
    lowercase(Locale.ROOT).replace(SAFE_FILE_NAME_CHARS, "-").trim('-').ifBlank {
        "unison-playlist"
    }

internal val M3U_TYPES =
    arrayOf(
        "audio/x-mpegurl",
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "text/plain",
    )
