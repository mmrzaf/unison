package com.darius.unison.library

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Pure path policy used by SAF tree resolution and regression tests. */
object PlaylistPathPolicy {
    sealed interface Decision {
        data class Valid(
            val relativePath: String,
            val normalizedPath: String,
            val fileName: String,
        ) : Decision

        data class Rejected(val reason: String) : Decision
    }

    fun evaluate(rawReference: String): Decision {
        val raw = rawReference.trim()
        if (raw.isEmpty()) return Decision.Rejected("empty")
        if ('\u0000' in raw) return Decision.Rejected("nul")
        if (URI_SCHEME.matches(raw)) return Decision.Rejected("uri")
        if (raw.startsWith('/') || raw.startsWith('\\') || WINDOWS_ABSOLUTE.matches(raw)) {
            return Decision.Rejected("absolute")
        }

        val decoded = decodePercentRepeatedly(raw) ?: return Decision.Rejected("invalid-encoding")
        if ('\u0000' in decoded) return Decision.Rejected("nul")
        if (
            decoded.startsWith('/') || decoded.startsWith('\\') || WINDOWS_ABSOLUTE.matches(decoded)
        ) {
            return Decision.Rejected("absolute")
        }

        val normalizedSegments = mutableListOf<String>()
        decoded.replace('\\', '/').split('/').forEach { segment ->
            val clean = segment.trim()
            when {
                clean.isEmpty() || clean == "." -> Unit
                clean == ".." -> return Decision.Rejected("traversal")
                ':' in clean && normalizedSegments.isEmpty() -> return Decision.Rejected("drive")
                else -> normalizedSegments += clean.lowercase(Locale.ROOT)
            }
        }
        if (normalizedSegments.isEmpty()) return Decision.Rejected("empty")
        val relativePath =
            decoded
                .replace('\\', '/')
                .split('/')
                .map(String::trim)
                .filter { it.isNotEmpty() && it != "." }
                .joinToString("/")
        return Decision.Valid(
            relativePath = relativePath,
            normalizedPath = normalizedSegments.joinToString("/"),
            fileName = normalizedSegments.last(),
        )
    }

    private fun decodePercentRepeatedly(value: String): String? {
        var current = value
        repeat(MAX_DECODE_PASSES) {
            if ('%' !in current) return current
            current =
                runCatching {
                        URLDecoder.decode(
                            current.replace("+", "%2B"),
                            StandardCharsets.UTF_8.name(),
                        )
                    }
                    .getOrNull() ?: return null
        }
        return current
    }

    private const val MAX_DECODE_PASSES = 2
    private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:.*")
    private val WINDOWS_ABSOLUTE = Regex("^[A-Za-z]:[\\\\/].*")
}

/** Selects a tree-path match only when it identifies one distinct document. */
object PlaylistTreeMatchPolicy {
    sealed interface Decision<out T> {
        data class Found<T>(val value: T) : Decision<T>

        data class Ambiguous(val count: Int) : Decision<Nothing>

        data object Missing : Decision<Nothing>
    }

    fun <T, K> decide(candidates: List<T>, identity: (T) -> K): Decision<T> {
        val distinct = candidates.distinctBy(identity)
        return when (distinct.size) {
            0 -> Decision.Missing
            1 -> Decision.Found(distinct.single())
            else -> Decision.Ambiguous(distinct.size)
        }
    }
}
