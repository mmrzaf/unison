package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.playback.PlaybackActivityState
import com.darius.unison.playback.PlaybackSample
import com.darius.unison.playback.PlayerExecutor
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.PlayerState
import com.darius.unison.sync.ClockEstimate
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.sync.ClockSyncState
import com.darius.unison.sync.PlaybackSyncDecision
import com.darius.unison.sync.PlaybackSyncInput
import com.darius.unison.sync.PlaybackSyncProfile
import com.darius.unison.sync.PlaybackSyncState
import com.darius.unison.sync.PlaybackSyncTuning
import com.darius.unison.sync.SyncAction
import com.darius.unison.util.MonotonicClock
import kotlin.math.abs

/**
 * Owns the local playback feedback loop.
 *
 * Room serialization is deliberately not part of this loop: a slow join, transfer, snapshot or
 * storage operation must not delay sampling enough to manufacture a synchronization failure. The
 * controller reads immutable canonical snapshots and thread-safe clock/session estimates, then
 * submits every Media3 correction through [PlayerExecutor]. Room/network side effects remain with
 * RoomRuntime and are triggered separately at a much lower cadence.
 */
internal class LocalPlaybackSyncController(
    private val player: PlayerPort,
    private val playerExecutor: PlayerExecutor,
    private val clock: MonotonicClock,
    private val clockSync: ClockSyncEngine,
    private val playbackSession: PlaybackSessionCoordinator,
    private val synchronization: PlaybackSynchronizationRuntime,
) {
    sealed interface TickResult {
        data class Evaluated(
            val sample: PlaybackSample,
            val canonical: CanonicalPlaybackState,
            val sampleCoordinatorNs: Long,
            val clockEstimate: ClockEstimate,
            val decision: PlaybackSyncDecision,
            val canonicalPositionMs: Long?,
        ) : TickResult

        data class Reacquiring(val reason: String) : TickResult
    }

    val tuning: PlaybackSyncTuning
        get() = synchronization.tuning

    val state: PlaybackSyncState
        get() = synchronization.state

    fun intervalMs(snapshot: RoomSnapshot?, playerState: PlayerState): Long? =
        PlaybackSyncCadencePolicy.intervalMs(
            queueItemPresent = snapshot?.playback?.queueItemId != null,
            canonicalPlaying = snapshot?.playback?.isPlaying == true,
            scheduledCommandPresent = playerExecutor.hasPendingTransport,
            localBuffering = playerState.buffering,
            syncState = state,
            tuning = tuning,
        )

    suspend fun tick(
        snapshot: RoomSnapshot,
        coordinator: Boolean,
        connected: Boolean,
    ): TickResult {
        val sample = player.samplePlayback()
        val canonical = playbackSession.canonicalForTick(snapshot, coordinator)
        if (playbackSession.observeOutputRoute(sample.outputRoute)) {
            // Output-route latency/decoder history changed, not the network clock. Reacquire only
            // playback convergence; throwing away a healthy coordinator clock here creates avoidable
            // transport unavailability after headphones/Bluetooth changes.
            resetPlaybackConvergence(
                canonical = canonical.takeIf { coordinator },
                preserveLearnedBaseline = false,
            )
            return TickResult.Reacquiring("audio_route_change")
        }

        val clockEstimate: ClockEstimate
        val sampleCoordinatorNs: Long
        if (coordinator) {
            sampleCoordinatorNs = sample.sampledAtLocalNs
            clockEstimate =
                ClockEstimate(
                    offsetNs = 0L,
                    rate = 1.0,
                    rttNs = 0L,
                    rttVariationNs = 0L,
                    uncertaintyNs = 0L,
                    sampledAtLocalNs = sample.sampledAtLocalNs,
                    lastGoodSampleLocalNs = sample.sampledAtLocalNs,
                    sampleAgeNs = 0L,
                    acceptedSampleCount = Int.MAX_VALUE,
                    rejectedSampleCount = 0,
                    state = ClockSyncState.LOCKED,
                )
        } else {
            val conversion = clockSync.toCoordinatorTimeWithUncertainty(sample.sampledAtLocalNs)
            sampleCoordinatorNs = conversion.timeNs
            clockEstimate = clockSync.estimate(sample.sampledAtLocalNs)
        }

        val futureCommand =
            canonical.coordinatorTimestampNs > sampleCoordinatorNs + FUTURE_COMMAND_TOLERANCE_NS
        val decision =
            if (futureCommand) {
                synchronization.holdForFutureCommand()
            } else {
                synchronization.evaluate(
                    PlaybackSyncInput(
                        canonicalQueueItemId = canonical.queueItemId,
                        expectedPositionMs = canonical.projectedPositionMs(sampleCoordinatorNs),
                        sample = sample,
                        connected = connected,
                        clockState = clockEstimate.state,
                        clockUncertaintyNs = clockEstimate.uncertaintyNs,
                        coordinatorUsesLocalClock = coordinator,
                        // Route-specific latency stays zero until Unison has a measured source.
                        outputLatencyOffsetMs = 0L,
                    )
                )
            }
        applyDecision(decision, sample.playbackSpeed)
        return TickResult.Evaluated(
            sample = sample,
            canonical = canonical,
            sampleCoordinatorNs = sampleCoordinatorNs,
            clockEstimate = clockEstimate,
            decision = decision,
            canonicalPositionMs =
                if (futureCommand) null else canonical.projectedPositionMs(sampleCoordinatorNs),
        )
    }

    fun shouldReportPlaybackStatus(sampledAtLocalNs: Long): Boolean =
        playbackSession.shouldReportPlaybackStatus(sampledAtLocalNs)

    fun shouldBroadcastPlaybackReference(nowCoordinatorNs: Long): Boolean =
        playbackSession.shouldBroadcastPlaybackReference(
            nowCoordinatorNs = nowCoordinatorNs,
            intervalNs = tuning.referenceIntervalMs * 1_000_000L,
        )

    suspend fun resetPlaybackConvergence(
        canonical: CanonicalPlaybackState?,
        preserveLearnedBaseline: Boolean,
    ) {
        synchronization.reset(preserveLearnedBaseline)
        playbackSession.resetAfterDiscontinuity(canonical)
        normalizeSpeed()
    }

    suspend fun updateProfile(profile: PlaybackSyncProfile): Boolean {
        if (!synchronization.updateProfile(profile)) return false
        normalizeSpeed()
        return true
    }

    suspend fun resetTracking(preserveLearnedBaseline: Boolean) {
        synchronization.reset(preserveLearnedBaseline)
        normalizeSpeed()
    }

    fun resetRuntime(preserveLearnedBaseline: Boolean = false) {
        synchronization.reset(preserveLearnedBaseline)
    }

    private suspend fun applyDecision(decision: PlaybackSyncDecision, actualSpeed: Float) {
        when (val action = decision.action) {
            is SyncAction.SetSpeed -> applySpeedTarget(action.speed, actualSpeed)
            is SyncAction.Seek -> {
                applySpeedTarget(decision.baselineSpeed, actualSpeed)
                playerExecutor.synchronize { seekTo(action.positionMs) }
            }
            is SyncAction.Hold -> applySpeedTarget(action.baselineSpeed, actualSpeed)
        }
    }

    private suspend fun applySpeedTarget(targetSpeed: Float, actualSpeed: Float) {
        synchronization
            .selectSpeed(
                requestedSpeed = targetSpeed,
                actualSpeed = actualSpeed,
                nowNs = clock.nowNs(),
            )
            ?.let { selected -> playerExecutor.synchronize { setPlaybackSpeed(selected) } }
    }

    private suspend fun normalizeSpeed() {
        val actualSpeed = player.state.value.playbackSpeed
        if (abs(actualSpeed - 1f) > PLAYBACK_SPEED_EPSILON) {
            playerExecutor.synchronize { setPlaybackSpeed(1f) }
        }
    }

    private companion object {
        const val FUTURE_COMMAND_TOLERANCE_NS = 25_000_000L
        const val PLAYBACK_SPEED_EPSILON = 0.0001f
    }
}
