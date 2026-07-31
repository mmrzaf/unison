package com.darius.unison.library

import java.util.Locale

private val SEARCH_WHITESPACE = Regex("\\s+")

/** Normalizes both indexed metadata and user queries once, outside SQLite's hot scan loop. */
internal fun normalizeSearchText(value: String): String =
    value.trim().lowercase(Locale.ROOT).replace(SEARCH_WHITESPACE, " ")

/** Escapes SQLite LIKE metacharacters so user text is matched literally. */
internal fun normalizeSearchQuery(value: String): String =
    normalizeSearchText(value).replace("!", "!!").replace("%", "!%").replace("_", "!_")
