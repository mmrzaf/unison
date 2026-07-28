package com.darius.unison.storage

object ArtworkRetryPolicy {
    fun delayMs(failureCount: Int): Long {
        val exponent = (failureCount.coerceAtLeast(1) - 1).coerceAtMost(MAX_EXPONENT)
        return (BASE_DELAY_MS shl exponent).coerceAtMost(MAX_DELAY_MS)
    }

    const val BASE_DELAY_MS = 30_000L
    const val MAX_DELAY_MS = 6L * 60L * 60L * 1_000L
    private const val MAX_EXPONENT = 10
}
