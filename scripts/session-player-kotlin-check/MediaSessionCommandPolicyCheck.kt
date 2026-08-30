package com.darius.unison.playback

import androidx.media3.common.Player

fun main() {
    val commands = MediaSessionCommandPolicy.SYSTEM_COMMANDS
    check(!commands.contains(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) {
        "arbitrary queue-item seek must not be advertised"
    }
    check(commands.contains(Player.COMMAND_PLAY_PAUSE))
    check(commands.contains(Player.COMMAND_STOP))
    check(commands.contains(Player.COMMAND_SEEK_TO_DEFAULT_POSITION))
    check(commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    check(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
    check(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
    check(commands.contains(Player.COMMAND_SEEK_BACK))
    check(commands.contains(Player.COMMAND_SEEK_FORWARD))
    println("MEDIA_SESSION_COMMAND_POLICY_OK")
}
