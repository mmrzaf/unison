package com.darius.unison.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTransitionCircuitBreakerTest {
    @Test
    fun `ordinary transitions remain allowed`() {
        val guard =
            PlayerTransitionCircuitBreaker(
                maxTransitions = 3,
                windowNs = 1_000L,
                cooldownNs = 2_000L,
            )

        assertEquals(PlayerTransitionCircuitBreaker.Result.ALLOW, guard.record(0L))
        assertEquals(PlayerTransitionCircuitBreaker.Result.ALLOW, guard.record(2_000L))
        assertEquals(PlayerTransitionCircuitBreaker.Result.ALLOW, guard.record(4_000L))
    }

    @Test
    fun `burst trips once and blocks during cooldown`() {
        val guard =
            PlayerTransitionCircuitBreaker(
                maxTransitions = 3,
                windowNs = 1_000L,
                cooldownNs = 2_000L,
            )

        assertEquals(PlayerTransitionCircuitBreaker.Result.ALLOW, guard.record(0L))
        assertEquals(PlayerTransitionCircuitBreaker.Result.ALLOW, guard.record(100L))
        assertEquals(PlayerTransitionCircuitBreaker.Result.TRIPPED, guard.record(200L))
        assertEquals(PlayerTransitionCircuitBreaker.Result.BLOCKED, guard.record(300L))
        assertEquals(PlayerTransitionCircuitBreaker.Result.ALLOW, guard.record(2_200L))
    }

    @Test
    fun `reset clears cooldown and history`() {
        val guard =
            PlayerTransitionCircuitBreaker(
                maxTransitions = 2,
                windowNs = 1_000L,
                cooldownNs = 5_000L,
            )
        guard.record(0L)
        assertEquals(PlayerTransitionCircuitBreaker.Result.TRIPPED, guard.record(1L))

        guard.reset()

        assertEquals(PlayerTransitionCircuitBreaker.Result.ALLOW, guard.record(2L))
    }
}
