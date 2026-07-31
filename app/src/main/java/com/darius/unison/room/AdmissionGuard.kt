package com.darius.unison.room

import java.util.ArrayDeque
import java.util.LinkedHashMap

/**
 * Bounded, thread-safe admission bookkeeping.
 *
 * The guard runs before expensive authentication work. It limits replay-state memory, per-address
 * failures, and aggregate failed authentication load without retaining unbounded
 * attacker-controlled addresses or nonces.
 */
internal class AdmissionGuard(
    private val maxTrackedAttempts: Int = 256,
    private val maxTrackedNonces: Int = 1_024,
    private val maxGlobalFailures: Int = 30,
    private val globalFailureWindowMs: Long = 60_000L,
    private val maxFailuresPerAddress: Int = 5,
    private val addressBackoffMs: Long = 30_000L,
    private val nonceTtlMs: Long = 120_000L,
) {
    private data class AttemptState(
        val failures: Int,
        val blockedUntilElapsedMs: Long,
        val lastUpdatedElapsedMs: Long,
    )

    private val lock = Any()
    private val attempts = LinkedHashMap<String, AttemptState>(16, 0.75f, true)
    private val usedNonces = LinkedHashMap<String, Long>()
    private val globalFailures = ArrayDeque<Long>()

    fun checkAndReserve(
        remoteAddress: String,
        nonceKey: String,
        nowElapsedMs: Long,
    ): String? =
        synchronized(lock) {
            prune(nowElapsedMs)

            val attempt = attempts[remoteAddress]
            if (attempt != null && attempt.blockedUntilElapsedMs > nowElapsedMs) {
                return@synchronized TOO_MANY_ATTEMPTS
            }
            if (globalFailures.size >= maxGlobalFailures) {
                return@synchronized SERVER_BUSY
            }
            if (usedNonces.containsKey(nonceKey)) {
                return@synchronized NONCE_REUSED
            }
            if (usedNonces.size >= maxTrackedNonces) {
                return@synchronized SERVER_BUSY
            }

            usedNonces[nonceKey] = nowElapsedMs + nonceTtlMs
            null
        }

    fun recordFailure(remoteAddress: String, nowElapsedMs: Long) =
        synchronized(lock) {
            prune(nowElapsedMs)
            globalFailures.addLast(nowElapsedMs)
            while (globalFailures.size > maxGlobalFailures) {
                globalFailures.removeFirst()
            }

            val previous = attempts[remoteAddress]
            val failures =
                if (previous == null || previous.blockedUntilElapsedMs <= nowElapsedMs) {
                    (previous?.failures ?: 0) + 1
                } else {
                    previous.failures
                }
            val blockedUntil =
                if (failures >= maxFailuresPerAddress) {
                    nowElapsedMs + addressBackoffMs
                } else {
                    0L
                }
            attempts[remoteAddress] =
                AttemptState(
                    failures = if (blockedUntil > 0L) 0 else failures,
                    blockedUntilElapsedMs = blockedUntil,
                    lastUpdatedElapsedMs = nowElapsedMs,
                )
            trimAttempts()
        }

    fun recordSuccess(remoteAddress: String) {
        synchronized(lock) {
            attempts.remove(remoteAddress)
        }
    }

    fun reset() =
        synchronized(lock) {
            attempts.clear()
            usedNonces.clear()
            globalFailures.clear()
        }

    internal fun trackedAttemptCount(): Int = synchronized(lock) { attempts.size }

    internal fun trackedNonceCount(): Int = synchronized(lock) { usedNonces.size }

    internal fun trackedGlobalFailureCount(): Int = synchronized(lock) { globalFailures.size }

    private fun prune(nowElapsedMs: Long) {
        usedNonces.entries.removeIf { (_, expiresAt) -> expiresAt <= nowElapsedMs }
        attempts.entries.removeIf { (_, state) ->
            state.blockedUntilElapsedMs <= nowElapsedMs &&
                nowElapsedMs - state.lastUpdatedElapsedMs >= ATTEMPT_RETENTION_MS
        }
        val failureCutoff = nowElapsedMs - globalFailureWindowMs
        while (globalFailures.isNotEmpty() && globalFailures.first() <= failureCutoff) {
            globalFailures.removeFirst()
        }
    }

    private fun trimAttempts() {
        while (attempts.size > maxTrackedAttempts) {
            val eldest = attempts.entries.iterator()
            if (!eldest.hasNext()) break
            eldest.next()
            eldest.remove()
        }
    }

    private companion object {
        const val ATTEMPT_RETENTION_MS = 5L * 60L * 1000L
        const val NONCE_REUSED = "Connection request was already used"
        const val TOO_MANY_ATTEMPTS = "Too many authentication attempts; try again shortly"
        const val SERVER_BUSY = "Authentication is temporarily busy; try again shortly"
    }
}
