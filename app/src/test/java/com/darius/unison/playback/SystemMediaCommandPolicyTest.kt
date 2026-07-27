package com.darius.unison.playback

import com.darius.unison.model.AppCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemMediaCommandPolicyTest {
    @Test
    fun playWhenReadyMapsToRoomTransport() {
        assertEquals(AppCommand.Play, SystemMediaCommandPolicy.playWhenReady(true))
        assertEquals(AppCommand.Pause, SystemMediaCommandPolicy.playWhenReady(false))
    }

    @Test
    fun negativeSeekIsClamped() {
        assertEquals(AppCommand.Seek(0L), SystemMediaCommandPolicy.seek(-500L))
        assertEquals(AppCommand.Seek(1_500L), SystemMediaCommandPolicy.seek(1_500L))
    }

    @Test
    fun previousRestartsCurrentSongAfterThreshold() {
        assertEquals(AppCommand.Seek(0L), SystemMediaCommandPolicy.previous(6_000L, 3_000L))
        assertEquals(AppCommand.SkipPrevious, SystemMediaCommandPolicy.previous(2_000L, 3_000L))
    }
}
