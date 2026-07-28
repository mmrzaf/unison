package com.darius.unison.library

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unison copies imported audio into app-owned storage, so SAF grants are operation-scoped. This
 * manager reference-counts concurrent readers and releases the persisted grant after the final
 * reader completes. It also removes grants leaked by older builds at startup.
 */
class PersistedUriPermissionManager(
    private val resolver: ContentResolver,
    private val ledger: UriPermissionLedger = UriPermissionLedger(),
) {
    suspend fun <T> withTemporaryReadPermission(uri: Uri, block: suspend () -> T): T {
        val key = uri.toString()
        val firstOwner = ledger.acquire(key)
        if (firstOwner) {
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            if (ledger.release(key)) releaseReadPermission(uri)
        }
    }

    suspend fun releaseAllUnused() = withContext(Dispatchers.IO) {
        resolver.persistedUriPermissions
            .asSequence()
            .filter { it.isReadPermission && !ledger.isActive(it.uri.toString()) }
            .map { it.uri }
            .toList()
            .forEach(::releaseReadPermission)
    }

    private fun releaseReadPermission(uri: Uri) {
        runCatching {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
