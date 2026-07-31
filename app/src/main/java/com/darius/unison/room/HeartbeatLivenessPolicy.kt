package com.darius.unison.room

/** Prevents a delayed scheduler tick after device sleep from immediately evicting every peer. */
internal class HeartbeatLivenessPolicy(
    private val expectedIntervalMs: Long,
    private val timeoutMs: Long,
    private val discontinuityMs: Long = expectedIntervalMs * 3,
) {
    init {
        require(expectedIntervalMs > 0)
        require(timeoutMs > expectedIntervalMs)
        require(discontinuityMs > expectedIntervalMs)
    }

    private var previousTickMs: Long? = null
    private var graceUntilMs: Long = 0

    /** Returns true when normal peer timeout enforcement is safe on this tick. */
    fun onTick(nowMs: Long): Boolean {
        require(nowMs >= 0)
        val previous = previousTickMs
        previousTickMs = nowMs
        if (previous != null && nowMs - previous > discontinuityMs) {
            graceUntilMs = maxOf(graceUntilMs, nowMs + timeoutMs)
            return false
        }
        return nowMs >= graceUntilMs
    }

    fun reset() {
        previousTickMs = null
        graceUntilMs = 0
    }
}
