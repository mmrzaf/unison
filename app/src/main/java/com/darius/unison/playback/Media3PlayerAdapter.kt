package com.darius.unison.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
) : PlayerPort, AutoCloseable {
    val exoPlayer: ExoPlayer =
        ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    private var ticker: Job? = null
    private var lastLoggedPlaybackState: Int? = null
    private var lastLoggedItemId: String? = null
    private var lastLoggedIsPlaying: Boolean? = null
    private var seekRevision = 0L
    private var itemTransitionRevision = 0L
    private var itemTransitionReason: PlayerItemTransitionReason? = null
    private var locallySuppressed = false
    @Volatile private var outputRoute = AudioOutputRoute.UNKNOWN

    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) =
                refreshOutputRoute()

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) =
                refreshOutputRoute()
        }

    private val listener =
        object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                publish()
                logStateChanges(player)
            }

            // Audio focus and "becoming noisy" are local device conditions. Record a local safety
            // pause, but never convert it into a canonical room Pause command for every peer.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                locallySuppressed =
                    !playWhenReady &&
                        (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)
                if (locallySuppressed) log.i(TAG, "Local playback suppressed reason=$reason")
                publish()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                itemTransitionRevision++
                itemTransitionReason = reason.toPlayerItemTransitionReason()
                log.i(
                    TAG,
                    "Item transition item=${mediaItem?.mediaId?.take(8)} " +
                        "reason=$itemTransitionReason revision=$itemTransitionRevision",
                )
                publish()
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
        refreshOutputRoute()
        audioManager?.registerAudioDeviceCallback(
            audioDeviceCallback,
            Handler(Looper.getMainLooper()),
        )
        ticker =
            scope.launch(Dispatchers.Main.immediate) {
                while (isActive) {
                    publish()
                    delay(if (exoPlayer.isPlaying) 200 else 750)
                }
            }
    }

    override suspend fun samplePlayback(): PlaybackSample = onMain {
        val sampledAtNs = SystemClock.elapsedRealtimeNanos()
        PlaybackSample(
            queueItemId =
                exoPlayer.currentMediaItem?.mediaId?.takeIf(String::isNotBlank)?.let(::QueueItemId),
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
            durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L,
            sampledAtLocalNs = sampledAtNs,
            playWhenReady = exoPlayer.playWhenReady,
            isPlaying = exoPlayer.isPlaying,
            activityState = activityState(),
            playbackSpeed = exoPlayer.playbackParameters.speed,
            outputRoute = outputRoute,
            seekRevision = seekRevision,
        )
    }

    override suspend fun setQueue(
        items: List<LocalPlayableItem>,
        currentQueueItemId: QueueItemId?,
        positionMs: Long,
    ) {
        // Building metadata and file URIs is pure work. Keep it off the player/main thread so a
        // queue refresh cannot delay audio callbacks or Compose input handling.
        val desired = withContext(Dispatchers.Default) { items.map(::toMediaItem) }
        onMain {
            val currentIds =
                (0 until exoPlayer.mediaItemCount).map { exoPlayer.getMediaItemAt(it).mediaId }
            if (desired.isEmpty()) {
                val action =
                    PlaybackTimelinePlan.decide(
                        currentIds = currentIds,
                        desiredIds = emptyList(),
                        currentId = exoPlayer.currentMediaItem?.mediaId,
                        targetId = null,
                        currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0),
                        targetPositionMs = 0,
                        playerIdle = exoPlayer.playbackState == Player.STATE_IDLE,
                        playWhenReady = exoPlayer.playWhenReady,
                    )
                if (action == PlaybackTimelinePlan.Action.NO_OP) return@onMain
                log.i(TAG, "Clear queue")
                locallySuppressed = false
                exoPlayer.playWhenReady = false
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                publish()
                return@onMain
            }

            val wasPlaying = exoPlayer.playWhenReady
            val originalCurrentId = exoPlayer.currentMediaItem?.mediaId
            val originalPosition = exoPlayer.currentPosition.coerceAtLeast(0)
            val desiredIdList = desired.map { it.mediaId }
            val desiredIds = desiredIdList.toHashSet()
            val requestedId = currentQueueItemId?.value
            val targetId =
                requestedId?.takeIf(desiredIds::contains)
                    ?: originalCurrentId?.takeIf(desiredIds::contains)
                    ?: desired.first().mediaId
            val targetPosition =
                if (originalCurrentId == targetId) originalPosition else positionMs.coerceAtLeast(0)
            val timelineAction =
                PlaybackTimelinePlan.decide(
                    currentIds = currentIds,
                    desiredIds = desiredIdList,
                    currentId = originalCurrentId,
                    targetId = targetId,
                    currentPositionMs = originalPosition,
                    targetPositionMs = targetPosition,
                    playerIdle = exoPlayer.playbackState == Player.STATE_IDLE,
                    playWhenReady = wasPlaying,
                )
            if (timelineAction == PlaybackTimelinePlan.Action.NO_OP) return@onMain

            // Metadata/availability refreshes are the common path. When order is unchanged,
            // reconcile
            // in O(n) without searching the whole Media3 timeline once per item.
            if (timelineAction == PlaybackTimelinePlan.Action.RECONCILE) {
                // Queue-item IDs are immutable and content-addressed. An unchanged ID order means
                // Media3 already owns the correct sources and metadata; replacing equal logical
                // items would only churn the player timeline and notification.
                val targetIndex = desiredIdList.indexOf(targetId)
                val needsSeek =
                    exoPlayer.currentMediaItem?.mediaId != targetId ||
                        kotlin.math.abs(exoPlayer.currentPosition - targetPosition) > 250
                val needsPrepare = exoPlayer.playbackState == Player.STATE_IDLE
                if (!needsSeek && !needsPrepare) return@onMain

                log.i(
                    TAG,
                    "Reconcile queue items=${desired.size} current=${targetId.take(8)} " +
                        "seek=$needsSeek prepare=$needsPrepare",
                )
                if (needsSeek) exoPlayer.seekTo(targetIndex, targetPosition)
                if (needsPrepare) exoPlayer.prepare()
                exoPlayer.playWhenReady = wasPlaying
                publish()
                return@onMain
            }

            // A large shuffle or bulk mutation is bounded to one Media3 call instead of hundreds of
            // main-thread moves. Small edits stay incremental to preserve uninterrupted playback.
            if (timelineAction == PlaybackTimelinePlan.Action.REBUILD) {
                log.i(
                    TAG,
                    "Rebuild queue from=${currentIds.size} to=${desired.size} current=${targetId.take(8)}",
                )
                exoPlayer.setMediaItems(desired, desiredIdList.indexOf(targetId), targetPosition)
                if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
                exoPlayer.playWhenReady = wasPlaying
                publish()
                return@onMain
            }

            // Keep a mirror of the mutable Media3 order. Only actual insertions/moves perform a
            // linear
            // search, so cost is O(n * changedItems) rather than O(n²) for every refresh.
            log.i(
                TAG,
                "Patch queue from=${currentIds.size} to=${desired.size} current=${targetId.take(8)}",
            )
            val workingIds = currentIds.toMutableList()
            for (index in workingIds.lastIndex downTo 0) {
                if (workingIds[index] !in desiredIds) {
                    exoPlayer.removeMediaItem(index)
                    workingIds.removeAt(index)
                }
            }
            desired.forEachIndexed { targetIndex, mediaItem ->
                val currentAtTarget = workingIds.getOrNull(targetIndex)
                if (currentAtTarget != mediaItem.mediaId) {
                    val existingIndex = workingIds.indexOf(mediaItem.mediaId)
                    if (existingIndex >= 0) {
                        exoPlayer.moveMediaItem(existingIndex, targetIndex)
                        workingIds.add(targetIndex, workingIds.removeAt(existingIndex))
                    } else {
                        exoPlayer.addMediaItem(
                            targetIndex.coerceAtMost(exoPlayer.mediaItemCount),
                            mediaItem,
                        )
                        workingIds.add(targetIndex, mediaItem.mediaId)
                    }
                }
            }

            val currentAfterDiff = exoPlayer.currentMediaItem?.mediaId
            val targetIndex =
                (0 until exoPlayer.mediaItemCount).first {
                    exoPlayer.getMediaItemAt(it).mediaId == targetId
                }

            val correctedTargetPosition =
                when {
                    currentAfterDiff == targetId && originalCurrentId == targetId ->
                        originalPosition
                    else -> targetPosition
                }
            if (
                currentAfterDiff != targetId ||
                    kotlin.math.abs(exoPlayer.currentPosition - correctedTargetPosition) > 250
            ) {
                exoPlayer.seekTo(targetIndex, correctedTargetPosition)
            }
            if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
            exoPlayer.playWhenReady = wasPlaying
            publish()
        }
    }

    private fun toMediaItem(item: LocalPlayableItem): MediaItem {
        val builder =
            MediaItem.Builder()
                .setMediaId(item.queueItemId.value)
                .setUri(Uri.fromFile(item.file))
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(item.track.displayTitle)
                        .setArtist(item.track.artist)
                        .setAlbumTitle(item.track.album)
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
        locallySuppressed = false
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
            }",
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
        val index =
            (0 until exoPlayer.mediaItemCount).firstOrNull {
                exoPlayer.getMediaItemAt(it).mediaId == queueItemId.value
            }
        if (index == null) {
            publishFailure("This song is not ready yet")
            return@onMain false
        }
        log.i(
            TAG,
            "Seek item=${queueItemId.value.take(8)} index=$index positionMs=${positionMs.coerceAtLeast(0)}",
        )
        exoPlayer.seekTo(index, positionMs.coerceAtLeast(0))
        seekRevision++
        if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
        publish()
        true
    }

    override suspend fun setPlaybackSpeed(speed: Float) = onMain {
        val target = speed.coerceIn(MINIMUM_SYNC_SPEED, MAXIMUM_SYNC_SPEED)
        if (kotlin.math.abs(exoPlayer.playbackParameters.speed - target) <= SPEED_COMMAND_EPSILON) {
            return@onMain
        }
        exoPlayer.setPlaybackSpeed(target)
        publish()
    }

    override suspend fun setRepeatCurrentItem(enabled: Boolean) = onMain {
        exoPlayer.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        publish()
    }

    private fun Int.toPlayerItemTransitionReason(): PlayerItemTransitionReason =
        when (this) {
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> PlayerItemTransitionReason.AUTO
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> PlayerItemTransitionReason.SEEK
            Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> PlayerItemTransitionReason.REPEAT
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED ->
                PlayerItemTransitionReason.PLAYLIST_CHANGED
            else -> PlayerItemTransitionReason.UNKNOWN
        }

    private fun publishFailure(message: String) {
        log.e(TAG, message)
        publish(message)
    }

    private fun publish(error: String? = null) {
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
        val next =
            PlayerState(
                queueItemId =
                    exoPlayer.currentMediaItem
                        ?.mediaId
                        ?.takeIf(String::isNotBlank)
                        ?.let(::QueueItemId),
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
                durationMs = duration,
                playWhenReady = exoPlayer.playWhenReady,
                isPlaying = exoPlayer.isPlaying,
                locallySuppressed = locallySuppressed,
                playbackSpeed = exoPlayer.playbackParameters.speed,
                prepared = exoPlayer.playbackState == Player.STATE_READY,
                buffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                activityState = activityState(error),
                outputRoute = outputRoute,
                ended = exoPlayer.playbackState == Player.STATE_ENDED,
                error = error,
                seekRevision = seekRevision,
                itemTransitionRevision = itemTransitionRevision,
                itemTransitionReason = itemTransitionReason,
            )
        if (_state.value != next) _state.value = next
    }

    private fun activityState(error: String? = null): PlaybackActivityState {
        if (error != null || exoPlayer.playerError != null) return PlaybackActivityState.FAILED
        return when (exoPlayer.playbackState) {
            Player.STATE_IDLE ->
                if (exoPlayer.mediaItemCount > 0) {
                    PlaybackActivityState.PREPARING
                } else {
                    PlaybackActivityState.IDLE
                }
            Player.STATE_BUFFERING -> PlaybackActivityState.BUFFERING
            Player.STATE_READY ->
                if (exoPlayer.isPlaying) {
                    PlaybackActivityState.READY_PLAYING
                } else {
                    PlaybackActivityState.READY_PAUSED
                }
            Player.STATE_ENDED -> PlaybackActivityState.ENDED
            else -> PlaybackActivityState.IDLE
        }
    }

    private fun refreshOutputRoute() {
        val detected = detectOutputRoute()
        if (detected == outputRoute) return
        outputRoute = detected
        if (Looper.myLooper() == Looper.getMainLooper()) publish()
    }

    private fun detectOutputRoute(): AudioOutputRoute {
        val devices =
            runCatching {
                    when {
                        audioManager == null -> emptyList()
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                            val mediaAttributes =
                                android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .setContentType(
                                        android.media.AudioAttributes.CONTENT_TYPE_MUSIC
                                    )
                                    .build()
                            audioManager.getAudioDevicesForAttributes(mediaAttributes)
                        }
                        else -> audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
                    }
                }
                .getOrDefault(emptyList())
        return when {
            devices.any { it.type in BLUETOOTH_DEVICE_TYPES } -> AudioOutputRoute.BLUETOOTH
            devices.any { it.type in USB_DEVICE_TYPES } -> AudioOutputRoute.USB
            devices.any { it.type in WIRED_DEVICE_TYPES } -> AudioOutputRoute.WIRED
            devices.any { it.type in HDMI_DEVICE_TYPES } -> AudioOutputRoute.HDMI
            devices.any { it.type in REMOTE_DEVICE_TYPES } -> AudioOutputRoute.REMOTE
            devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } ->
                AudioOutputRoute.BUILT_IN_SPEAKER
            else -> AudioOutputRoute.UNKNOWN
        }
    }

    private fun logStateChanges(player: Player) {
        val itemId = player.currentMediaItem?.mediaId
        val state = player.playbackState
        val playing = player.isPlaying
        if (
            itemId == lastLoggedItemId &&
                state == lastLoggedPlaybackState &&
                playing == lastLoggedIsPlaying
        )
            return
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

    private fun stateName(state: Int): String =
        when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> state.toString()
        }

    private suspend fun <T> onMain(block: () -> T): T =
        withContext(Dispatchers.Main.immediate) { block() }

    override fun close() {
        ticker?.cancel()
        runCatching { audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback) }
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }

    private companion object {
        const val TAG = "UnisonPlayback"
        const val MINIMUM_SYNC_SPEED = 0.995f
        const val MAXIMUM_SYNC_SPEED = 1.005f
        const val SPEED_COMMAND_EPSILON = 0.00001f
        val BLUETOOTH_DEVICE_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            add(AudioDeviceInfo.TYPE_HEARING_AID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }
        val USB_DEVICE_TYPES =
            setOf(
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
            )
        val WIRED_DEVICE_TYPES =
            setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_LINE_ANALOG,
                AudioDeviceInfo.TYPE_LINE_DIGITAL,
            )
        val HDMI_DEVICE_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_HDMI)
            add(AudioDeviceInfo.TYPE_HDMI_ARC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_HDMI_EARC)
            }
        }
        val REMOTE_DEVICE_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_DOCK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_REMOTE_SUBMIX)
            }
        }
    }
}
