package com.darius.unison.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.darius.unison.app.RoomCommandBus
import com.darius.unison.model.AppCommand
import com.darius.unison.util.DiagnosticLog

/**
 * Player facade exposed to Android's MediaSession.
 *
 * The ExoPlayer delegate remains the source of playback state and metadata, while every transport
 * request from notifications, the lock screen, Bluetooth controls, Android Auto, or other system
 * controllers is translated into a room command. This prevents a system controller from changing
 * only this phone and breaking synchronization with the rest of the room.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class RoomMediaSessionPlayer(
    player: Player,
    private val commandBus: RoomCommandBus,
    private val log: DiagnosticLog,
) : ForwardingPlayer(player) {

    override fun play() = dispatch(AppCommand.Play, "play")

    override fun pause() = dispatch(AppCommand.Pause, "pause")

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        dispatch(SystemMediaCommandPolicy.playWhenReady(playWhenReady), "setPlayWhenReady=$playWhenReady")
    }

    override fun stop() = dispatch(AppCommand.Pause, "stop")

    override fun seekTo(positionMs: Long) {
        dispatch(SystemMediaCommandPolicy.seek(normalizePosition(positionMs)), "seekTo=$positionMs")
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        when {
            mediaItemIndex == currentMediaItemIndex -> seekTo(positionMs)
            mediaItemIndex == nextMediaItemIndex -> seekToNextMediaItem()
            mediaItemIndex == previousMediaItemIndex -> seekToPreviousMediaItem()
            else -> log.i(TAG, "Ignored unsupported system seek to item index=$mediaItemIndex")
        }
    }

    override fun seekToDefaultPosition() = seekTo(0L)

    override fun seekToDefaultPosition(mediaItemIndex: Int) = seekTo(mediaItemIndex, 0L)

    override fun seekBack() {
        seekTo((currentPosition - seekBackIncrement).coerceAtLeast(0L))
    }

    override fun seekForward() {
        val upperBound = duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        seekTo((currentPosition + seekForwardIncrement).coerceAtMost(upperBound))
    }

    override fun seekToNext() = dispatch(AppCommand.SkipNext, "next")

    override fun seekToNextMediaItem() = dispatch(AppCommand.SkipNext, "nextItem")

    override fun seekToPrevious() {
        dispatch(
            SystemMediaCommandPolicy.previous(currentPosition, maxSeekToPreviousPosition),
            "previous",
        )
    }

    override fun seekToPreviousMediaItem() = dispatch(AppCommand.SkipPrevious, "previousItem")

    private fun normalizePosition(positionMs: Long): Long = when {
        positionMs == androidx.media3.common.C.TIME_UNSET -> 0L
        positionMs < 0L -> 0L
        else -> positionMs
    }

    private fun dispatch(command: AppCommand, source: String) {
        val result = commandBus.trySend(command)
        if (result.isSuccess) {
            log.i(TAG, "System media control: $source")
        } else {
            log.e(TAG, "Could not accept system media control: $source", result.exceptionOrNull())
        }
    }

    private companion object {
        const val TAG = "UnisonMediaSession"
    }
}
