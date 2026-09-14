package com.darius.unison.library

import kotlinx.coroutines.sync.Mutex

/**
 * Computes the hard byte limit for one managed import while preserving Unison's free-space reserve.
 */
internal object ImportStorageBudgetPolicy {
    fun copyLimitBytes(
        availableBytes: Long,
        declaredSizeBytes: Long?,
        maximumTrackBytes: Long,
        reserveBytes: Long,
    ): Long {
        require(maximumTrackBytes > 0) { "Maximum track size must be positive" }
        require(reserveBytes >= 0) { "Storage reserve must not be negative" }
        declaredSizeBytes?.let { declared ->
            require(declared in 1..maximumTrackBytes) {
                "Audio files must be between 1 byte and 1 GiB"
            }
        }
        val writable = (availableBytes - reserveBytes).coerceAtLeast(0L)
        val copyLimit = minOf(maximumTrackBytes, writable)
        require(copyLimit > 0) { "Not enough storage space" }
        declaredSizeBytes?.let { declared ->
            require(declared <= copyLimit) { "Not enough storage space" }
        }
        return copyLimit
    }
}

/**
 * Serializes app-managed imports so concurrent picker/M3U operations cannot spend the same
 * free-space snapshot. Capacity is recalculated only after the previous managed copy has completed
 * or failed.
 */
internal class ImportStorageGate(
    private val maximumTrackBytes: Long,
    private val reserveBytes: Long,
    private val availableBytes: () -> Long,
) {
    private val mutex = Mutex()

    suspend fun <T> withBudget(declaredSizeBytes: Long?, copy: suspend (Long) -> T): T {
        mutex.lock()
        try {
            val copyLimit =
                ImportStorageBudgetPolicy.copyLimitBytes(
                    availableBytes = availableBytes(),
                    declaredSizeBytes = declaredSizeBytes,
                    maximumTrackBytes = maximumTrackBytes,
                    reserveBytes = reserveBytes,
                )
            return copy(copyLimit)
        } finally {
            mutex.unlock()
        }
    }
}
