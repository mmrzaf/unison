package com.darius.unison.library

import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.storage.ManagedFileLeaseReason
import com.darius.unison.storage.ManagedFileStore
import java.io.File

class TrackRepository {
    suspend fun requireReadableFile(trackId: TrackId): File? = null
    suspend fun requireReadableFileWithLease(
        trackId: TrackId,
        reason: ManagedFileLeaseReason,
    ): ManagedFileStore.LeasedManagedFile? = null

    suspend fun registerManagedFile(track: TrackDescriptor, retentionPolicy: RetentionPolicy) = Unit
}
