package com.darius.unison.transfer

import com.darius.unison.model.PeerId
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Bounds total upload work while guaranteeing that one destination cannot consume parallel slots.
 * Peer entries are reference counted and removed when the last waiter/holder leaves the gate.
 */
internal class TransferUploadGate(
    maxConcurrentUploads: Int,
    private val maxConcurrentPerDestination: Int = 1,
) {
    private inner class PeerEntry {
        val permit = Semaphore(maxConcurrentPerDestination)
        var users: Int = 0
    }

    private val globalPermit = Semaphore(maxConcurrentUploads)
    private val lock = Any()
    private val entries = mutableMapOf<PeerId, PeerEntry>()

    init {
        require(maxConcurrentUploads > 0) { "Upload concurrency must be positive" }
        require(maxConcurrentPerDestination > 0) { "Per-destination upload concurrency must be positive" }
        require(maxConcurrentPerDestination <= maxConcurrentUploads) {
            "Per-destination upload concurrency cannot exceed total upload capacity"
        }
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


    /**
     * Defensive non-blocking admission. Normal coordinator scheduling should already have reserved
     * capacity, so an unexpected duplicate/stale socket is rejected rather than sitting in a
     * handshake queue until the destination times out.
     */
    suspend fun tryWithPermit(peerId: PeerId, block: suspend () -> Unit): Boolean {
        val entry =
            synchronized(lock) {
                entries.getOrPut(peerId, ::PeerEntry).also { it.users++ }
            }
        var peerAcquired = false
        var globalAcquired = false
        return try {
            peerAcquired = entry.permit.tryAcquire()
            if (!peerAcquired) return false
            globalAcquired = globalPermit.tryAcquire()
            if (!globalAcquired) return false
            block()
            true
        } finally {
            if (globalAcquired) globalPermit.release()
            if (peerAcquired) entry.permit.release()
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
