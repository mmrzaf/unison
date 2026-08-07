package com.darius.unison.sync

import com.darius.unison.model.QueueItemId
import com.darius.unison.playback.PlaybackActivityState
import com.darius.unison.playback.PlaybackSpeedCommandGate
import org.junit.Assert.assertTrue
import org.junit.Test

class ManyParticipantSynchronizationSimulationTest {
    private val item = QueueItemId("many-participant-track")

    @Test
    fun sevenHealthyListenersStayBoundedWhileAnEighthRepeatedlyBuffers() {
        val healthyDevices =
            listOf(
                Device(0.9996, PlaybackSyncProfile.BALANCED, true, 0),
                Device(1.0003, PlaybackSyncProfile.TIGHT, false, 1),
                Device(0.9992, PlaybackSyncProfile.BALANCED, false, 2),
                Device(1.0012, PlaybackSyncProfile.SMOOTH, false, 3),
                Device(1.0008, PlaybackSyncProfile.BALANCED, false, 4),
                Device(0.9997, PlaybackSyncProfile.TIGHT, false, 5),
                Device(1.0005, PlaybackSyncProfile.SMOOTH, false, 6),
            )

        healthyDevices.forEach { device ->
            val tuning = device.profile.tuning()
            val controller = PlaybackSyncController(tuning)
            val player = FakeSynchronizedPlayer(item, device.hardwareRate)
            val noise = listOf(0L, 4L, -5L, 8L, -7L, 12L, -10L, 6L)
            val result =
                PlaybackScenarioRunner(
                        controller = controller,
                        player = player,
                        queueItemId = item,
                        speedGate = PlaybackSpeedCommandGate(tuning),
                    )
                    .run(
                        durationMs = 60 * 60 * 1_000L,
                        coordinatorUsesLocalClock = device.coordinator,
                        expectedPositionNoiseMs = { tick ->
                            noise[(tick + device.noisePhase) % noise.size]
                        },
                    )

            assertTrue(result.p95AbsoluteDriftMs < p95Limit(device.profile))
            assertTrue(result.maximumAbsoluteDriftMs < maximumLimit(device.profile))
            assertTrue(result.hardSeekCount == 0)
            assertTrue(result.maximumSpeed <= tuning.maximumSpeed + 0.0001f)
            assertTrue(result.minimumSpeed >= tuning.minimumSpeed - 0.0001f)
        }

        // The degraded listener is intentionally exercised separately because synchronized peers
        // never use another participant's local player as a clock or correction source.
        val degradedTuning = PlaybackSyncProfile.BALANCED.tuning()
        val degradedController = PlaybackSyncController(degradedTuning)
        val degradedPlayer = FakeSynchronizedPlayer(item, hardwareRate = 1.0015)
        val degraded =
            PlaybackScenarioRunner(
                    controller = degradedController,
                    player = degradedPlayer,
                    queueItemId = item,
                    speedGate = PlaybackSpeedCommandGate(degradedTuning),
                )
                .run(durationMs = 60 * 60 * 1_000L) { tick, player ->
                    // Repeated 10-second stalls model one overloaded or radio-starved phone.
                    player.activityState =
                        if (tick % 1_200 in 400..419) {
                            PlaybackActivityState.BUFFERING
                        } else {
                            PlaybackActivityState.READY_PLAYING
                        }
                }

        assertTrue(degraded.maximumSpeed <= degradedTuning.maximumSpeed + 0.0001f)
        assertTrue(degraded.minimumSpeed >= degradedTuning.minimumSpeed - 0.0001f)
        assertTrue(degraded.hardSeekCount <= 6)
    }

    private data class Device(
        val hardwareRate: Double,
        val profile: PlaybackSyncProfile,
        val coordinator: Boolean,
        val noisePhase: Int,
    )

    private fun p95Limit(profile: PlaybackSyncProfile): Long =
        when (profile) {
            PlaybackSyncProfile.TIGHT -> 120L
            PlaybackSyncProfile.BALANCED -> 150L
            PlaybackSyncProfile.SMOOTH -> 200L
        }

    private fun maximumLimit(profile: PlaybackSyncProfile): Long =
        when (profile) {
            PlaybackSyncProfile.TIGHT -> 250L
            PlaybackSyncProfile.BALANCED -> 320L
            PlaybackSyncProfile.SMOOTH -> 420L
        }
}
