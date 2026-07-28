package com.darius.unison.library

import java.util.Locale
import kotlin.math.abs

/** Metadata-only fallback matching. URI and tree-path matches must be attempted before this. */
object PlaylistMatchPolicy {
    data class Candidate(
        val trackId: String,
        val fileName: String?,
        val title: String?,
        val durationMs: Long,
        val sizeBytes: Long,
    )

    sealed interface Decision {
        data class Unique(val candidate: Candidate) : Decision
        data class Ambiguous(val candidates: List<Candidate>) : Decision
        data object Missing : Decision
    }

    fun decide(
        fileName: String?,
        displayTitle: String?,
        durationSeconds: Long?,
        candidates: List<Candidate>,
    ): Decision {
        if (candidates.isEmpty()) return Decision.Missing
        val normalizedFileName = fileName.normalized()
        val normalizedTitle = displayTitle.normalized()

        val fileMatches = candidates.filter { it.fileName.normalized() == normalizedFileName }
        val initial = when {
            normalizedFileName != null && fileMatches.isNotEmpty() -> fileMatches
            normalizedTitle != null -> candidates.filter { it.title.normalized() == normalizedTitle }
            else -> emptyList()
        }
        if (initial.isEmpty()) return Decision.Missing
        if (initial.size == 1) return Decision.Unique(initial.single())

        var narrowed = initial
        if (normalizedTitle != null) {
            val titleMatches = narrowed.filter { it.title.normalized() == normalizedTitle }
            if (titleMatches.isNotEmpty()) narrowed = titleMatches
        }
        if (durationSeconds != null) {
            val expectedMs = durationSeconds * 1_000L
            val durationMatches = narrowed.filter { candidate ->
                candidate.durationMs > 0L && abs(candidate.durationMs - expectedMs) <= DURATION_TOLERANCE_MS
            }
            if (durationMatches.isNotEmpty()) narrowed = durationMatches
        }
        val distinct = narrowed.distinctBy(Candidate::trackId)
        return when (distinct.size) {
            0 -> Decision.Missing
            1 -> Decision.Unique(distinct.single())
            else -> Decision.Ambiguous(distinct.sortedWith(compareBy({ it.title.orEmpty() }, { it.fileName.orEmpty() }, { it.trackId })))
        }
    }

    private fun String?.normalized(): String? = this
        ?.filterNot { it.isISOControl() }
        ?.trim()
        ?.lowercase(Locale.US)
        ?.ifBlank { null }

    private const val DURATION_TOLERANCE_MS = 2_000L
}
