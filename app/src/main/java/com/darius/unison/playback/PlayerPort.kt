package com.darius.unison.playback

import com.darius.unison.model.QueueItemId
import com.darius.unison.model.TrackDescriptor
import kotlinx.coroutines.flow.StateFlow
import java.io.File

enum class PlaybackActivityState {
    IDLE,
    PREPARING,
    BUFFERING,
    READY_PAUSED,
    READY_PLAYING,
    ENDED,
    FAILED,
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
    val playbackSpeed: Float = 1f,
    val prepared: Boolean = false,
    val buffering: Boolean = false,
    val activityState: PlaybackActivityState = PlaybackActivityState.IDLE,
    val outputRoute: AudioOutputRoute = AudioOutputRoute.UNKNOWN,
    val ended: Boolean = false,
    val error: String? = null,
    val seekRevision: Long = 0,
    val repeatTransitionRevision: Long = 0,
)

data class LocalPlayableItem(
    val queueItemId: QueueItemId,
    val track: TrackDescriptor,
    val file: File,
    val artworkFile: File? = null,
)

interface PlayerPort {
    val state: StateFlow<PlayerState>

    /** Always reads directly from the player thread. Never derive synchronization from [state]. */
    suspend fun samplePlayback(): PlaybackSample

    suspend fun setQueue(items: List<LocalPlayableItem>, currentQueueItemId: QueueItemId?, positionMs: Long)
    suspend fun play(): Boolean
    suspend fun pause()
    suspend fun seekTo(positionMs: Long)
    suspend fun seekToItem(queueItemId: QueueItemId, positionMs: Long): Boolean
    suspend fun setRepeatCurrentItem(enabled: Boolean)
    suspend fun setPlaybackSpeed(speed: Float)
}
