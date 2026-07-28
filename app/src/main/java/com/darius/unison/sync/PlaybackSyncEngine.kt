package com.darius.unison.sync

import com.darius.unison.model.QueueItemId
import com.darius.unison.playback.AudioOutputRoute
import com.darius.unison.playback.PlaybackActivityState
import com.darius.unison.playback.PlaybackSample
import java.util.ArrayDeque
import kotlin.math.abs

/** Tunable synchronization policy. Keep every stability-sensitive value in one place. */
data class PlaybackSyncConfig(
    val tickIntervalMs: Long = 500,
    val referenceIntervalMs: Long = 1_000,
    val ignoreThresholdMs: Long = 80,
    val hardSeekThresholdMs: Long = 500,
    val maxSpeedDelta: Float = 0.005f,
    val requiredConsistentSamples: Int = 3,
    val filterWindowSize: Int = 5,
    val hardSeekCooldownMs: Long = 12_000,
    val settleAfterSeekMs: Long = 3_000,
    val maxClockUncertaintyMs: Long = 40,
    val speedSmoothing: Float = 0.35f,
    val baselineLimit: Float = 0.002f,
    val baselineLearningWindowMs: Long = 60_000,
) {
    init {
        require(tickIntervalMs > 0)
        require(referenceIntervalMs >= tickIntervalMs)
        require(ignoreThresholdMs >= 0)
        require(hardSeekThresholdMs > ignoreThresholdMs)
        require(maxSpeedDelta in 0f..0.05f)
        require(requiredConsistentSamples >= 2)
        require(filterWindowSize >= requiredConsistentSamples)
        require(hardSeekCooldownMs >= 0)
        require(settleAfterSeekMs >= 0)
        require(maxClockUncertaintyMs >= 0)
        require(speedSmoothing in 0f..1f)
        require(baselineLimit in 0f..maxSpeedDelta)
        require(baselineLearningWindowMs > 0)
    }
}

enum class PlaybackSyncState {
    DISABLED,
    WAITING_FOR_MEDIA,
    WAITING_FOR_CLOCK,
    ACQUIRING,
    TRACKING,
    SOFT_CORRECTING,
    HARD_SEEKING,
    SETTLING,
    BUFFERING,
    PAUSED,
    FAILED,
}

enum class SyncHoldReason {
    DISCONNECTED,
    NO_CANONICAL_ITEM,
    LOCAL_ITEM_MISMATCH,
    CLOCK_UNAVAILABLE,
    CLOCK_UNCERTAIN,
    PLAYER_IDLE,
    PREPARING,
    BUFFERING,
    PAUSED,
    ENDED,
    PLAYER_FAILED,
    INTENT_MISMATCH,
    SEEK_SETTLEMENT,
    ACQUIRING_SAMPLES,
    UNSTABLE_DIRECTION,
    FUTURE_COMMAND,
}

sealed interface SyncAction {
    data class SetSpeed(val speed: Float) : SyncAction
    data class Seek(val positionMs: Long) : SyncAction
    data class Hold(val reason: SyncHoldReason, val baselineSpeed: Float) : SyncAction
}

data class PlaybackSyncInput(
    val canonicalQueueItemId: QueueItemId?,
    val expectedPositionMs: Long,
    val sample: PlaybackSample,
    val connected: Boolean,
    val clockState: ClockSyncState,
    val clockUncertaintyNs: Long,
    val coordinatorUsesLocalClock: Boolean,
    /** Positive values mean audible output trails the player position. */
    val outputLatencyOffsetMs: Long = 0,
)

data class PlaybackSyncDecision(
    val action: SyncAction,
    val state: PlaybackSyncState,
    val rawDriftMs: Long?,
    val filteredDriftMs: Long?,
    val selectedSpeed: Float,
    val baselineSpeed: Float,
    val hardSeekCount: Int,
    val reason: String,
)

/**
 * One instance owns all automatic synchronization commands for one local player.
 * User and canonical scheduled commands remain outside this controller.
 */
