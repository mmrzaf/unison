package com.darius.unison.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.darius.unison.R
import com.darius.unison.app.unisonContainer
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

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
private fun Player.Commands.Builder.addAllReadOnlyCommands(): Player.Commands.Builder = addAll(
    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
    Player.COMMAND_GET_TIMELINE,
    Player.COMMAND_GET_METADATA,
    Player.COMMAND_GET_AUDIO_ATTRIBUTES,
    Player.COMMAND_GET_VOLUME,
    Player.COMMAND_GET_DEVICE_VOLUME,
    Player.COMMAND_GET_TEXT,
    Player.COMMAND_GET_TRACKS,
)

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class UnisonRoomService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var playerAdapter: Media3PlayerAdapter
    private lateinit var mediaSession: MediaSession
    private lateinit var sessionPlayer: RoomMediaSessionPlayer
    private lateinit var runtime: RoomRuntime
    private var idleStopJob: Job? = null
    private var roomForegroundJob: Job? = null

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (!controller.isTrusted) return MediaSession.ConnectionResult.reject()
            return MediaSession.ConnectionResult.accept(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                SYSTEM_MEDIA_COMMANDS,
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val mediaNotificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.room_service_channel)
            .setNotificationId(NOTIFICATION_ID)
            .build()
            .apply { setSmallIcon(R.drawable.ic_notification) }
        setMediaNotificationProvider(mediaNotificationProvider)

        playerAdapter = Media3PlayerAdapter(
            context = this,
            scope = serviceScope,
            log = unisonContainer.diagnostics,
            onLocalInterruption = { unisonContainer.roomCommandBus.trySend(com.darius.unison.model.AppCommand.Pause) },
        )
        sessionPlayer = RoomMediaSessionPlayer(
            player = playerAdapter.exoPlayer,
            commandBus = unisonContainer.roomCommandBus,
            log = unisonContainer.diagnostics,
        )
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setCallback(mediaSessionCallback)
            .setSessionActivity(createContentIntent())
            .build()

        // Unison's in-app UI submits room commands directly instead of binding a MediaController.
        // Register the session explicitly so MediaSessionService creates its internal notification
        // controller and publishes Android media controls even when no external controller binds.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS)
        addSession(mediaSession)
        triggerNotificationUpdate()
        unisonContainer.diagnostics.i(TAG, "Media session registered for system controls")

        runtime = RoomRuntime(this, unisonContainer, playerAdapter, serviceScope)
        roomForegroundJob = serviceScope.launch(Dispatchers.Main.immediate) {
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
        serviceScope.launch {
            unisonContainer.roomCommandBus.flow.collect { command ->
                try {
                    runtime.handle(command)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    unisonContainer.diagnostics.e(TAG, "Room command failed: ${command::class.simpleName}", error)
                    unisonContainer.roomStore.update { state ->
                        state.copy(errorMessage = "Unison could not complete that action")
                    }
                }
                scheduleStopWhenIdle()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService() request creates a fresh promotion deadline, including
        // requests delivered to an already-created service. Satisfy that contract here, before
        // Media3, networking, or command processing can suspend or demote the service.
        startAsForeground()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val roomState = unisonContainer.roomStore.state.value
        val roomRequiresForeground = roomState.sessionActive || roomState.hotspot != null
        // Media3 normally moves a paused player back to a background service. An active offline
        // room still needs sockets, discovery, transfers, and the shared clock, so force the same
        // MediaStyle notification to remain foreground until the room ends.
        super.onUpdateNotification(
            session,
            startInForegroundRequired || roomRequiresForeground ||
                (::playerAdapter.isInitialized && playerAdapter.state.value.playWhenReady),
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val playing = ::playerAdapter.isInitialized && playerAdapter.state.value.playWhenReady
        val room = unisonContainer.roomStore.state.value
        if (!room.sessionActive && room.hotspot == null && !playing) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        idleStopJob?.cancel()
        roomForegroundJob?.cancel()
        if (::runtime.isInitialized) runtime.close()
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::playerAdapter.isInitialized) playerAdapter.close()
        serviceScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun scheduleStopWhenIdle() {
        idleStopJob?.cancel()
        idleStopJob = serviceScope.launch {
            delay(1_000)
            val room = unisonContainer.roomStore.state.value
            val playing = ::playerAdapter.isInitialized && playerAdapter.state.value.playWhenReady
            if (!room.operationActive && room.hotspot == null && !playing) stopSelf()
        }
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.room_service_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.room_service_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun createContentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: Intent(this, com.darius.unison.ui.MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.room_service_running))
            .setOngoing(true)
            .setContentIntent(createContentIntent())
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
    }

    companion object {
        private const val TAG = "UnisonRoomService"
        private const val CHANNEL_ID = "unison_room"
        private const val NOTIFICATION_ID = 4102
        private val SYSTEM_MEDIA_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAllReadOnlyCommands()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
            .build()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, UnisonRoomService::class.java))
        }
    }
}
