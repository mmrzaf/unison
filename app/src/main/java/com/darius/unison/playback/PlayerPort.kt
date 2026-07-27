package com.darius.unison.playback

import com.darius.unison.model.QueueItemId
import com.darius.unison.model.TrackDescriptor
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class PlayerState(
    val queueItemId: QueueItemId? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1f,
    val prepared: Boolean = false,
    val ended: Boolean = false,
    val error: String? = null,
)

data class LocalPlayableItem(
    val queueItemId: QueueItemId,
    val track: TrackDescriptor,
    val file: File,
)

interface PlayerPort {
    val state: StateFlow<PlayerState>
    suspend fun setQueue(items: List<LocalPlayableItem>, currentQueueItemId: QueueItemId?, positionMs: Long)
    suspend fun play(): Boolean
    suspend fun pause()
    suspend fun seekTo(positionMs: Long)
    suspend fun seekToItem(queueItemId: QueueItemId, positionMs: Long): Boolean
    suspend fun setPlaybackSpeed(speed: Float)
}
