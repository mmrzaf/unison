package com.darius.unison.sync

import com.darius.unison.model.QueueItemId
import com.darius.unison.playback.PlaybackActivityState
import com.darius.unison.playback.PlaybackSpeedCommandGate
import org.junit.Assert.assertTrue
import org.junit.Test

class SynchronizationSimulationTest {
    private val item = QueueItemId("simulation-track")

    @Test
    fun threeDevicesRemainBoundedForTwoHours() {
        val rates = listOf(0.9995, 1.00025, 1.001)
        rates.forEach { hardwareRate ->
            val controller = PlaybackSyncController()
            val player = FakeSynchronizedPlayer(item, hardwareRate)
            val result = PlaybackScenarioRunner(controller, player, item).run(2 * 60 * 60 * 1_000L)
            assertTrue(result.p95AbsoluteDriftMs < 100)
            assertTrue(result.maximumAbsoluteDriftMs < 200)
            assertTrue(result.hardSeekCount == 0)
            assertTrue(result.maximumSpeed <= 1.0051f)
            assertTrue(result.minimumSpeed >= 0.9949f)
        }
    }

    @Test
    fun audioSafeSpeedGatingKeepsLongPlaybackBoundedWithFarFewerRateChanges() {
        listOf(0.9995, 1.001).forEach { hardwareRate ->
            val controller = PlaybackSyncController()
            val player = FakeSynchronizedPlayer(item, hardwareRate)
            val result = PlaybackScenarioRunner(
                controller = controller,
                player = player,
                queueItemId = item,
                speedGate = PlaybackSpeedCommandGate(),
            ).run(2 * 60 * 60 * 1_000L)

            assertTrue(result.p95AbsoluteDriftMs < 120)
            assertTrue(result.maximumAbsoluteDriftMs < 250)
            assertTrue(result.hardSeekCount == 0)
            assertTrue(player.speedCommandCount < 300)
        }
    }

    @Test
    fun coordinatorAndGuestsIndependentlyFollowTheSameTimeline() {
        val devices = listOf(
            Triple(0.9995, true, "coordinator"),
            Triple(1.00025, false, "guest-a"),
            Triple(1.001, false, "guest-b"),
        )
        devices.forEach { (hardwareRate, coordinator, _) ->
            val controller = PlaybackSyncController()
            val player = FakeSynchronizedPlayer(item, hardwareRate)
            val result = PlaybackScenarioRunner(controller, player, item).run(
                durationMs = 2 * 60 * 60 * 1_000L,
                coordinatorUsesLocalClock = coordinator,
            )
            assertTrue(result.p95AbsoluteDriftMs < 100)
            assertTrue(result.maximumAbsoluteDriftMs < 200)
            assertTrue(result.hardSeekCount == 0)
        }
    }

    @Test
    fun noisyReferenceMeasurementsDoNotCreateOscillationOrSeekLoops() {
        val controller = PlaybackSyncController()
        val player = FakeSynchronizedPlayer(item, 1.0004)
        val noise = listOf(0L, 18L, -12L, 35L, -25L, 8L, 70L, -55L)
        val result = PlaybackScenarioRunner(controller, player, item).run(
            durationMs = 30 * 60 * 1_000L,
            expectedPositionNoiseMs = { tick -> noise[tick % noise.size] },
        )
        assertTrue(result.p95AbsoluteDriftMs < 200)
        assertTrue(result.hardSeekCount == 0)
        assertTrue(result.maximumSpeed <= 1.0051f)
        assertTrue(result.minimumSpeed >= 0.9949f)
    }

    @Test
    fun bufferingNeverAppliesCorrectionAndRecoversWithoutSeekLoop() {
        val controller = PlaybackSyncController()
        val player = FakeSynchronizedPlayer(item, 1.0005)
        val result = PlaybackScenarioRunner(controller, player, item).run(10 * 60 * 1_000L) { tick, fake ->
            fake.activityState = if (tick in 300..319) {
                PlaybackActivityState.BUFFERING
            } else {
                PlaybackActivityState.READY_PLAYING
            }
        }
        assertTrue(result.hardSeekCount <= 1)
        assertTrue(result.maximumSpeed <= 1.0051f)
    }
}
