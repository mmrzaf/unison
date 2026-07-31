package com.darius.unison.library

import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import java.io.File

class TrackRepository {
    suspend fun requireReadableFile(trackId: TrackId): File? = null
    suspend fun registerManagedFile(track: TrackDescriptor, retentionPolicy: RetentionPolicy) = Unit
}