class PlaybackSyncController(
    val config: PlaybackSyncConfig = PlaybackSyncConfig(),
) {
    private data class DriftPoint(val atLocalNs: Long, val driftMs: Long)
    private data class LearningAnchor(val atLocalNs: Long, val driftMs: Long, val appliedSpeed: Float)

    private val recent = ArrayDeque<DriftPoint>()
    private var trackedQueueItemId: QueueItemId? = null
    private var trackedOutputRoute: AudioOutputRoute? = null
    private var lastSeekRevision: Long? = null
    private var settleUntilLocalNs = 0L
    private var lastHardSeekLocalNs: Long? = null
    private var currentTargetSpeed = 1f
    private var learnedBaselineSpeed = 1f
    private var learningAnchor: LearningAnchor? = null
    private var hardSeekCount = 0

    var state: PlaybackSyncState = PlaybackSyncState.DISABLED
        private set

    val baselineSpeed: Float get() = learnedBaselineSpeed
    val automaticHardSeekCount: Int get() = hardSeekCount

    fun evaluate(input: PlaybackSyncInput): PlaybackSyncDecision {
        val sample = input.sample
        val nowNs = sample.sampledAtLocalNs

        if (!input.connected) return hold(PlaybackSyncState.DISABLED, SyncHoldReason.DISCONNECTED)
        val canonicalItem = input.canonicalQueueItemId
            ?: return hold(PlaybackSyncState.WAITING_FOR_MEDIA, SyncHoldReason.NO_CANONICAL_ITEM)

        if (trackedOutputRoute != null && trackedOutputRoute != sample.outputRoute) {
            resetInternal(preserveBaseline = false)
        }
        trackedOutputRoute = sample.outputRoute

        if (trackedQueueItemId != null && trackedQueueItemId != canonicalItem) {
            resetInternal(preserveBaseline = true)
        }
        trackedQueueItemId = canonicalItem

        if (sample.queueItemId != canonicalItem) {
            return hold(PlaybackSyncState.WAITING_FOR_MEDIA, SyncHoldReason.LOCAL_ITEM_MISMATCH)
        }

        if (!input.coordinatorUsesLocalClock) {
            if (input.clockState != ClockSyncState.LOCKED) {
                return hold(PlaybackSyncState.WAITING_FOR_CLOCK, SyncHoldReason.CLOCK_UNAVAILABLE)
            }
            if (input.clockUncertaintyNs > input.configuredMaxClockUncertaintyNs()) {
                return hold(PlaybackSyncState.WAITING_FOR_CLOCK, SyncHoldReason.CLOCK_UNCERTAIN)
            }
        }

        val previousSeekRevision = lastSeekRevision
        lastSeekRevision = sample.seekRevision
        if (previousSeekRevision != null && previousSeekRevision != sample.seekRevision) {
            beginSettlement(nowNs)
        }

        when (sample.activityState) {
            PlaybackActivityState.IDLE -> return hold(
                PlaybackSyncState.WAITING_FOR_MEDIA,
                SyncHoldReason.PLAYER_IDLE,
            )
            PlaybackActivityState.PREPARING -> return hold(
                PlaybackSyncState.WAITING_FOR_MEDIA,
                SyncHoldReason.PREPARING,
            )
            PlaybackActivityState.BUFFERING -> return hold(
                PlaybackSyncState.BUFFERING,
                SyncHoldReason.BUFFERING,
            )
            PlaybackActivityState.READY_PAUSED -> return hold(
                PlaybackSyncState.PAUSED,
                SyncHoldReason.PAUSED,
            )
            PlaybackActivityState.ENDED -> return hold(
                PlaybackSyncState.WAITING_FOR_MEDIA,
                SyncHoldReason.ENDED,
            )
            PlaybackActivityState.FAILED -> return hold(
                PlaybackSyncState.FAILED,
                SyncHoldReason.PLAYER_FAILED,
            )
            PlaybackActivityState.READY_PLAYING -> Unit
        }

        if (!sample.playWhenReady || !sample.isPlaying) {
            return hold(PlaybackSyncState.PAUSED, SyncHoldReason.INTENT_MISMATCH)
        }
        if (nowNs < settleUntilLocalNs) {
            return hold(PlaybackSyncState.SETTLING, SyncHoldReason.SEEK_SETTLEMENT)
        }

        // Audible output corresponds to an earlier player position when route latency is positive.
        val audiblePositionMs = sample.positionMs - input.outputLatencyOffsetMs
        val rawDriftMs = input.expectedPositionMs - audiblePositionMs
        appendPoint(DriftPoint(nowNs, rawDriftMs))

        val consistent = consistentTail()
        val filtered = median(recent.map { it.driftMs })
        if (consistent == null) {
            val insideIgnoreZone = abs(rawDriftMs) < config.ignoreThresholdMs
            if (insideIgnoreZone) {
                learnBaseline(nowNs, rawDriftMs, sample.playbackSpeed)
                state = PlaybackSyncState.TRACKING
                return speedDecision(
                    target = learnedBaselineSpeed,
                    rawDriftMs = rawDriftMs,
                    filteredDriftMs = filtered,
                    reason = "inside_ignore_zone",
                )
            }
            state = PlaybackSyncState.ACQUIRING
            val holdReason = if (recent.size >= config.requiredConsistentSamples) {
                SyncHoldReason.UNSTABLE_DIRECTION
            } else {
                SyncHoldReason.ACQUIRING_SAMPLES
            }
            return holdWithDrift(holdReason, rawDriftMs, filtered)
        }

        val confirmedDriftMs = median(consistent.map { it.driftMs })
        learnBaseline(nowNs, confirmedDriftMs, sample.playbackSpeed)
        if (abs(confirmedDriftMs) >= config.hardSeekThresholdMs) {
            val cooldownElapsedMs = lastHardSeekLocalNs?.let { (nowNs - it) / 1_000_000L }
            if (cooldownElapsedMs == null || cooldownElapsedMs >= config.hardSeekCooldownMs) {
                lastHardSeekLocalNs = nowNs
                hardSeekCount++
                state = PlaybackSyncState.HARD_SEEKING
                recent.clear()
                learningAnchor = null
                currentTargetSpeed = learnedBaselineSpeed
                settleUntilLocalNs = nowNs + config.settleAfterSeekMs * 1_000_000L
                return PlaybackSyncDecision(
                    action = SyncAction.Seek(input.expectedPositionMs.coerceAtLeast(0)),
                    state = state,
                    rawDriftMs = rawDriftMs,
                    filteredDriftMs = confirmedDriftMs,
                    selectedSpeed = learnedBaselineSpeed,
                    baselineSpeed = learnedBaselineSpeed,
                    hardSeekCount = hardSeekCount,
                    reason = "confirmed_hard_seek",
                )
            }
        }

        val magnitude = abs(confirmedDriftMs)
        if (magnitude < config.ignoreThresholdMs) {
            state = PlaybackSyncState.TRACKING
            return speedDecision(
                learnedBaselineSpeed,
                rawDriftMs,
                confirmedDriftMs,
                "filtered_inside_ignore_zone",
            )
        }

        val softRange = (config.hardSeekThresholdMs - config.ignoreThresholdMs).coerceAtLeast(1)
        val normalized = ((magnitude - config.ignoreThresholdMs).toDouble() / softRange)
            .coerceIn(0.0, 1.0)
            .toFloat()
        val minimumUsefulDelta = minOf(0.0005f, config.maxSpeedDelta)
        val delta = (minimumUsefulDelta + (config.maxSpeedDelta - minimumUsefulDelta) * normalized)
            .coerceAtMost(config.maxSpeedDelta)
        val target = if (confirmedDriftMs > 0) {
            learnedBaselineSpeed + delta
        } else {
            learnedBaselineSpeed - delta
        }
        state = PlaybackSyncState.SOFT_CORRECTING
        return speedDecision(target, rawDriftMs, confirmedDriftMs, "continuous_soft_correction")
    }

    fun holdForFutureCommand(): PlaybackSyncDecision =
        hold(PlaybackSyncState.ACQUIRING, SyncHoldReason.FUTURE_COMMAND)

    fun reset(preserveLearnedBaseline: Boolean = true) {
        resetInternal(preserveLearnedBaseline)
        state = PlaybackSyncState.ACQUIRING
    }

    private fun appendPoint(point: DriftPoint) {
        recent.addLast(point)
        while (recent.size > config.filterWindowSize) recent.removeFirst()
    }

    private fun consistentTail(): List<DriftPoint>? {
        if (recent.size < config.requiredConsistentSamples) return null
        val tail = recent.toList().takeLast(config.requiredConsistentSamples)
        if (tail.any { abs(it.driftMs) < config.ignoreThresholdMs }) return null
        val direction = tail.first().driftMs.sign()
        if (direction == 0 || tail.any { it.driftMs.sign() != direction }) return null
        return tail
    }

    private fun speedDecision(
        target: Float,
        rawDriftMs: Long?,
        filteredDriftMs: Long?,
        reason: String,
    ): PlaybackSyncDecision {
        val minSpeed = learnedBaselineSpeed - config.maxSpeedDelta
        val maxSpeed = learnedBaselineSpeed + config.maxSpeedDelta
        val boundedTarget = target.coerceIn(minSpeed, maxSpeed)
        currentTargetSpeed += (boundedTarget - currentTargetSpeed) * config.speedSmoothing
        if (abs(currentTargetSpeed - learnedBaselineSpeed) < 0.00002f) {
            currentTargetSpeed = learnedBaselineSpeed
        }
        return PlaybackSyncDecision(
            action = SyncAction.SetSpeed(currentTargetSpeed),
            state = state,
            rawDriftMs = rawDriftMs,
            filteredDriftMs = filteredDriftMs,
            selectedSpeed = currentTargetSpeed,
            baselineSpeed = learnedBaselineSpeed,
            hardSeekCount = hardSeekCount,
            reason = reason,
        )
    }

    private fun hold(state: PlaybackSyncState, reason: SyncHoldReason): PlaybackSyncDecision {
        this.state = state
        recent.clear()
        learningAnchor = null
        currentTargetSpeed += (learnedBaselineSpeed - currentTargetSpeed) * config.speedSmoothing
        if (abs(currentTargetSpeed - learnedBaselineSpeed) < 0.00002f) {
            currentTargetSpeed = learnedBaselineSpeed
        }
        return PlaybackSyncDecision(
            action = SyncAction.Hold(reason, learnedBaselineSpeed),
            state = state,
            rawDriftMs = null,
            filteredDriftMs = null,
            selectedSpeed = learnedBaselineSpeed,
            baselineSpeed = learnedBaselineSpeed,
            hardSeekCount = hardSeekCount,
            reason = reason.name.lowercase(),
        )
    }

    private fun holdWithDrift(
        reason: SyncHoldReason,
        rawDriftMs: Long,
        filteredDriftMs: Long,
    ): PlaybackSyncDecision {
        currentTargetSpeed += (learnedBaselineSpeed - currentTargetSpeed) * config.speedSmoothing
        return PlaybackSyncDecision(
            action = SyncAction.Hold(reason, learnedBaselineSpeed),
            state = state,
            rawDriftMs = rawDriftMs,
            filteredDriftMs = filteredDriftMs,
            selectedSpeed = learnedBaselineSpeed,
            baselineSpeed = learnedBaselineSpeed,
            hardSeekCount = hardSeekCount,
            reason = reason.name.lowercase(),
        )
    }

    private fun beginSettlement(nowNs: Long) {
        recent.clear()
        learningAnchor = null
        currentTargetSpeed = learnedBaselineSpeed
        settleUntilLocalNs = nowNs + config.settleAfterSeekMs * 1_000_000L
        state = PlaybackSyncState.SETTLING
    }

    private fun learnBaseline(nowNs: Long, driftMs: Long, appliedSpeed: Float) {
        val anchor = learningAnchor
        if (anchor == null) {
            learningAnchor = LearningAnchor(nowNs, driftMs, appliedSpeed)
            return
        }
        val elapsedMs = (nowNs - anchor.atLocalNs) / 1_000_000.0
        if (elapsedMs < config.baselineLearningWindowMs) return
        if (elapsedMs <= 0.0) {
            learningAnchor = LearningAnchor(nowNs, driftMs, appliedSpeed)
            return
        }
        val driftSlope = (driftMs - anchor.driftMs) / elapsedMs
        val candidate = (anchor.appliedSpeed + driftSlope.toFloat())
            .coerceIn(1f - config.baselineLimit, 1f + config.baselineLimit)
        learnedBaselineSpeed += (candidate - learnedBaselineSpeed) * 0.1f
        learnedBaselineSpeed = learnedBaselineSpeed.coerceIn(
            1f - config.baselineLimit,
            1f + config.baselineLimit,
        )
        learningAnchor = LearningAnchor(nowNs, driftMs, appliedSpeed)
    }

    private fun resetInternal(preserveBaseline: Boolean) {
        recent.clear()
        learningAnchor = null
        trackedQueueItemId = null
        lastSeekRevision = null
        settleUntilLocalNs = 0L
        lastHardSeekLocalNs = null
        if (!preserveBaseline) learnedBaselineSpeed = 1f
        currentTargetSpeed = learnedBaselineSpeed
    }

    private fun PlaybackSyncInput.configuredMaxClockUncertaintyNs(): Long =
        config.maxClockUncertaintyMs * 1_000_000L

    private fun Long.sign(): Int = when {
        this > 0L -> 1
        this < 0L -> -1
        else -> 0
    }

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else (sorted[middle - 1] / 2L) + (sorted[middle] / 2L)
    }
}

/** Source-compatible name for older call sites while the app migrates to the controller wording. */
typealias PlaybackSyncEngine = PlaybackSyncController
