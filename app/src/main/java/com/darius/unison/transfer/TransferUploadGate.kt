package com.darius.unison.transfer

import com.darius.unison.model.PeerId
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Bounds total upload work while guaranteeing that one destination cannot consume parallel slots.
 * Peer entries are reference counted and removed when the last waiter/holder leaves the gate.
 */
internal class TransferUploadGate(maxConcurrentUploads: Int) {
    private class PeerEntry {
        val permit = Semaphore(1)
        var users: Int = 0
    }

    private val globalPermit = Semaphore(maxConcurrentUploads)
    private val lock = Any()
    private val entries = mutableMapOf<PeerId, PeerEntry>()

    init {
        require(maxConcurrentUploads > 0) { "Upload concurrency must be positive" }
    }

    suspend fun <T> withPermit(peerId: PeerId, block: suspend () -> T): T {
        val entry =
            synchronized(lock) {
                entries.getOrPut(peerId, ::PeerEntry).also { it.users++ }
            }
        return try {
            entry.permit.withPermit { globalPermit.withPermit { block() } }
        } finally {
            synchronized(lock) {
                entry.users--
                check(entry.users >= 0) { "Upload gate reference count underflow" }
                if (entry.users == 0) entries.remove(peerId, entry)
            }
        }
    }

    internal val trackedPeerCount: Int
        get() = synchronized(lock) { entries.size }
}
