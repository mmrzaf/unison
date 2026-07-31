package com.darius.unison.sync

import com.darius.unison.util.MonotonicClock
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockSyncEngineTest {
    private class FakeClock(var value: Long = 0) : MonotonicClock {
        override fun nowNs() = value
    }

    @Test
    fun estimatesCoordinatorOffsetAndRate() {
        val clock = FakeClock(1_000_000_000L)
        val engine = ClockSyncEngine(clock)
        val trueRate = 1.0001
        val trueOffset = 40_000_000L

        repeat(30) { index ->
            val ping = engine.createPing()
            val outbound = 8_000_000L + (index % 3) * 500_000L
            val processing = 1_000_000L
            val inbound = 9_000_000L + (index % 2) * 400_000L
            val coordinatorReceive = map(ping.localSendNs + outbound, trueRate, trueOffset)
            val coordinatorSend = coordinatorReceive + processing
            clock.value = ping.localSendNs + outbound + processing + inbound
            engine.recordPong(
                ping.pingId,
                ping.localSendNs,
                coordinatorReceive,
                coordinatorSend,
                clock.value,
            )
            clock.value += 1_000_000_000L
        }

        val estimate = engine.estimate()
        assertEquals(ClockSyncState.LOCKED, estimate.state)
        assertTrue(abs(estimate.rate - trueRate) < 0.00004)
        val expectedNow = map(clock.value, trueRate, trueOffset)
        assertTrue(abs(engine.toCoordinatorTime(clock.value) - expectedNow) < 8_000_000L)
        assertTrue(estimate.uncertaintyNs < 30_000_000L)
    }

    @Test
    fun becomesStaleWithoutFreshSamples() {
        val clock = FakeClock(1_000_000_000L)
        val engine = ClockSyncEngine(clock)
        repeat(6) {
            val ping = engine.createPing()
            val coordinatorReceive = ping.localSendNs + 50_000_000L
            val coordinatorSend = coordinatorReceive + 1_000_000L
            clock.value = ping.localSendNs + 21_000_000L
            engine.recordPong(
                ping.pingId,
                ping.localSendNs,
                coordinatorReceive,
                coordinatorSend,
                clock.value,
            )
            clock.value += 1_000_000_000L
        }
        assertEquals(ClockSyncState.LOCKED, engine.state)
        clock.value += 13_000_000_000L
        assertEquals(ClockSyncState.STALE, engine.state)
    }

    @Test
    fun staleGapForcesFreshAcquisitionWindow() {
        val clock = FakeClock(1_000_000_000L)
        val engine = ClockSyncEngine(clock)
        repeat(6) {
            val ping = engine.createPing()
            val coordinatorReceive = ping.localSendNs + 40_000_000L
            val coordinatorSend = coordinatorReceive + 1_000_000L
            clock.value = ping.localSendNs + 21_000_000L
            engine.recordPong(
                ping.pingId,
                ping.localSendNs,
                coordinatorReceive,
                coordinatorSend,
                clock.value,
            )
            clock.value += 1_000_000_000L
        }
        assertEquals(ClockSyncState.LOCKED, engine.state)
        clock.value += 13_000_000_000L
        val ping = engine.createPing()
        val coordinatorReceive = ping.localSendNs + 40_000_000L
        val coordinatorSend = coordinatorReceive + 1_000_000L
        clock.value = ping.localSendNs + 21_000_000L
        engine.recordPong(
            ping.pingId,
            ping.localSendNs,
            coordinatorReceive,
            coordinatorSend,
            clock.value,
        )
        assertEquals(ClockSyncState.ACQUIRING, engine.state)
        assertEquals(1, engine.estimate().acceptedSampleCount)
    }

    @Test
    fun lowRttModelRejectsLargeAsymmetricOffsetOutlier() {
        val clock = FakeClock(1_000_000_000L)
        val engine = ClockSyncEngine(clock)
        repeat(8) {
            val ping = engine.createPing()
            val coordinatorReceive = ping.localSendNs + 50_000_000L
            val coordinatorSend = coordinatorReceive + 1_000_000L
            clock.value = ping.localSendNs + 21_000_000L
            engine.recordPong(
                ping.pingId,
                ping.localSendNs,
                coordinatorReceive,
                coordinatorSend,
                clock.value,
            )
            clock.value += 1_000_000_000L
        }
        val acceptedBefore = engine.estimate().acceptedSampleCount
        val outlier = engine.createPing()
        clock.value = outlier.localSendNs + 100_000_000L
        val result =
            engine.recordPong(
                outlier.pingId,
                outlier.localSendNs,
                outlier.localSendNs + 220_000_000L,
                outlier.localSendNs + 221_000_000L,
                clock.value,
            )
        assertNull(result)
        assertEquals(acceptedBefore, engine.estimate().acceptedSampleCount)
    }

    @Test
    fun rejectsUnknownAndExcessiveRttSamples() {
        val clock = FakeClock(0)
        val engine = ClockSyncEngine(clock)
        assertNull(engine.recordPong("missing", 0, 0, 0, 1))

        val ping = engine.createPing()
        clock.value = ping.localSendNs + 300_000_000L
        assertNull(
            engine.recordPong(
                ping.pingId,
                ping.localSendNs,
                ping.localSendNs + 100_000_000L,
                ping.localSendNs + 101_000_000L,
                clock.value,
            )
        )
        assertEquals(2, engine.estimate().rejectedSampleCount)
    }

    @Test
    fun remainsAccurateThroughDeterministicJitterAndAsymmetry() {
        val clock =
            AffineGuestClock(
                coordinatorNowNs = 2_000_000_000L,
                localRate = 0.99975,
                localOffsetNs = -65_000_000L,
            )
        val scenario = ClockNetworkScenario(clock)
        scenario.exchangeSeries(count = 90) { index ->
            when {
                index % 19 == 0 -> 90_000_000L to 8_000_000L
                index % 7 == 0 -> 18_000_000L to 6_000_000L
                else ->
                    (4_000_000L + (index % 5) * 700_000L) to (5_000_000L + (index % 4) * 600_000L)
            }
        }

        val estimate = scenario.engine.estimate()
        assertEquals(ClockSyncState.LOCKED, estimate.state)
        assertTrue(scenario.mappingErrorNs() < 20_000_000L)
        assertTrue(abs(estimate.rate - (1.0 / 0.99975)) < 0.00008)
        assertTrue(estimate.uncertaintyNs < 35_000_000L)
    }

    @Test
    fun sleepWakeAndResetRequireClockReacquisition() {
        val clock =
            AffineGuestClock(
                coordinatorNowNs = 1_000_000_000L,
                localRate = 1.0003,
                localOffsetNs = 25_000_000L,
            )
        val scenario = ClockNetworkScenario(clock)
        scenario.exchangeSeries(count = 8) { 6_000_000L to 7_000_000L }
        assertEquals(ClockSyncState.LOCKED, scenario.engine.state)

        clock.advanceCoordinatorNs(13_000_000_000L)
        assertEquals(ClockSyncState.STALE, scenario.engine.state)

        scenario.exchange(6_000_000L, 7_000_000L)
        assertEquals(ClockSyncState.ACQUIRING, scenario.engine.state)
        scenario.exchangeSeries(count = 7) { 6_000_000L to 7_000_000L }
        assertEquals(ClockSyncState.LOCKED, scenario.engine.state)
        assertTrue(scenario.mappingErrorNs() < 15_000_000L)

        scenario.engine.reset()
        assertEquals(ClockSyncState.UNSYNCHRONIZED, scenario.engine.state)
        scenario.exchangeSeries(count = 5) { 5_000_000L to 5_000_000L }
        assertEquals(ClockSyncState.LOCKED, scenario.engine.state)
    }

    @Test
    fun futureCoordinatorDeadlineUsesImprovingMapping() {
        val clock =
            AffineGuestClock(
                coordinatorNowNs = 5_000_000_000L,
                localRate = 1.001,
                localOffsetNs = 80_000_000L,
            )
        val scenario = ClockNetworkScenario(clock)
        scenario.exchangeSeries(count = 5) { index ->
            (12_000_000L + index * 1_000_000L) to 5_000_000L
        }
        val deadlineCoordinatorNs = clock.coordinatorNowNs + 60_000_000_000L
        val earlyLocalTarget = scenario.engine.toLocalTime(deadlineCoordinatorNs)

        scenario.exchangeSeries(count = 40) { index ->
            (4_000_000L + (index % 3) * 400_000L) to (5_000_000L + (index % 2) * 300_000L)
        }
        val improvedLocalTarget = scenario.engine.toLocalTime(deadlineCoordinatorNs)
        val trueLocalTarget = (deadlineCoordinatorNs.toDouble() * 1.001).toLong() + 80_000_000L

        assertTrue(
            abs(improvedLocalTarget - trueLocalTarget) < abs(earlyLocalTarget - trueLocalTarget)
        )
        assertTrue(abs(improvedLocalTarget - trueLocalTarget) < 15_000_000L)
    }

    private fun map(localNs: Long, rate: Double, offsetNs: Long): Long =
        (localNs.toDouble() * rate).toLong() + offsetNs
}
