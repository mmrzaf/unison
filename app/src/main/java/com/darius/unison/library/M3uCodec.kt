package com.darius.unison.library

import java.io.BufferedReader
import java.io.Reader

data class M3uEntry(
    val reference: String,
    val durationSeconds: Long? = null,
    val displayTitle: String? = null,
)

data class M3uPlaylist(val entries: List<M3uEntry>)

object M3uCodec {
    const val MAX_ENTRIES = 10_000
    const val MAX_LINE_LENGTH = 8_192
    const val MAX_FILE_BYTES = 4 * 1024 * 1024

    fun parse(reader: Reader): M3uPlaylist {
        val entries = mutableListOf<M3uEntry>()
        var pendingDuration: Long? = null
        var pendingTitle: String? = null
        BufferedReader(reader).useLines { lines ->
            lines.forEachIndexed { index, raw ->
                require(raw.length <= MAX_LINE_LENGTH) { "M3U line is too long" }
                val line = if (index == 0) raw.removePrefix("\uFEFF").trim() else raw.trim()
                when {
                    line.isBlank() -> Unit
                    line.startsWith("#EXTINF:", ignoreCase = true) -> {
                        val value = line.substringAfter(':')
                        val duration =
                            value.substringBefore(',').trim().toLongOrNull()?.takeIf { it >= 0 }
                        val title = value.substringAfter(',', "").trim().ifBlank { null }
                        pendingDuration = duration
                        pendingTitle = title
                    }

                    line.startsWith('#') -> Unit
                    else -> {
                        require(entries.size < MAX_ENTRIES) { "M3U playlist has too many entries" }
                        entries += M3uEntry(line, pendingDuration, pendingTitle)
                        pendingDuration = null
                        pendingTitle = null
                    }
                }
            }
        }
        return M3uPlaylist(entries)
    }

    fun encode(entries: List<M3uEntry>): String = buildString {
        appendLine("#EXTM3U")
        entries.forEach { entry ->
            if (entry.durationSeconds != null || entry.displayTitle != null) {
                append("#EXTINF:")
                append(entry.durationSeconds ?: -1)
                append(',')
                appendLine(entry.displayTitle.orEmpty())
            }
            appendLine(entry.reference)
        }
    }
}
