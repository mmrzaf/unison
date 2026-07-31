package com.darius.unison.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomServicePolicyTest {
    @Test
    fun `idle stop requires a valid service start id`() {
        assertFalse(RoomServicePolicy.canScheduleIdleStop(0))
        assertFalse(RoomServicePolicy.canScheduleIdleStop(-1))
        assertTrue(RoomServicePolicy.canScheduleIdleStop(1))
    }

    @Test
    fun `active room or hotspot requires foreground ownership`() {
        assertTrue(
            RoomServicePolicy.requiresRoomForeground(sessionActive = true, hotspotActive = false)
        )
        assertTrue(
            RoomServicePolicy.requiresRoomForeground(sessionActive = false, hotspotActive = true)
        )
        assertFalse(
            RoomServicePolicy.requiresRoomForeground(sessionActive = false, hotspotActive = false)
        )
    }

    @Test
    fun `stale playback intent without an item is inactive`() {
        assertFalse(
            RoomServicePolicy.playbackActive(queueItemPresent = false, playWhenReady = true)
        )
        assertFalse(
            RoomServicePolicy.playbackActive(queueItemPresent = true, playWhenReady = false)
        )
        assertTrue(RoomServicePolicy.playbackActive(queueItemPresent = true, playWhenReady = true))
    }

    @Test
    fun `service stops only when room hotspot and real playback are inactive`() {
        assertTrue(
            RoomServicePolicy.shouldStop(
                operationActive = false,
                hotspotActive = false,
                playbackActive = false,
            )
        )
        assertFalse(RoomServicePolicy.shouldStop(true, false, false))
        assertFalse(RoomServicePolicy.shouldStop(false, true, false))
        assertFalse(RoomServicePolicy.shouldStop(false, false, true))
        assertFalse(
            RoomServicePolicy.shouldStop(
                operationActive = false,
                hotspotActive = false,
                playbackActive = false,
                commandOutstanding = true,
            )
        )
    }
}
