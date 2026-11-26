package com.darius.unison.util

/** Monotonic device time that keeps advancing through deep sleep. */
fun interface MonotonicClock {
    fun nowNs(): Long

    fun elapsedMs(): Long = nowNs() / 1_000_000L
}
