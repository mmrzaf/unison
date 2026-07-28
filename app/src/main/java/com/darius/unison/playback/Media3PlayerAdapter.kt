package com.darius.unison.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.darius.unison.model.QueueItemId
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Media3PlayerAdapter(
    context: Context,
    private val scope: CoroutineScope,
    private val log: DiagnosticLog,
    private val onLocalInterruption: () -> Unit = {},
) : PlayerPort, AutoCloseable {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    private var ticker: Job? = null
    private var lastLoggedPlaybackState: Int? = null
    private var lastLoggedItemId: String? = null
    private var lastLoggedIsPlaying: Boolean? = null
    private var seekRevision = 0L
    private var repeatTransitionRevision = 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
            logStateChanges(player)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && (
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                    )
            ) {
                log.i(TAG, "Local audio interruption requested a room pause reason=$reason")
                onLocalInterruption()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                repeatTransitionRevision++
                publish()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val message = buildString {
                append("Playback failed: ").append(error.errorCodeName)
                error.message?.takeIf(String::isNotBlank)?.let { append(" — ").append(it) }
            }
            log.e(TAG, message, error)
            publish("This song could not be played")
        }
    }

    init {
        exoPlayer.addListener(listener)
        ticker = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                publish()
                delay(if (exoPlayer.isPlaying) 200 else 750)
            }
        }
    }

    override suspend fun setQueue(
        items: List<LocalPlayableItem>,
        currentQueueItemId: QueueItemId?,
        positionMs: Long,
    ) = onMain {
        val desired = items.map(::toMediaItem)
        log.i(
            TAG,
            "Set queue items=${desired.size} current=${currentQueueItemId?.value?.take(8)} " +
                "positionMs=${positionMs.coerceAtLeast(0)} files=${items.count { it.file.isFile && it.file.canRead() }}",
        )
        if (desired.isEmpty()) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            publish()
            return@onMain
        }

        val wasPlaying = exoPlayer.playWhenReady
        val originalCurrentId = exoPlayer.currentMediaItem?.mediaId
        val originalPosition = exoPlayer.currentPosition.coerceAtLeast(0)
        val desiredIds = desired.mapTo(hashSetOf()) { it.mediaId }

        // Diff the timeline in place. Replacing the whole playlist whenever another track finishes
        // downloading would re-buffer the current song and create audible interruptions.
        for (index in exoPlayer.mediaItemCount - 1 downTo 0) {
            if (exoPlayer.getMediaItemAt(index).mediaId !in desiredIds) exoPlayer.removeMediaItem(index)
        }
        desired.forEachIndexed { targetIndex, mediaItem ->
            val existingIndex = (0 until exoPlayer.mediaItemCount)
                .firstOrNull { exoPlayer.getMediaItemAt(it).mediaId == mediaItem.mediaId }
            when {
                existingIndex == null -> exoPlayer.addMediaItem(
                    targetIndex.coerceAtMost(exoPlayer.mediaItemCount),
                    mediaItem
                )

                else -> {
                    // Queue items often become playable before artwork extraction finishes. Replace
                    // compatible items when metadata changes; Media3 keeps the same media source and
                    // continues current playback without rebuilding the whole timeline.
                    if (exoPlayer.getMediaItemAt(existingIndex) != mediaItem) {
                        exoPlayer.replaceMediaItem(existingIndex, mediaItem)
                    }
                    if (existingIndex != targetIndex) exoPlayer.moveMediaItem(existingIndex, targetIndex)
                }
            }
        }

        val requestedId = currentQueueItemId?.value
        val currentAfterDiff = exoPlayer.currentMediaItem?.mediaId
        val targetId = requestedId?.takeIf { id -> desired.any { it.mediaId == id } }
            ?: originalCurrentId?.takeIf { id -> desired.any { it.mediaId == id } }
            ?: desired.first().mediaId
        val targetIndex = (0 until exoPlayer.mediaItemCount)
            .first { exoPlayer.getMediaItemAt(it).mediaId == targetId }

        val targetPosition = when {
            currentAfterDiff == targetId && originalCurrentId == targetId -> originalPosition
            else -> positionMs.coerceAtLeast(0)
        }
        if (currentAfterDiff != targetId || kotlin.math.abs(exoPlayer.currentPosition - targetPosition) > 250) {
            exoPlayer.seekTo(targetIndex, targetPosition)
        }
        if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
        exoPlayer.playWhenReady = wasPlaying
        publish()
    }

    private fun toMediaItem(item: LocalPlayableItem): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(item.queueItemId.value)
            .setUri(Uri.fromFile(item.file))
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(item.track.displayTitle)
                    .setArtist(item.track.artist)
                    .setAlbumTitle(item.track.album)
                    .setArtworkUri(item.artworkFile?.let(Uri::fromFile))
                    .build()
            )
        item.track.mimeType?.takeIf { it.isNotBlank() }?.let(builder::setMimeType)
        return builder.build()
    }

    override suspend fun play(): Boolean = onMain {
        if (exoPlayer.mediaItemCount <= 0) {
            publishFailure("This song is not ready yet")
            return@onMain false
        }
        if (exoPlayer.currentMediaItem == null) {
            publishFailure("This song is not ready yet")
            return@onMain false
        }
        if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
        log.i(
            TAG,
            "Play item=${exoPlayer.currentMediaItem?.mediaId?.take(8)} " +
                "positionMs=${exoPlayer.currentPosition.coerceAtLeast(0)} state=${stateName(exoPlayer.playbackState)}",
        )
        exoPlayer.play()
        publish()
        true
    }

    override suspend fun pause() = onMain {
        log.i(
            TAG,
            "Pause item=${exoPlayer.currentMediaItem?.mediaId?.take(8)} positionMs=${
                exoPlayer.currentPosition.coerceAtLeast(0)
            }"
        )
        exoPlayer.pause()
        publish()
    }

    override suspend fun seekTo(positionMs: Long) = onMain {
        if (exoPlayer.mediaItemCount <= 0) {
            publishFailure("This song is not ready yet")
            return@onMain
        }
        log.i(TAG, "Seek current positionMs=${positionMs.coerceAtLeast(0)}")
        exoPlayer.seekTo(positionMs.coerceAtLeast(0))
        seekRevision++
        publish()
    }

    override suspend fun seekToItem(queueItemId: QueueItemId, positionMs: Long): Boolean = onMain {
        val index = (0 until exoPlayer.mediaItemCount)
            .firstOrNull { exoPlayer.getMediaItemAt(it).mediaId == queueItemId.value }
        if (index == null) {
            publishFailure("This song is not ready yet")
            return@onMain false
        }
        log.i(TAG, "Seek item=${queueItemId.value.take(8)} index=$index positionMs=${positionMs.coerceAtLeast(0)}")
        exoPlayer.seekTo(index, positionMs.coerceAtLeast(0))
        seekRevision++
        if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
        publish()
        true
    }

    override suspend fun setPlaybackSpeed(speed: Float) = onMain {
        exoPlayer.setPlaybackSpeed(speed.coerceIn(0.95f, 1.05f))
        publish()
    }

    override suspend fun setRepeatCurrentItem(enabled: Boolean) = onMain {
        exoPlayer.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        publish()
    }

    private fun publishFailure(message: String) {
        log.e(TAG, message)
        publish(message)
    }

    private fun publish(error: String? = null) {
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
        _state.value = PlayerState(
            queueItemId = exoPlayer.currentMediaItem?.mediaId?.takeIf(String::isNotBlank)?.let(::QueueItemId),
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            playWhenReady = exoPlayer.playWhenReady,
            isPlaying = exoPlayer.isPlaying,
            playbackSpeed = exoPlayer.playbackParameters.speed,
            prepared = exoPlayer.playbackState == Player.STATE_READY,
            ended = exoPlayer.playbackState == Player.STATE_ENDED,
            error = error,
            seekRevision = seekRevision,
            repeatTransitionRevision = repeatTransitionRevision,
        )
    }

    private fun logStateChanges(player: Player) {
        val itemId = player.currentMediaItem?.mediaId
        val state = player.playbackState
        val playing = player.isPlaying
        if (itemId == lastLoggedItemId && state == lastLoggedPlaybackState && playing == lastLoggedIsPlaying) return
        lastLoggedItemId = itemId
        lastLoggedPlaybackState = state
        lastLoggedIsPlaying = playing
        log.i(
            TAG,
            "State item=${itemId?.take(8)} playback=${stateName(state)} " +
                "playWhenReady=${player.playWhenReady} isPlaying=$playing positionMs=${
                    player.currentPosition.coerceAtLeast(
                        0
                    )
                }",
        )
    }

    private fun stateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> state.toString()
    }

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main.immediate) { block() }

    override fun close() {
        ticker?.cancel()
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }

    private companion object {
        const val TAG = "UnisonPlayback"
    }
}
