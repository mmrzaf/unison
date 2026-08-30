package com.darius.unison.playback

import androidx.media3.common.Player

/** MediaSession capabilities that Unison can faithfully translate into canonical room commands. */
internal object MediaSessionCommandPolicy {
    val readOnlyPlayerCommands: Set<Int> =
        setOf(
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_AUDIO_ATTRIBUTES,
            Player.COMMAND_GET_VOLUME,
            Player.COMMAND_GET_DEVICE_VOLUME,
            Player.COMMAND_GET_TEXT,
            Player.COMMAND_GET_TRACKS,
        )

    val supportedPlayerCommands: Set<Int> =
        setOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
        )
}
