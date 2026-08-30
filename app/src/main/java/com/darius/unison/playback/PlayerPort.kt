package com.darius.unison.playback

import com.darius.unison.model.LocalPlaybackInhibitionReason
import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.TrackDescriptor
import java.io.File
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackActivityState {
    IDLE,
    PREPARING,
    BUFFERING,
    READY_PAUSED,
    READY_PLAYING,
    ENDED,
    FAILED,
}


enum class PlaybackPauseCause {
    USER_TRANSPORT,
    SCHEDULED_TRANSPORT,
    CANONICAL_RECONCILIATION,
    CANONICAL_QUEUE_EMPTY,
    WATCHDOG_RECONCILIATION,
    OUTPUT_INHIBITION,
    TRANSITION_CIRCUIT_BREAKER,
    CONNECTION_INTERRUPTION,
    SESSION_END,
    FAILURE_TEARDOWN,
}

enum class PlayerItemTransitionReason {
    AUTO,
    SEEK,
    REPEAT,
    PLAYLIST_CHANGED,
    UNKNOWN,
}

enum class AudioOutputRoute {
    BUILT_IN_SPEAKER,
    WIRED,
    USB,
    BLUETOOTH,
    HDMI,
    REMOTE,
    UNKNOWN,
}

data class PlaybackSample(
    val queueItemId: QueueItemId?,
    val positionMs: Long,
    val durationMs: Long,
    /** Local monotonic timestamp captured in the same main-thread operation as [positionMs]. */
    val sampledAtLocalNs: Long,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val activityState: PlaybackActivityState,
    val playbackSpeed: Float,
    val outputRoute: AudioOutputRoute,
    /** Increments for user, scheduled, and automatic seeks. */
    val seekRevision: Long,
)

data class PlayerState(
    val queueItemId: QueueItemId? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** True while playback is intended, including temporary buffering and seek settlement. */
    val playWhenReady: Boolean = false,
    /** True only while media is currently advancing and audible. */
    val isPlaying: Boolean = false,
    /** Local participation in room playback. Inhibited devices never influence room timing. */
    val participation: LocalPlaybackParticipation = LocalPlaybackParticipation.ACTIVE,
    val inhibitionReason: LocalPlaybackInhibitionReason? = null,
    /** True while Android/Media3 still reports a system suppression that makes resume unsafe. */
    val outputResumeBlocked: Boolean = false,
    val playbackSpeed: Float = 1f,
    val prepared: Boolean = false,
    val buffering: Boolean = false,
    val activityState: PlaybackActivityState = PlaybackActivityState.IDLE,
    val outputRoute: AudioOutputRoute = AudioOutputRoute.UNKNOWN,
    val ended: Boolean = false,
    val error: String? = null,
    val seekRevision: Long = 0,
    /** Increments for every Media3 current-item transition callback. */
    val itemTransitionRevision: Long = 0,
    /** Preserves Media3's transition origin for diagnostics only. */
    val itemTransitionReason: PlayerItemTransitionReason? = null,
    /** Increments once for each physical natural item boundary, including END_OF_MEDIA_ITEM. */
    val itemBoundaryRevision: Long = 0,
    /** Item that actually ended at [itemBoundaryRevision], independent of the newly selected item. */
    val boundaryEndedQueueItemId: QueueItemId? = null,
    val boundaryEndedPositionMs: Long = 0,
    val boundaryEndedDurationMs: Long = 0,
)

data class LocalPlayableItem(
    val queueItemId: QueueItemId,
    val track: TrackDescriptor,
    val file: File,
)

interface PlayerPort {
    val state: StateFlow<PlayerState>

    /** Always reads directly from the player thread. Never derive synchronization from [state]. */
    suspend fun samplePlayback(): PlaybackSample

    suspend fun setQueue(
        items: List<LocalPlayableItem>,
        currentQueueItemId: QueueItemId?,
        positionMs: Long,
    )

    /** Canonical/scheduled play. Must never clear local output inhibition. */
    suspend fun play(): Boolean

    /**
     * Atomically positions an inhibited player on the live room timeline and resumes local output.
     * A successful return means local participation is ACTIVE again; sync convergence is tracked
     * independently by the synchronization/peer-health layers.
     */
    suspend fun rejoinLivePlayback(queueItemId: QueueItemId, positionMs: Long): Boolean

    /** Clears device-local interruption state at a room/session boundary without starting audio. */
    suspend fun resetLocalPlaybackParticipation()

    suspend fun pause(cause: PlaybackPauseCause)

    suspend fun seekTo(positionMs: Long)

    suspend fun seekToItem(queueItemId: QueueItemId, positionMs: Long): Boolean

    suspend fun setRepeatCurrentItem(enabled: Boolean)

    suspend fun setPlaybackSpeed(speed: Float)
}
