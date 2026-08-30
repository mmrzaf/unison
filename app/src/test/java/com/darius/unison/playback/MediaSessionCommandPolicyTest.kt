package com.darius.unison.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionCommandPolicyTest {
    private val commands = MediaSessionCommandPolicy.SYSTEM_COMMANDS

    @Test
    fun arbitraryQueueItemSeekIsNotAdvertised() {
        assertFalse(commands.contains(Player.COMMAND_SEEK_TO_MEDIA_ITEM))
    }

    @Test
    fun supportedCanonicalTransportCommandsRemainAdvertised() {
        assertTrue(commands.contains(Player.COMMAND_PLAY_PAUSE))
        assertTrue(commands.contains(Player.COMMAND_STOP))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_DEFAULT_POSITION))
        assertTrue(commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_SEEK_BACK))
        assertTrue(commands.contains(Player.COMMAND_SEEK_FORWARD))
    }
}
