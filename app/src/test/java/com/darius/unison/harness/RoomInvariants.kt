package com.darius.unison.harness

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertTrue

/** Invariants every simulated device must satisfy, independent of the scenario. */
object RoomInvariants {
    fun failedEvents(device: SimDevice) =
        device.log.snapshot().filter { it.eventName == "room.event.failed" }

    fun assertNoFailedEvents(vararg devices: SimDevice) {
        devices.forEach { device ->
            val failed = failedEvents(device)
            assertTrue(
                "device ${device.index} failed to handle room events: " +
                    failed.take(3).joinToString { it.error?.message ?: it.eventName },
                failed.isEmpty(),
            )
        }
    }
}

/** Advances virtual time in small steps so every dispatcher turn gets a chance to run. */
fun TestScope.settle(millis: Long = 2_000) {
    val step = 50L
    var elapsed = 0L
    while (elapsed < millis) {
        advanceTimeBy(step)
        runCurrent()
        elapsed += step
    }
}
