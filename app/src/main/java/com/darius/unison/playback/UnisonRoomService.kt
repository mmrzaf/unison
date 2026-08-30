package com.darius.unison.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.darius.unison.R
import com.darius.unison.app.unisonContainer
import com.darius.unison.model.AppCommand
import com.darius.unison.room.RoomRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.darius.unison.util.DiagnosticCategory

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class UnisonRoomService : MediaSessionService() {
    private data class NotificationContent(
        val queueItemId: String?,
        val playWhenReady: Boolean,
    )

    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var playerAdapter: Media3PlayerAdapter
    private lateinit var mediaSession: MediaSession
    private lateinit var sessionPlayer: RoomMediaSessionPlayer
    private lateinit var runtime: RoomRuntime
    private var idleStopJob: Job? = null
    private var roomForegroundJob: Job? = null
    private var deferredNotificationUpdateJob: Job? = null
    private var lastNotificationUpdateElapsedMs: Long? = null
    private var lastForegroundStartAttemptElapsedMs: Long? = null
    private var lastRenderedNotificationContent: NotificationContent? = null
    private var notificationEnqueueCount = 0L
    private var notificationDeferredCount = 0L
    private var notificationDeduplicatedCount = 0L
    private var latestStartId: Int = 0

    private val mediaSessionCallback =
        object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                if (!controller.isTrusted) return MediaSession.ConnectionResult.reject()
                return MediaSession.ConnectionResult.accept(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                    MediaSessionCommandPolicy.SYSTEM_COMMANDS,
                )
            }
        }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val mediaNotificationProvider =
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.room_service_channel)
                .setNotificationId(NOTIFICATION_ID)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) }
        setMediaNotificationProvider(mediaNotificationProvider)

        playerAdapter =
            Media3PlayerAdapter(
                context = this,
                scope = runtimeScope,
                log = unisonContainer.diagnostics,
            )
        sessionPlayer =
            RoomMediaSessionPlayer(
                player = playerAdapter.exoPlayer,
                commandBus = unisonContainer.roomCommandBus,
                log = unisonContainer.diagnostics,
                systemArtworkData = UnisonMediaArtwork.createPng(),
            )
        mediaSession =
            MediaSession.Builder(this, sessionPlayer)
                .setCallback(mediaSessionCallback)
                .setSessionActivity(createContentIntent())
                .build()

        // MediaSessionService owns the only notification path. The notification is always the
        // real Media3 transport notification; Unison never posts a generic service placeholder.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)
        addSession(mediaSession)
        unisonContainer.diagnostics.info(
            TAG,
            DiagnosticCategory.PLAYBACK,
            "playback.service.started",
        )

        runtime = RoomRuntime(this, unisonContainer, playerAdapter, runtimeScope)
        roomForegroundJob = lifecycleScope.launch {
            unisonContainer.roomStore.structure
                .map { state -> state.sessionActive || state.hotspot != null }
                .distinctUntilChanged()
                .collect {
                    // Room/network activity is external to Media3's Player state. Force Media3 to
                    // reevaluate foreground ownership whenever that activity changes.
                    triggerNotificationUpdate()
                    scheduleStopWhenIdle()
                }
        }
        // Transport has one ordered ingress. Debounce/supersession is owned by RoomRuntime's
        // single transport-intent processor rather than manufactured through concurrent collectors.
        runtimeScope.launch {
            unisonContainer.roomCommandBus.transportFlow.collect(::submitRoomCommand)
        }
        runtimeScope.launch {
            unisonContainer.roomCommandBus.generalFlow.collect(::submitRoomCommand)
        }
    }

    private suspend fun submitRoomCommand(command: AppCommand) {
        try {
            runtime.submit(command).invokeOnCompletion { cause ->
                if (cause != null && cause !is CancellationException) {
                    reportRoomCommandFailure(command, cause)
                }
                unisonContainer.roomCommandBus.complete()
                scheduleStopWhenIdle()
            }
        } catch (cancelled: CancellationException) {
            unisonContainer.roomCommandBus.complete()
            scheduleStopWhenIdle()
            throw cancelled
        } catch (error: Exception) {
            reportRoomCommandFailure(command, error)
            unisonContainer.roomCommandBus.complete()
            scheduleStopWhenIdle()
        }
    }

    private fun reportRoomCommandFailure(command: AppCommand, error: Throwable) {
        unisonContainer.diagnostics.error(
            TAG,
            DiagnosticCategory.PLAYBACK,
            "playback.service.command_failed",
            attributes = mapOf("command.type" to command::class.simpleName),
            throwable = error,
        )
        unisonContainer.roomStore.update { state ->
            state.copy(errorMessage = "Unison could not complete that action")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        // A fresh command invalidates every idle decision made before this start ID existed.
        idleStopJob?.cancel()
        idleStopJob = null
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        mediaSession

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        val roomState = unisonContainer.roomStore.structure.value
        val roomRequiresForeground =
            RoomServicePolicy.requiresRoomForeground(
                sessionActive = roomState.sessionActive,
                hotspotActive = roomState.hotspot != null,
            )
        val effectiveForegroundRequired = startInForegroundRequired || roomRequiresForeground
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val foregroundRetryAllowed =
            lastForegroundStartAttemptElapsedMs?.let {
                nowElapsedMs - it >= FOREGROUND_START_RETRY_INTERVAL_MS
            } ?: true
        val urgentForegroundStart =
            effectiveForegroundRequired && !isPlaybackOngoing() && foregroundRetryAllowed
        val playerState = playerAdapter.state.value
        val currentContent =
            NotificationContent(
                queueItemId = playerState.queueItemId?.value,
                playWhenReady = playerState.playWhenReady,
            )
        val decision =
            MediaNotificationUpdatePolicy.decide(
                nowElapsedMs = nowElapsedMs,
                lastUpdateElapsedMs = lastNotificationUpdateElapsedMs,
                minimumIntervalMs = NOTIFICATION_UPDATE_INTERVAL_MS,
                urgentForegroundStart = urgentForegroundStart,
                renderedContentChanged = currentContent != lastRenderedNotificationContent,
            )
        if (decision.updateNow) {
            deferredNotificationUpdateJob?.cancel()
            deferredNotificationUpdateJob = null
            lastNotificationUpdateElapsedMs = nowElapsedMs
            lastRenderedNotificationContent = currentContent
            notificationEnqueueCount++
            if (urgentForegroundStart) lastForegroundStartAttemptElapsedMs = nowElapsedMs
            // Keep exactly one notification: Media3's player controls. Room/network ownership may
            // keep that same media notification foreground while paused; no second status
            // notification. Media-session controls do not use a separate general-notification
            // permission flow.
            super.onUpdateNotification(session, effectiveForegroundRequired)
            return
        }

        val delayMs = decision.delayMs
        if (delayMs == null) {
            notificationDeduplicatedCount++
            return
        }
        notificationDeferredCount++
        if (deferredNotificationUpdateJob?.isActive != true) {
            deferredNotificationUpdateJob = lifecycleScope.launch {
                delay(delayMs)
                deferredNotificationUpdateJob = null
                triggerNotificationUpdate()
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Removing Unison from recents is an explicit application exit. A room is a live shared
        // session, not background audio that survives after the app is closed. End it cleanly so a
        // healthy coordinator can remove this participant immediately and so a coordinator tells
        // listeners that the room itself ended.
        lifecycleScope.launch {
            try {
                if (::runtime.isInitialized && unisonContainer.roomStore.structure.value.sessionActive) {
                    runtime.handle(AppCommand.LeaveRoom)
                }
            } catch (error: Exception) {
                if (error !is CancellationException) {
                    unisonContainer.diagnostics.warn(
                        TAG,
                        DiagnosticCategory.ROOM,
                        "room.task_removed.leave_failed",
                        throwable = error,
                    )
                }
            } finally {
                stopSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        idleStopJob?.cancel()
        roomForegroundJob?.cancel()
        deferredNotificationUpdateJob?.cancel()
        if (::runtime.isInitialized) {
            unisonContainer.diagnostics.info(
                TAG,
                DiagnosticCategory.PLAYBACK,
                "playback.service.stopped",
                attributes = mapOf(
                    "notification.enqueued_count" to notificationEnqueueCount,
                    "notification.deferred_count" to notificationDeferredCount,
                    "notification.deduplicated_count" to notificationDeduplicatedCount,
                ),
            )
        }
        if (::runtime.isInitialized) runtime.close()
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::playerAdapter.isInitialized) playerAdapter.close()
        runtimeScope.cancel()
        lifecycleScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun scheduleStopWhenIdle() {
        // All service-lifecycle mutation is serialized on the main dispatcher. Runtime commands are
        // processed on a separate scope and may call this from any thread.
        lifecycleScope.launch {
            idleStopJob?.cancel()
            // Never create an unbound stop timer during onCreate, before Android has delivered the
            // first service start. Every delayed stop must be rejected automatically by a newer ID.
            val scheduledForStartId = latestStartId
            if (!RoomServicePolicy.canScheduleIdleStop(scheduledForStartId)) {
                idleStopJob = null
                return@launch
            }
            idleStopJob = lifecycleScope.launch {
                delay(IDLE_STOP_DELAY_MS)
                val room = unisonContainer.roomStore.structure.value
                val playerState = playerAdapter.state.value
                val playbackActive =
                    RoomServicePolicy.playbackActive(
                        queueItemPresent = playerState.queueItemId != null,
                        playWhenReady = playerState.playWhenReady,
                    )
                if (
                    RoomServicePolicy.shouldStop(
                        operationActive = room.operationActive,
                        hotspotActive = room.hotspot != null,
                        playbackActive = playbackActive,
                        commandOutstanding = unisonContainer.roomCommandBus.hasOutstandingCommands,
                    )
                ) {
                    stopSelfResult(scheduledForStartId)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.room_service_channel),
                        NotificationManager.IMPORTANCE_LOW,
                    )
                    .apply {
                        description = getString(R.string.room_service_channel_description)
                        setShowBadge(false)
                    }
            )
    }

    private fun createContentIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            } ?: Intent(this, com.darius.unison.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val TAG = "UnisonRoomService"
        private const val CHANNEL_ID = "unison_room"
        private const val NOTIFICATION_ID = 4102
        private const val IDLE_STOP_DELAY_MS = 1_000L
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 300L
        private const val FOREGROUND_START_RETRY_INTERVAL_MS = 1_000L
        /**
         * Refreshes Android's started-service ownership for each UI command. This is deliberately a
         * normal service start, not foreground promotion: MediaSessionService remains the sole
         * owner of the player notification. A newer start ID also prevents an older idle timeout
         * from stopping a command that arrived concurrently.
         */
        fun ensureStarted(context: Context): Boolean =
            runCatching {
                    context.startService(Intent(context, UnisonRoomService::class.java)) != null
                }
                .getOrDefault(false)
    }
}
