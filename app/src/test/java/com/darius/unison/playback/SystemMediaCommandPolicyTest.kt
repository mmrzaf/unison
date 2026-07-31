package com.darius.unison.playback

import com.darius.unison.model.AppCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemMediaCommandPolicyTest {
    @Test
    fun playWhenReadyMapsToRoomTransport() {
        assertTrue(SystemMediaCommandPolicy.playWhenReady(true) is AppCommand.Play)
        assertTrue(SystemMediaCommandPolicy.playWhenReady(false) is AppCommand.Pause)
    }

    @Test
    fun negativeSeekIsClamped() {
        assertEquals(0L, SystemMediaCommandPolicy.seek(-500L).positionMs)
        assertEquals(1_500L, SystemMediaCommandPolicy.seek(1_500L).positionMs)
    }

    @Test
    fun previousRestartsCurrentSongAfterThreshold() {
        assertEquals(
            0L,
            (SystemMediaCommandPolicy.previous(6_000L, 3_000L) as AppCommand.Seek).positionMs,
        )
        assertTrue(SystemMediaCommandPolicy.previous(2_000L, 3_000L) is AppCommand.SkipPrevious)
    }
}
