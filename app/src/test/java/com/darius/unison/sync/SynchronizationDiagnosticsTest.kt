package com.darius.unison.sync

import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SynchronizationDiagnosticsTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var log: DiagnosticLog

    @Before
    fun setUp() {
        log = DiagnosticLog(temporaryFolder.newFile("diagnostics.log"))
    }

    @After
    fun tearDown() {
        log.close()
    }

    @Test
    fun clearAllowsImmediateRoutineSampleForTheNextRoom() = runBlocking {
        val diagnostics = SynchronizationDiagnostics(this, log)
        diagnostics.record(routineEvent(1_000_000_000L))
        awaitSize(diagnostics, 1)
        diagnostics.clear()

        diagnostics.record(routineEvent(1_000_000_001L))
        awaitSize(diagnostics, 1)

        assertEquals(1, diagnostics.snapshot().size)
        assertTrue(diagnostics.closeAndJoin())
    }

    @Test
    fun closeAndJoinDrainsAcceptedEvents() = runBlocking {
        val diagnostics = SynchronizationDiagnostics(this, log)
        diagnostics.record(routineEvent(1L).copy(action = "SEEK"))

        assertTrue(diagnostics.closeAndJoin())
        assertEquals(1, diagnostics.snapshot().size)
    }

    @Test
    fun stablePausedStateLogsTransitionThenUsesRoutineSampling() = runBlocking {
        val diagnostics = SynchronizationDiagnostics(this, log)
        val paused =
            routineEvent(1L)
                .copy(
                    playbackSyncState = "PAUSED",
                    action = "HOLD",
                    actionReason = "paused",
                )
        diagnostics.record(paused)
        diagnostics.record(paused.copy(timestampLocalNs = 500_000_001L))
        diagnostics.record(paused.copy(timestampLocalNs = 19_000_000_001L))
        awaitSize(diagnostics, 1)

        diagnostics.record(paused.copy(timestampLocalNs = 21_000_000_001L))
        awaitSize(diagnostics, 2)

        assertTrue(diagnostics.closeAndJoin())
    }

    @Test
    fun continuousSoftCorrectionIsRateLimitedButStateChangesRemainImmediate() = runBlocking {
        val diagnostics = SynchronizationDiagnostics(this, log)
        val correcting =
            routineEvent(1L)
                .copy(
                    rawDriftMs = 350,
                    playbackSyncState = "SOFT_CORRECTING",
                    action = "SET_SPEED",
                    actionReason = "continuous_soft_correction",
                )
        diagnostics.record(correcting)
        diagnostics.record(correcting.copy(timestampLocalNs = 500_000_001L))
        awaitSize(diagnostics, 1)

        diagnostics.record(correcting.copy(timestampLocalNs = 2_100_000_001L))
        diagnostics.record(
            correcting.copy(
                timestampLocalNs = 2_200_000_001L,
                playbackSyncState = "SETTLING",
                action = "HOLD",
                actionReason = "seek_settlement",
            )
        )
        awaitSize(diagnostics, 3)

        assertTrue(diagnostics.closeAndJoin())
    }

    @Test
    fun sixHourPausedRoomRemainsBounded() = runBlocking {
        val diagnostics = SynchronizationDiagnostics(this, log, maxEntries = 64)
        val paused =
            routineEvent(0L)
                .copy(
                    playbackSyncState = "PAUSED",
                    action = "HOLD",
                    actionReason = "paused",
                )
        val halfSecondNs = 500_000_000L
        val samples = 6 * 60 * 60 * 2
        repeat(samples) { index ->
            diagnostics.record(paused.copy(timestampLocalNs = index * halfSecondNs))
        }

        assertTrue(diagnostics.closeAndJoin())
        assertTrue(diagnostics.snapshot().isNotEmpty())
        assertTrue(diagnostics.snapshot().size <= 64)
    }

    private suspend fun awaitSize(diagnostics: SynchronizationDiagnostics, expected: Int) {
        repeat(100) {
            if (diagnostics.snapshot().size == expected) return
            delay(1)
        }
        assertEquals(expected, diagnostics.snapshot().size)
    }

    private fun routineEvent(timestampNs: Long) =
        SynchronizationEvent(
            timestampLocalNs = timestampNs,
            timestampCoordinatorNs = timestampNs,
            deviceId = "device",
            deviceModel = "model",
            androidVersion = 36,
            outputRoute = "speaker",
            roomIdHash = "room",
            coordinatorTerm = 1,
            queueItemId = null,
            canonicalPositionMs = null,
            sampledPlayerPositionMs = 0,
            sampleAgeMs = 0,
            rawDriftMs = 0,
            filteredDriftMs = 0,
            selectedSpeed = 1f,
            learnedBaselineSpeed = 1f,
            clockOffsetNs = 0,
            clockRate = 1.0,
            clockRttMs = 1.0,
            clockUncertaintyMs = 1.0,
            clockState = "LOCKED",
            playbackSyncState = "TRACKING",
            action = "NONE",
            actionReason = "routine",
            hardSeekCount = 0,
            buffering = false,
        )
}
