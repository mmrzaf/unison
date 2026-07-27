package com.darius.unison.sync

import com.darius.unison.util.MonotonicClock
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockSyncEngineTest {
    private class FakeClock(var value: Long = 0) : MonotonicClock {
        override fun nowNs() = value
    }

    @Test
    fun estimatesCoordinatorOffset() {
        val clock = FakeClock(1_000_000_000)
        val engine = ClockSyncEngine(clock)
        repeat(5) {
            val ping = engine.createPing()
            val coordinatorReceive = ping.localSendNs + 50_000_000
            val coordinatorSend = coordinatorReceive + 1_000_000
            clock.value = ping.localSendNs + 21_000_000
            engine.recordPong(ping.pingId, ping.localSendNs, coordinatorReceive, coordinatorSend, clock.value)
            clock.value += 100_000_000
        }
        assertTrue(engine.synchronized)
        assertTrue(kotlin.math.abs(engine.offsetNs - 40_000_000L) <= 2_000_000L)
    }
}
