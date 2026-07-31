package com.darius.unison.playback

import java.util.ArrayDeque

/**
 * Last-resort guard against a self-sustaining automatic item-transition loop.
 *
 * Manual navigation is reported by Media3 as SEEK and never enters this guard. The threshold is
 * deliberately high enough that ordinary playback and user interaction cannot trip it.
 */
class PlayerTransitionCircuitBreaker(
    private val maxTransitions: Int = 8,
    private val windowNs: Long = 2_000_000_000L,
    private val cooldownNs: Long = 5_000_000_000L,
) {
    private val observedAtNs = ArrayDeque<Long>()
    private var blockedUntilNs = 0L

    init {
        require(maxTransitions >= 2)
        require(windowNs > 0)
        require(cooldownNs > 0)
    }

    fun record(nowNs: Long): Result {
        if (nowNs < blockedUntilNs) return Result.BLOCKED
        while (observedAtNs.isNotEmpty() && nowNs - observedAtNs.first() > windowNs) {
            observedAtNs.removeFirst()
        }
        observedAtNs.addLast(nowNs)
        if (observedAtNs.size < maxTransitions) return Result.ALLOW

        observedAtNs.clear()
        blockedUntilNs = nowNs + cooldownNs
        return Result.TRIPPED
    }

    fun reset() {
        observedAtNs.clear()
        blockedUntilNs = 0L
    }

    enum class Result {
        ALLOW,
        TRIPPED,
        BLOCKED,
    }
}
