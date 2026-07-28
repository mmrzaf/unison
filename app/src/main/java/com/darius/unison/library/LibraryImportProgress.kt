package com.darius.unison.library

enum class LibraryImportStage {
    READING_PLAYLIST,
    INDEXING_FOLDER,
    RESOLVING_ENTRIES,
    IMPORTING_AUDIO,
    FINALIZING,
}

data class LibraryImportProgress(
    val stage: LibraryImportStage,
    val documentsScanned: Int = 0,
    val playlistEntriesProcessed: Int = 0,
    val totalPlaylistEntries: Int = 0,
    val tracksResolved: Int = 0,
    val unresolvedEntries: Int = 0,
    val ambiguousEntries: Int = 0,
    val currentDirectory: String? = null,
    val elapsedMs: Long = 0L,
) {
    val fraction: Float
        get() = if (totalPlaylistEntries <= 0) 0f
        else playlistEntriesProcessed.coerceIn(0, totalPlaylistEntries).toFloat() / totalPlaylistEntries
}
