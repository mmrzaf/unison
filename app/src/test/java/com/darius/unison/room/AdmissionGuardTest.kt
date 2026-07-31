package com.darius.unison.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdmissionGuardTest {
    @Test
    fun nonceReplayIsRejectedUntilExpiry() {
        val guard = AdmissionGuard(nonceTtlMs = 100)
        assertNull(guard.checkAndReserve("10.0.0.2", "nonce", 1_000))
        assertEquals(
            "Connection request was already used",
            guard.checkAndReserve("10.0.0.2", "nonce", 1_050),
        )
        assertNull(guard.checkAndReserve("10.0.0.2", "nonce", 1_101))
    }

    @Test
    fun nonceStateIsHardCapped() {
        val guard = AdmissionGuard(maxTrackedNonces = 2)
        assertNull(guard.checkAndReserve("a", "n1", 0))
        assertNull(guard.checkAndReserve("b", "n2", 0))
        assertNotNull(guard.checkAndReserve("c", "n3", 0))
        assertEquals(2, guard.trackedNonceCount())
    }

    @Test
    fun addressStateIsHardCapped() {
        val guard = AdmissionGuard(maxTrackedAttempts = 2, maxGlobalFailures = 100)
        repeat(8) { index -> guard.recordFailure("address-$index", index.toLong()) }
        assertTrue(guard.trackedAttemptCount() <= 2)
    }

    @Test
    fun perAddressBackoffRunsBeforeAuthentication() {
        val guard = AdmissionGuard(maxFailuresPerAddress = 2, maxGlobalFailures = 100)
        guard.recordFailure("10.0.0.2", 0)
        guard.recordFailure("10.0.0.2", 1)
        assertEquals(
            "Too many authentication attempts; try again shortly",
            guard.checkAndReserve("10.0.0.2", "new", 2),
        )
    }

    @Test
    fun globalFailuresBoundAggregateAuthenticationWork() {
        val guard = AdmissionGuard(maxGlobalFailures = 2, maxFailuresPerAddress = 10)
        guard.recordFailure("a", 0)
        guard.recordFailure("b", 1)
        assertEquals(
            "Authentication is temporarily busy; try again shortly",
            guard.checkAndReserve("c", "nonce", 2),
        )
    }

    @Test
    fun resetClearsAllAdmissionState() {
        val guard = AdmissionGuard(maxFailuresPerAddress = 1)
        assertNull(guard.checkAndReserve("a", "nonce", 0))
        guard.recordFailure("a", 0)
        guard.reset()
        assertEquals(0, guard.trackedAttemptCount())
        assertEquals(0, guard.trackedNonceCount())
        assertNull(guard.checkAndReserve("a", "nonce", 1))
    }

    @Test
    fun globalFailureWindowIsHardCapped() {
        val guard = AdmissionGuard(maxGlobalFailures = 2, maxFailuresPerAddress = 100)
        repeat(10) { index -> guard.recordFailure("address-$index", index.toLong()) }
        assertTrue(guard.trackedGlobalFailureCount() <= 2)
    }
}
