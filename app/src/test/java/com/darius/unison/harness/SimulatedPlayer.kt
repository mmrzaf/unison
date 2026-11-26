package com.darius.unison.harness

import com.darius.unison.model.LocalPlaybackInhibitionReason
import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.QueueItemId
import com.darius.unison.playback.LocalPlayableItem
import com.darius.unison.playback.PlaybackActivityState
import com.darius.unison.playback.PlaybackPauseCause
import com.darius.unison.playback.PlaybackSample
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.PlayerState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Deterministic player model. It keeps only the state the room reacts to and counts every physical
 * mutation, which is what the "quiet steady state" invariants assert on.
 */
class SimulatedPlayer(private val clock: com.darius.unison.util.MonotonicClock) : PlayerPort {
    private val mutable = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutable.asStateFlow()

    /** Virtual time every physical mutation takes, to model a busy Media3 thread. */
    @Volatile var mutationLatencyMs = 0L

    private suspend fun busy() {
        if (mutationLatencyMs > 0) delay(mutationLatencyMs)
    }

    val setQueueCalls = AtomicInteger()
    val playCalls = AtomicInteger()
    val pauseCalls = AtomicInteger()
    val seekCalls = AtomicInteger()
    val seekItemCalls = AtomicInteger()
    val speedCalls = AtomicInteger()
    val rejoinCalls = AtomicInteger()

    override suspend fun samplePlayback(): PlaybackSample {
        val current = mutable.value
        return PlaybackSample(
            queueItemId = current.queueItemId,
            positionMs = current.positionMs,
            durationMs = current.durationMs,
            sampledAtLocalNs = clock.nowNs(),
            playWhenReady = current.playWhenReady,
            isPlaying = current.isPlaying,
            activityState = current.activityState,
            playbackSpeed = current.playbackSpeed,
            outputRoute = current.outputRoute,
            seekRevision = current.seekRevision,
        )
    }

    override suspend fun setQueue(
        items: List<LocalPlayableItem>,
        currentQueueItemId: QueueItemId?,
        positionMs: Long,
    ) {
        busy()
        setQueueCalls.incrementAndGet()
        val duration =
            items.firstOrNull { it.queueItemId == currentQueueItemId }?.track?.durationMs ?: 0L
        mutable.update {
            it.copy(
                queueItemId = currentQueueItemId,
                positionMs = positionMs,
                durationMs = duration,
                prepared = currentQueueItemId != null,
                buffering = false,
                activityState =
                    if (currentQueueItemId == null) PlaybackActivityState.IDLE
                    else PlaybackActivityState.READY_PAUSED,
            )
        }
    }

    override suspend fun play(): Boolean {
        busy()
        playCalls.incrementAndGet()
        mutable.update {
            it.copy(
                playWhenReady = true,
                isPlaying = true,
                activityState = PlaybackActivityState.READY_PLAYING,
            )
        }
        return true
    }

    override suspend fun rejoinLivePlayback(
        queueItemId: QueueItemId,
        positionMs: Long,
    ): Boolean {
        busy()
        rejoinCalls.incrementAndGet()
        mutable.update {
            it.copy(
                queueItemId = queueItemId,
                positionMs = positionMs,
                playWhenReady = true,
                isPlaying = true,
                participation = LocalPlaybackParticipation.ACTIVE,
                inhibitionReason = null,
                outputResumeBlocked = false,
                automaticRejoinAllowed = false,
                activityState = PlaybackActivityState.READY_PLAYING,
            )
        }
        return true
    }

    override suspend fun resetLocalPlaybackParticipation() {
        mutable.update {
            it.copy(
                participation = LocalPlaybackParticipation.ACTIVE,
                inhibitionReason = null,
                outputResumeBlocked = false,
                automaticRejoinAllowed = false,
            )
        }
    }

    override suspend fun pause(cause: PlaybackPauseCause) {
        busy()
        pauseCalls.incrementAndGet()
        mutable.update {
            it.copy(
                playWhenReady = false,
                isPlaying = false,
                activityState = PlaybackActivityState.READY_PAUSED,
            )
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        busy()
        seekCalls.incrementAndGet()
        mutable.update {
            it.copy(
                positionMs = positionMs,
                seekRevision = it.seekRevision + 1,
            )
        }
    }

    override suspend fun seekToItem(queueItemId: QueueItemId, positionMs: Long): Boolean {
        busy()
        seekItemCalls.incrementAndGet()
        mutable.update {
            it.copy(
                queueItemId = queueItemId,
                positionMs = positionMs,
                seekRevision = it.seekRevision + 1,
                itemTransitionRevision =
                    if (it.queueItemId == queueItemId) it.itemTransitionRevision
                    else it.itemTransitionRevision + 1,
            )
        }
        return true
    }

    override suspend fun setRepeatCurrentItem(enabled: Boolean) = Unit

    override suspend fun setPlaybackSpeed(speed: Float) {
        busy()
        speedCalls.incrementAndGet()
        mutable.update { it.copy(playbackSpeed = speed) }
    }

    /**
     * Test hook: the current item plays to its natural end. Media3 (paused at every item boundary)
     * reports one physical boundary and stops, leaving the successor to canonical room state.
     */
    fun endNaturally() {
        mutable.update {
            it.copy(
                positionMs = it.durationMs,
                playWhenReady = false,
                isPlaying = false,
                activityState = PlaybackActivityState.READY_PAUSED,
                itemBoundaryRevision = it.itemBoundaryRevision + 1,
                boundaryEndedQueueItemId = it.queueItemId,
                boundaryEndedPositionMs = it.durationMs,
                boundaryEndedDurationMs = it.durationMs,
            )
        }
    }

    /**
     * Test hook: Android takes the output away. [transient] models a resumable focus loss (a call,
     * another app); otherwise the interruption needs an explicit user rejoin (becoming noisy).
     */
    fun interrupt(transient: Boolean) {
        mutable.update {
            it.copy(
                participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                inhibitionReason =
                    if (transient) LocalPlaybackInhibitionReason.AUDIO_FOCUS
                    else LocalPlaybackInhibitionReason.BECOMING_NOISY,
                outputResumeBlocked = transient,
                automaticRejoinAllowed = transient,
                playWhenReady = false,
                isPlaying = false,
                activityState = PlaybackActivityState.READY_PAUSED,
            )
        }
    }

    /** Test hook: the platform releases a transient suppression. */
    fun releaseSuppression() {
        mutable.update { it.copy(outputResumeBlocked = false) }
    }
}
