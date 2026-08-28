package com.darius.unison.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import java.io.File
import java.util.ArrayDeque
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticEvent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RoomLogSeverityFilter(
    val label: String,
    val minimumSeverityNumber: Int,
) {
    ALL("All", 0),
    INFO("Info+", 9),
    WARN("Warn+", 13),
    ERROR("Errors", 17),
}

@Composable
internal fun RoomLogsDialog(
    revision: StateFlow<Long>,
    loadEvents: () -> List<DiagnosticEvent>,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val observedRevision by revision.collectAsStateWithLifecycle()
    val events = remember(observedRevision) { loadEvents() }
    var query by rememberSaveable { mutableStateOf("") }
    var severityFilter by remember { mutableStateOf(RoomLogSeverityFilter.ALL) }
    var categoryFilter by remember { mutableStateOf<DiagnosticCategory?>(null) }
    var copied by remember { mutableStateOf(false) }

    val availableCategories =
        remember(events) { events.map(DiagnosticEvent::category).distinct().sortedBy { it.wireName } }
    val filtered =
        remember(events, query, severityFilter, categoryFilter) {
            val normalizedQuery = query.trim()
            events.asSequence()
                .filter { it.severity.severityNumber >= severityFilter.minimumSeverityNumber }
                .filter { categoryFilter == null || it.category == categoryFilter }
                .filter { event ->
                    normalizedQuery.isEmpty() ||
                        event.eventName.contains(normalizedQuery, ignoreCase = true) ||
                        event.body?.contains(normalizedQuery, ignoreCase = true) == true ||
                        event.component.contains(normalizedQuery, ignoreCase = true) ||
                        event.attributes.any { (key, value) ->
                            key.contains(normalizedQuery, ignoreCase = true) ||
                                value?.toString()?.contains(normalizedQuery, ignoreCase = true) == true
                        }
                }
                .toList()
                .asReversed()
        }
    val warningCount = remember(events) { events.count { it.severity.severityNumber in 13..16 } }
    val errorCount = remember(events) { events.count { it.severity.severityNumber >= 17 } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Diagnostics",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${events.size} events · $warningCount warnings · $errorCount errors",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        enabled = filtered.isNotEmpty(),
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = roomLogClipboardText(filtered)
                            copied =
                                runCatching {
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText("Unison room logs", text)
                                        )
                                    }
                                    .isSuccess
                        },
                    ) {
                        Icon(
                            if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                            contentDescription = null,
                        )
                        Text(if (copied) "Copied" else "Copy", modifier = Modifier.padding(start = 6.dp))
                    }
                    TextButton(
                        enabled = filtered.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                val uri =
                                    withContext(Dispatchers.IO) {
                                        writeFilteredRoomLogs(context, filtered)
                                    }
                                val shareIntent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "application/x-ndjson"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = ClipData.newRawUri("Unison diagnostics", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                context.startActivity(Intent.createChooser(shareIntent, "Share diagnostics"))
                            }
                        },
                    ) { Text("Share") }
                    TextButton(
                        enabled = events.isNotEmpty(),
                        onClick = {
                            onClear()
                            copied = false
                        },
                    ) { Text("Clear") }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it.take(120)
                        copied = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search logs") },
                    placeholder = { Text("event, component, message, attribute…") },
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RoomLogSeverityFilter.entries, key = { "severity:${it.name}" }) { filter ->
                        FilterChip(
                            selected = severityFilter == filter,
                            onClick = {
                                severityFilter = filter
                                copied = false
                            },
                            label = { Text(filter.label) },
                        )
                    }
                }

                if (availableCategories.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "category:all") {
                            FilterChip(
                                selected = categoryFilter == null,
                                onClick = {
                                    categoryFilter = null
                                    copied = false
                                },
                                label = { Text("All areas") },
                            )
                        }
                        items(availableCategories, key = { "category:${it.name}" }) { category ->
                            FilterChip(
                                selected = categoryFilter == category,
                                onClick = {
                                    categoryFilter = category
                                    copied = false
                                },
                                label = { Text(category.wireName.replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("No matching room logs", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Room diagnostics appear here live while this room is active.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filtered, key = DiagnosticEvent::sequence) { event ->
                            RoomLogRow(event)
                        }
                    }
                }
            }
        }
    }
}

private fun roomLogClipboardText(newestFirst: List<DiagnosticEvent>): String {
    val selected = ArrayDeque<String>()
    var chars = 0
    for (event in newestFirst) {
        val line = event.toJsonLine()
        if (chars + line.length + 1 > MAX_ROOM_LOG_CLIPBOARD_CHARS) break
        selected.addFirst(line)
        chars += line.length + 1
    }
    return selected.joinToString(separator = "\n", postfix = if (selected.isEmpty()) "" else "\n")
}

private const val MAX_ROOM_LOG_CLIPBOARD_CHARS = 400_000

private fun writeFilteredRoomLogs(
    context: Context,
    newestFirst: List<DiagnosticEvent>,
): android.net.Uri {
    val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
    val file = File(directory, "unison-diagnostics.ndjson")
    file.bufferedWriter(Charsets.UTF_8).use { writer ->
        newestFirst.asReversed().forEach { event ->
            writer.appendLine(event.toJsonLine())
        }
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", file)
}

@Composable
private fun RoomLogRow(event: DiagnosticEvent) {
    var expanded by rememberSaveable(event.sequence) { mutableStateOf(false) }
    val time = remember(event.timestamp) { event.timestamp.substringAfter('T').take(12) }
    val severityColor =
        when {
            event.severity.severityNumber >= 17 -> MaterialTheme.colorScheme.error
            event.severity.severityNumber >= 13 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    event.severity.severityText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = severityColor,
                )
                Text(
                    event.eventName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            event.body?.let { body ->
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${event.category.wireName} · ${event.component}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                event.attributes.entries
                    .asSequence()
                    .filterNot { (key, _) -> key == DiagnosticEvent.ROOM_SESSION_ID_ATTRIBUTE }
                    .sortedBy { it.key }
                    .forEach { (key, value) ->
                        Text(
                            "$key = ${value ?: "null"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                event.error?.let { error ->
                    Text(
                        buildString {
                            append(error.type)
                            error.message?.let { append(": ").append(it) }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
