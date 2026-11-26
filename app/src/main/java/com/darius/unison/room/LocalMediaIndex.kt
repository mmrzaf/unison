package com.darius.unison.room

import com.darius.unison.model.TrackId
import java.io.File

/**
 * This phone's verified media files for the current room session. The room actor is its only reader
 * and writer, so player work receives resolved files instead of reading storage itself. A missing
 * key means "not probed yet"; a null value means "probed and unavailable".
 */
internal class LocalMediaIndex {
    private val files = mutableMapOf<TrackId, File?>()

    fun markAvailable(trackId: TrackId, file: File) {
        files[trackId] = file
    }

    fun markUnavailable(trackId: TrackId) {
        files[trackId] = null
    }

    fun isProbed(trackId: TrackId): Boolean = files.containsKey(trackId)

    fun file(trackId: TrackId): File? = files[trackId]

    fun isAvailable(trackId: TrackId): Boolean = files[trackId] != null

    fun availableTrackIds(): Set<TrackId> = files.filterValues { it != null }.keys.toSet()

    fun clear() = files.clear()
}
