package com.darius.unison.sync

import com.darius.unison.model.QueueItemId
import com.darius.unison.playback.AudioOutputRoute
import com.darius.unison.playback.PlaybackActivityState
import com.darius.unison.playback.PlaybackSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSyncEngineTest {
    private val item = QueueItemId("item")

    @Test
    fun requiresThreeConsistentSamplesBeforeSoftCorrection() {
        val controller = PlaybackSyncController()
        val first = controller.evaluate(input(atMs = 0, expectedMs = 10_000, actualMs = 9_850))
        val second = controller.evaluate(input(atMs = 500, expectedMs = 10_500, actualMs = 10_350))
        val third = controller.evaluate(input(atMs = 1_000, expectedMs = 11_000, actualMs = 10_850))

        assertTrue(first.action is SyncAction.Hold)
        assertTrue(second.action is SyncAction.Hold)
        assertTrue(third.action is SyncAction.SetSpeed)
        third.action as SyncAction.SetSpeed
        assertTrue(third.action.speed > 1f)
        assertTrue(third.action.speed <= 1.005f)
    }

    @Test
    fun unstableDirectionDoesNotCorrect() {
        val controller = PlaybackSyncController()
        controller.evaluate(input(0, 10_000, 9_850))
        controller.evaluate(input(500, 10_500, 10_560))
        val decision = controller.evaluate(input(1_000, 11_000, 10_870))
        assertTrue(decision.action is SyncAction.Hold)
    }

    @Test
    fun hardSeekRequiresConfirmationAndEntersSettlement() {
        val controller = PlaybackSyncController()
        controller.evaluate(input(0, 10_000, 9_300))
        controller.evaluate(input(500, 10_500, 9_800))
        val decision = controller.evaluate(input(1_000, 11_000, 10_300))
        assertEquals(SyncAction.Seek(11_000), decision.action)

        val settling = controller.evaluate(input(1_500, 11_500, 11_000, seekRevision = 1))
        assertTrue(settling.action is SyncAction.Hold)
        assertEquals(PlaybackSyncState.SETTLING, settling.state)
    }

    @Test
    fun hardSeekCooldownPreventsRepeatedSeeking() {
        val controller = PlaybackSyncController()
        controller.evaluate(input(0, 10_000, 9_000))
        controller.evaluate(input(500, 10_500, 9_500))
        val firstSeek = controller.evaluate(input(1_000, 11_000, 10_000))
        assertTrue(firstSeek.action is SyncAction.Seek)

        // Allow settlement to expire, then present another confirmed large error inside cooldown.
        controller.evaluate(input(4_500, 14_500, 13_500, seekRevision = 1))
        controller.evaluate(input(5_000, 15_000, 14_000, seekRevision = 1))
        val insideCooldown = controller.evaluate(input(5_500, 15_500, 14_500, seekRevision = 1))
        assertTrue(insideCooldown.action !is SyncAction.Seek)
        assertEquals(1, insideCooldown.hardSeekCount)
    }

    @Test
    fun bufferingRestoresBaselineAndNeverCorrects() {
        val controller = PlaybackSyncController()
        val decision =
            controller.evaluate(
                input(0, 10_000, 9_000)
                    .copy(
                        sample =
                            input(0, 10_000, 9_000)
                                .sample
                                .copy(
                                    activityState = PlaybackActivityState.BUFFERING,
                                    isPlaying = false,
                                    playbackSpeed = 1.004f,
                                )
                    )
            )
        assertTrue(decision.action is SyncAction.Hold)
        decision.action as SyncAction.Hold
        assertEquals(1f, decision.action.baselineSpeed)
        assertEquals(PlaybackSyncState.BUFFERING, decision.state)
    }

    @Test
    fun coordinatorRunsTheSameCorrectionController() {
        val controller = PlaybackSyncController()
        controller.evaluate(input(0, 10_000, 9_850))
        controller.evaluate(input(500, 10_500, 10_350))
        val decision = controller.evaluate(input(1_000, 11_000, 10_850))
        assertEquals(PlaybackSyncState.SOFT_CORRECTING, decision.state)
        assertTrue(decision.action is SyncAction.SetSpeed)
    }

    @Test
    fun `coordinator applies route latency against the canonical timeline`() {
        val controller = PlaybackSyncController()
        repeat(2) { index ->
            controller.evaluate(
                input(
                        atMs = index * 500L,
                        expectedMs = 10_000L + index * 500L,
                        actualMs = 10_000L + index * 500L,
                    )
                    .copy(outputLatencyOffsetMs = 120L)
            )
        }
        val decision =
            controller.evaluate(
                input(atMs = 1_000L, expectedMs = 11_000L, actualMs = 11_000L)
                    .copy(outputLatencyOffsetMs = 120L)
            )

        assertEquals(120L, decision.rawDriftMs)
        assertEquals(PlaybackSyncState.SOFT_CORRECTING, decision.state)
        assertTrue(decision.action is SyncAction.SetSpeed)
    }

    @Test
    fun routeChangeClearsMeasurementHistoryAndBaseline() {
        val controller = PlaybackSyncController()
        controller.evaluate(input(0, 10_000, 9_850))
        controller.evaluate(input(500, 10_500, 10_350))
        val correcting = controller.evaluate(input(1_000, 11_000, 10_850))
        assertTrue(correcting.action is SyncAction.SetSpeed)

        val changedRoute =
            input(1_500, 11_500, 11_350)
                .copy(
                    sample =
                        input(1_500, 11_500, 11_350)
                            .sample
                            .copy(outputRoute = AudioOutputRoute.BLUETOOTH)
                )
        val reacquiring = controller.evaluate(changedRoute)
        assertEquals(PlaybackSyncState.ACQUIRING, reacquiring.state)
        assertTrue(reacquiring.action is SyncAction.Hold)
        assertEquals(1f, reacquiring.baselineSpeed)
    }

    @Test
    fun pauseAndDisconnectSuppressAllAutomaticCorrection() {
        val pausedInput =
            input(0, 10_000, 9_000)
                .copy(
                    sample =
                        input(0, 10_000, 9_000)
                            .sample
                            .copy(
                                activityState = PlaybackActivityState.READY_PAUSED,
                                isPlaying = false,
                                playWhenReady = false,
                            )
                )
        val paused = PlaybackSyncController().evaluate(pausedInput)
        assertEquals(PlaybackSyncState.PAUSED, paused.state)
        assertTrue(paused.action is SyncAction.Hold)

        val disconnected =
            PlaybackSyncController().evaluate(input(0, 10_000, 9_000).copy(connected = false))
        assertEquals(PlaybackSyncState.DISABLED, disconnected.state)
        assertTrue(disconnected.action is SyncAction.Hold)
    }

    @Test
    fun clockUncertaintySuppressesParticipantCorrection() {
        val controller = PlaybackSyncController()
        val decision =
            controller.evaluate(
                input(0, 10_000, 9_000)
                    .copy(
                        coordinatorUsesLocalClock = false,
                        clockUncertaintyNs = 100_000_000L,
                    )
            )
        assertEquals(PlaybackSyncState.WAITING_FOR_CLOCK, decision.state)
        assertTrue(decision.action is SyncAction.Hold)
    }

    @Test
    fun baselineLearningUsesTheActuallyAppliedPlayerSpeed() {
        val controller =
            PlaybackSyncController(PlaybackSyncConfig(baselineLearningWindowMs = 1_000L))
        val first =
            input(0, 10_000, 9_990)
                .copy(sample = input(0, 10_000, 9_990).sample.copy(playbackSpeed = 0.999f))
        val second =
            input(1_000, 11_000, 10_990)
                .copy(sample = input(1_000, 11_000, 10_990).sample.copy(playbackSpeed = 0.999f))

        controller.evaluate(first)
        val learned = controller.evaluate(second)

        assertTrue(learned.baselineSpeed < 0.99995f)
        assertTrue(learned.baselineSpeed > 0.9998f)
    }

    @Test
    fun softCorrectionUsesAReleaseDeadbandInsteadOfTogglingAtTheAcquireThreshold() {
        val controller = PlaybackSyncController()
        controller.evaluate(input(0, 10_000, 9_850))
        controller.evaluate(input(500, 10_500, 10_350))
        val correcting = controller.evaluate(input(1_000, 11_000, 10_850))
        assertEquals(PlaybackSyncState.SOFT_CORRECTING, correcting.state)

        // Repeated 60 ms samples are below the 80 ms acquisition threshold but above the 20 ms
        // release threshold. Once correction is active, they must not toggle the player back to
        // normal speed merely because the drift crossed the acquisition boundary.
        repeat(3) { index ->
            val atMs = 1_500L + index * 500L
            val expected = 11_500L + index * 500L
            val stillCorrecting = controller.evaluate(input(atMs, expected, expected - 60L))
            assertEquals(PlaybackSyncState.SOFT_CORRECTING, stillCorrecting.state)
            assertTrue(stillCorrecting.action is SyncAction.SetSpeed)
        }

        repeat(3) { index ->
            val atMs = 3_000L + index * 500L
            val expected = 13_000L + index * 500L
            controller.evaluate(input(atMs, expected, expected - 10L))
        }
        assertEquals(PlaybackSyncState.TRACKING, controller.state)
    }

    private fun input(
        atMs: Long,
        expectedMs: Long,
        actualMs: Long,
        seekRevision: Long = 0,
    ) =
        PlaybackSyncInput(
            canonicalQueueItemId = item,
            expectedPositionMs = expectedMs,
            sample =
                PlaybackSample(
                    queueItemId = item,
                    positionMs = actualMs,
                    durationMs = 120_000,
                    sampledAtLocalNs = atMs * 1_000_000L,
                    playWhenReady = true,
                    isPlaying = true,
                    activityState = PlaybackActivityState.READY_PLAYING,
                    playbackSpeed = 1f,
                    outputRoute = AudioOutputRoute.BUILT_IN_SPEAKER,
                    seekRevision = seekRevision,
                ),
            connected = true,
            clockState = ClockSyncState.LOCKED,
            clockUncertaintyNs = 0,
            coordinatorUsesLocalClock = true,
        )
}
