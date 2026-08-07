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
import com.darius.unison.model.LocalPlaybackInhibitionReason
import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.QueueItemId
import com.darius.unison.util.DiagnosticCategory
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
    private var participation = LocalPlaybackParticipation.ACTIVE
    private var inhibitionReason: LocalPlaybackInhibitionReason? = null
    private var lastPlaybackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE
    private var lastNaturalTransitionNs = Long.MIN_VALUE
    private val expectedPlayIntentChanges = ExpectedPlayerIntentTracker()
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

            // Audio focus and "becoming noisy" are local device conditions, never room commands.
            // Once output is inhibited, Media3 is not allowed to make this device audible again
            // until RoomRuntime starts an explicit live-room rejoin.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                val nowNs = SystemClock.elapsedRealtimeNanos()
                val expectedSource = consumeExpectedPlayIntent(playWhenReady, nowNs)
                val reasonName = playWhenReadyReasonName(reason)
                log.debug(
                    TAG,
                    DiagnosticCategory.PLAYBACK,
                    "playback.media3.play_when_ready.changed",
                    attributes = mapOf(
                        "playback.play_when_ready" to playWhenReady,
                        "media3.play_when_ready_reason" to reason,
                        "media3.play_when_ready_reason_name" to reasonName,
                        "playback.expected_mutation_source" to expectedSource,
                        "playback.participation" to participation.name,
                        "media3.playback_suppression_reason" to lastPlaybackSuppressionReason,
                    ),
                )

                val explicitLocalReason = reason.toLocalInhibitionReason()
                val suppressionLocalReason =
                    lastPlaybackSuppressionReason.toLocalSuppressionReason()
                when {
                    !playWhenReady && explicitLocalReason != null -> {
                        expectedPlayIntentChanges.clear()
                        inhibitOutput(explicitLocalReason, reason, reasonName)
                    }

                    !playWhenReady &&
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG &&
                        suppressionLocalReason != null -> {
                        expectedPlayIntentChanges.clear()
                        inhibitOutput(suppressionLocalReason, reason, reasonName)
                    }

                    !playWhenReady &&
                        expectedSource == null &&
                        reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> {
                        expectedPlayIntentChanges.clear()
                        inhibitOutput(
                            LocalPlaybackInhibitionReason.SYSTEM_POLICY,
                            reason,
                            reasonName,
                        )
                    }

                    playWhenReady &&
                        participation == LocalPlaybackParticipation.OUTPUT_INHIBITED -> {
                        log.info(
                            TAG,
                            DiagnosticCategory.PLAYBACK,
                            "playback.output.autoresume_blocked",
                            attributes = mapOf(
                                "playback.inhibition_reason" to inhibitionReason?.name,
                                "media3.play_when_ready_reason" to reason,
                                "media3.play_when_ready_reason_name" to reasonName,
                            ),
                        )
                        setPlayWhenReadyInternal(false, "output_inhibition")
                    }
                }
                publish()
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                val previous = lastPlaybackSuppressionReason
                lastPlaybackSuppressionReason = playbackSuppressionReason
                log.debug(
                    TAG,
                    DiagnosticCategory.PLAYBACK,
                    "playback.media3.suppression.changed",
                    attributes = mapOf(
                        "media3.playback_suppression_from" to previous,
                        "media3.playback_suppression_to" to playbackSuppressionReason,
                        "media3.playback_suppression_name" to
                            playbackSuppressionReasonName(playbackSuppressionReason),
                        "playback.play_when_ready" to exoPlayer.playWhenReady,
                        "playback.participation" to participation.name,
                    ),
                )
                val localReason = playbackSuppressionReason.toLocalSuppressionReason()
                if (localReason != null && participation == LocalPlaybackParticipation.ACTIVE) {
                    expectedPlayIntentChanges.clear()
                    inhibitOutput(
                        localReason,
                        playbackSuppressionReason,
                        playbackSuppressionReasonName(playbackSuppressionReason),
                    )
                    if (exoPlayer.playWhenReady) {
                        setPlayWhenReadyInternal(false, "output_inhibition")
                    }
                    publish()
                } else if (
                    playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                        participation == LocalPlaybackParticipation.OUTPUT_INHIBITED
                ) {
                    log.debug(
                        TAG,
                        DiagnosticCategory.PLAYBACK,
                        "playback.output.suppression_cleared",
                        attributes = mapOf(
                            "playback.inhibition_reason" to inhibitionReason?.name,
                        ),
                    )
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                itemTransitionRevision++
                val transitionReason = reason.toPlayerItemTransitionReason()
                itemTransitionReason = transitionReason
                if (transitionReason == PlayerItemTransitionReason.AUTO) {
                    lastNaturalTransitionNs = SystemClock.elapsedRealtimeNanos()
                }
                log.info(
                    TAG, DiagnosticCategory.PLAYBACK, "playback.item.transitioned",
                    attributes = mapOf(
                        "queue.item_id" to mediaItem?.mediaId?.take(12),
                        "playback.transition_reason" to transitionReason.name,
                        "playback.transition_revision" to itemTransitionRevision,
                    ),
                )
                publish()
            }

            override fun onPlayerError(error: PlaybackException) {
                val message = buildString {
                    append("Playback failed: ").append(error.errorCodeName)
                    error.message?.takeIf(String::isNotBlank)?.let { append(" — ").append(it) }
                }
                log.error(
                    TAG, DiagnosticCategory.PLAYBACK, "playback.player.failed", message,
                    attributes = mapOf("media3.error_code" to error.errorCodeName), throwable = error,
                )
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
                log.info(
                    TAG, DiagnosticCategory.PLAYBACK, "playback.queue.cleared",
                    attributes = mapOf("queue.previous_size" to currentIds.size),
                )
                setPlayWhenReadyInternal(false, "queue_clear")
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
            log.debug(
                TAG,
                DiagnosticCategory.PLAYBACK,
                "playback.timeline.plan",
                attributes = mapOf(
                    "playback.timeline_action" to timelineAction.name,
                    "queue.previous_size" to currentIds.size,
                    "queue.size" to desired.size,
                    "queue.item_id" to targetId.take(12),
                    "playback.current_item_id" to originalCurrentId?.take(12),
                    "playback.position_ms" to originalPosition,
                    "playback.target_position_ms" to targetPosition,
                    "playback.play_when_ready" to wasPlaying,
                ),
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

                log.debug(
                    TAG, DiagnosticCategory.PLAYBACK, "playback.queue.reconciled",
                    attributes = mapOf(
                        "queue.size" to desired.size, "queue.item_id" to targetId.take(12),
                        "playback.seek_required" to needsSeek, "playback.prepare_required" to needsPrepare,
                    ),
                )
                if (needsSeek) exoPlayer.seekTo(targetIndex, targetPosition)
                if (needsPrepare) exoPlayer.prepare()
                setPlayWhenReadyInternal(wasPlaying, "queue_reconcile")
                publish()
                return@onMain
            }

            // A large shuffle or bulk mutation is bounded to one Media3 call instead of hundreds of
            // main-thread moves. Small edits stay incremental to preserve uninterrupted playback.
            if (timelineAction == PlaybackTimelinePlan.Action.REBUILD) {
                log.debug(
                    TAG, DiagnosticCategory.PLAYBACK, "playback.queue.rebuilt",
                    attributes = mapOf(
                        "queue.previous_size" to currentIds.size, "queue.size" to desired.size,
                        "queue.item_id" to targetId.take(12),
                    ),
                )
                exoPlayer.setMediaItems(desired, desiredIdList.indexOf(targetId), targetPosition)
                if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
                setPlayWhenReadyInternal(wasPlaying, "queue_reconcile")
                publish()
                return@onMain
            }

            // Keep a mirror of the mutable Media3 order. Only actual insertions/moves perform a
            // linear
            // search, so cost is O(n * changedItems) rather than O(n²) for every refresh.
            log.debug(
                TAG, DiagnosticCategory.PLAYBACK, "playback.queue.patched",
                attributes = mapOf(
                    "queue.previous_size" to currentIds.size, "queue.size" to desired.size,
                    "queue.item_id" to targetId.take(12),
                ),
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
            setPlayWhenReadyInternal(wasPlaying, "queue_reconcile")
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
        if (participation == LocalPlaybackParticipation.OUTPUT_INHIBITED) {
            log.info(
                TAG, DiagnosticCategory.PLAYBACK, "playback.command.output_deferred",
                attributes = mapOf(
                    "playback.inhibition_reason" to inhibitionReason?.name,
                    "queue.item_id" to exoPlayer.currentMediaItem?.mediaId?.take(12),
                ),
            )
            if (exoPlayer.playWhenReady) setPlayWhenReadyInternal(false, "output_inhibition")
            publish()
            return@onMain true
        }
        if (exoPlayer.mediaItemCount <= 0) {
            publishFailure("This song is not ready yet")
            return@onMain false
        }
        if (exoPlayer.currentMediaItem == null) {
            publishFailure("This song is not ready yet")
            return@onMain false
        }
        if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
        log.info(
            TAG, DiagnosticCategory.PLAYBACK, "playback.play.started",
            attributes = mapOf(
                "queue.item_id" to exoPlayer.currentMediaItem?.mediaId?.take(12),
                "playback.position_ms" to exoPlayer.currentPosition.coerceAtLeast(0),
                "playback.state" to stateName(exoPlayer.playbackState),
            ),
        )
        requestPlayInternal("canonical_play")
        publish()
        true
    }

    override suspend fun beginLocalRejoin() = onMain {
        if (participation != LocalPlaybackParticipation.OUTPUT_INHIBITED) return@onMain
        participation = LocalPlaybackParticipation.REJOINING
        log.info(
            TAG, DiagnosticCategory.PLAYBACK, "playback.rejoin.started",
            attributes = mapOf(
                "playback.inhibition_reason" to inhibitionReason?.name,
                "queue.item_id" to exoPlayer.currentMediaItem?.mediaId?.take(12),
            ),
        )
        publish()
    }

    override suspend fun completeLocalRejoin() = onMain {
        if (participation != LocalPlaybackParticipation.REJOINING) return@onMain
        participation = LocalPlaybackParticipation.ACTIVE
        val previousReason = inhibitionReason
        inhibitionReason = null
        log.info(
            TAG, DiagnosticCategory.PLAYBACK, "playback.rejoin.synchronized",
            attributes = mapOf(
                "playback.inhibition_reason" to previousReason?.name,
                "queue.item_id" to exoPlayer.currentMediaItem?.mediaId?.take(12),
            ),
        )
        publish()
    }

    override suspend fun pause(cause: PlaybackPauseCause) = onMain {
        val nowNs = SystemClock.elapsedRealtimeNanos()
        if (
            !PlaybackPausePolicy.shouldApply(
                cause = cause,
                playWhenReady = exoPlayer.playWhenReady,
                lastNaturalTransitionNs = lastNaturalTransitionNs,
                nowNs = nowNs,
            )
        ) {
            log.debug(
                TAG,
                DiagnosticCategory.PLAYBACK,
                "playback.pause.reconciliation_skipped",
                attributes = mapOf(
                    "playback.pause_cause" to cause.name,
                    "queue.item_id" to exoPlayer.currentMediaItem?.mediaId?.take(12),
                    "playback.ms_since_auto_transition" to
                        ((nowNs - lastNaturalTransitionNs).coerceAtLeast(0L) / 1_000_000L),
                ),
            )
            publish()
            return@onMain
        }
        log.info(
            TAG, DiagnosticCategory.PLAYBACK, "playback.pause.applied",
            attributes = mapOf(
                "queue.item_id" to exoPlayer.currentMediaItem?.mediaId?.take(12),
                "playback.position_ms" to exoPlayer.currentPosition.coerceAtLeast(0),
                "playback.pause_cause" to cause.name,
            ),
        )
        requestPauseInternal(cause.name.lowercase())
        publish()
    }

    override suspend fun seekTo(positionMs: Long) = onMain {
        if (exoPlayer.mediaItemCount <= 0) {
            publishFailure("This song is not ready yet")
            return@onMain
        }
        log.info(
            TAG, DiagnosticCategory.PLAYBACK, "playback.seek.applied",
            attributes = mapOf("playback.position_ms" to positionMs.coerceAtLeast(0)),
        )
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
        log.info(
            TAG, DiagnosticCategory.PLAYBACK, "playback.seek.applied",
            attributes = mapOf(
                "queue.item_id" to queueItemId.value.take(12), "queue.index" to index,
                "playback.position_ms" to positionMs.coerceAtLeast(0),
            ),
        )
        exoPlayer.seekTo(index, positionMs.coerceAtLeast(0))
        seekRevision++
        if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
        publish()
        true
    }

    override suspend fun setPlaybackSpeed(speed: Float) = onMain {
        require(speed.isFinite() && speed in SAFE_PLAYBACK_SPEED_RANGE) {
            "Invalid synchronization speed $speed"
        }
        if (kotlin.math.abs(exoPlayer.playbackParameters.speed - speed) <= SPEED_COMMAND_EPSILON) {
            return@onMain
        }
        exoPlayer.setPlaybackSpeed(speed)
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

    private fun inhibitOutput(
        reason: LocalPlaybackInhibitionReason,
        media3Reason: Int,
        media3ReasonName: String,
    ) {
        val changed =
            participation != LocalPlaybackParticipation.OUTPUT_INHIBITED ||
                inhibitionReason != reason
        participation = LocalPlaybackParticipation.OUTPUT_INHIBITED
        inhibitionReason = reason
        if (changed) {
            log.info(
                TAG, DiagnosticCategory.PLAYBACK, "playback.output.inhibited",
                attributes = mapOf(
                    "playback.inhibition_reason" to reason.name,
                    "media3.reason_code" to media3Reason,
                    "media3.reason_name" to media3ReasonName,
                    "queue.item_id" to exoPlayer.currentMediaItem?.mediaId?.take(12),
                ),
            )
        }
    }

    private fun Int.toLocalInhibitionReason(): LocalPlaybackInhibitionReason? =
        when (this) {
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ->
                LocalPlaybackInhibitionReason.AUDIO_FOCUS
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ->
                LocalPlaybackInhibitionReason.BECOMING_NOISY
            else -> null
        }

    private fun Int.toLocalSuppressionReason(): LocalPlaybackInhibitionReason? =
        when (this) {
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS ->
                LocalPlaybackInhibitionReason.AUDIO_FOCUS
            Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT ->
                LocalPlaybackInhibitionReason.UNSUITABLE_OUTPUT
            else -> null
        }

    private fun playWhenReadyReasonName(reason: Int): String =
        when (reason) {
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
            Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
            Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
            Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG -> "SUPPRESSED_TOO_LONG"
            else -> "UNKNOWN_$reason"
        }

    private fun playbackSuppressionReasonName(reason: Int): String =
        when (reason) {
            Player.PLAYBACK_SUPPRESSION_REASON_NONE -> "NONE"
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS ->
                "TRANSIENT_AUDIO_FOCUS_LOSS"
            Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT -> "UNSUITABLE_AUDIO_OUTPUT"
            Player.PLAYBACK_SUPPRESSION_REASON_SCRUBBING -> "SCRUBBING"
            else -> "UNKNOWN_$reason"
        }

    private fun setPlayWhenReadyInternal(value: Boolean, source: String) {
        if (exoPlayer.playWhenReady == value) return
        expectedPlayIntentChanges.expect(value, source, SystemClock.elapsedRealtimeNanos())
        exoPlayer.playWhenReady = value
    }

    private fun requestPlayInternal(source: String) {
        if (exoPlayer.playWhenReady) return
        expectedPlayIntentChanges.expect(true, source, SystemClock.elapsedRealtimeNanos())
        exoPlayer.play()
    }

    private fun requestPauseInternal(source: String) {
        if (!exoPlayer.playWhenReady) return
        expectedPlayIntentChanges.expect(false, source, SystemClock.elapsedRealtimeNanos())
        exoPlayer.pause()
    }

    private fun consumeExpectedPlayIntent(value: Boolean, nowNs: Long): String? =
        expectedPlayIntentChanges.consume(value, nowNs)

    private fun publishFailure(message: String) {
        log.error(TAG, DiagnosticCategory.PLAYBACK, "playback.request.failed", message)
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
                participation = participation,
                inhibitionReason = inhibitionReason,
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
        log.info(
            TAG, DiagnosticCategory.PLAYBACK, "playback.state.changed",
            attributes = mapOf(
                "queue.item_id" to itemId?.take(12), "playback.state" to stateName(state),
                "playback.play_when_ready" to player.playWhenReady, "playback.is_playing" to playing,
                "playback.position_ms" to player.currentPosition.coerceAtLeast(0),
            ),
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
        private val SAFE_PLAYBACK_SPEED_RANGE = 0.95f..1.05f
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
