package com.darius.unison.room

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatLivenessPolicyTest {
    @Test
    fun regularTicksAllowTimeoutChecks() {
        val policy = HeartbeatLivenessPolicy(5_000, 30_000)
        assertTrue(policy.onTick(1_000))
        assertTrue(policy.onTick(6_000))
        assertTrue(policy.onTick(11_000))
    }

    @Test
    fun longSchedulerPauseCreatesOneTimeoutWindowOfGrace() {
        val policy = HeartbeatLivenessPolicy(5_000, 30_000)
        assertTrue(policy.onTick(1_000))
        assertFalse(policy.onTick(25_000))
        assertFalse(policy.onTick(30_000))
        assertFalse(policy.onTick(35_000))
        assertFalse(policy.onTick(40_000))
        assertFalse(policy.onTick(45_000))
        assertFalse(policy.onTick(50_000))
        assertTrue(policy.onTick(55_000))
    }

    @Test
    fun resetDropsOldDiscontinuityState() {
        val policy = HeartbeatLivenessPolicy(5_000, 30_000)
        policy.onTick(1_000)
        policy.onTick(25_000)
        policy.reset()
        assertTrue(policy.onTick(26_000))
    }
}
