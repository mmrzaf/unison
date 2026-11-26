package com.darius.unison.room.port

import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.storage.ManagedFileLeaseReason
import com.darius.unison.storage.ManagedFileStore
import java.io.File
import kotlinx.coroutines.flow.Flow

/** Durable local identity and the retention default for received tracks. */
interface IdentityStore {
    val retentionPolicy: Flow<RetentionPolicy>

    suspend fun ensureIdentity(): LocalIdentity

    suspend fun rotateIdentity(): LocalIdentity

    suspend fun saveDisplayName(name: String)
}

/** The verified local library as the room sees it. */
interface TrackStore {
    suspend fun getMany(trackIds: List<TrackId>): List<TrackDescriptor>

    suspend fun requireReadableFile(trackId: TrackId): File?

    suspend fun requireReadableFileWithLease(
        trackId: TrackId,
        reason: ManagedFileLeaseReason,
    ): ManagedFileStore.LeasedManagedFile?

    suspend fun registerManagedFile(descriptor: TrackDescriptor, retentionPolicy: RetentionPolicy)

    suspend fun hasVerifiedSource(trackId: TrackId): Boolean

    suspend fun touchTemporary(trackId: TrackId)

    suspend fun markPlayed(trackId: TrackId)
}
