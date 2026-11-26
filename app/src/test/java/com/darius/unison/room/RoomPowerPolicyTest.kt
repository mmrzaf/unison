package com.darius.unison.room

import com.darius.unison.room.port.PowerDemand
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomPowerPolicyTest {
    @Test
    fun inactiveRoomReleasesEverything() {
        assertEquals(
            PowerDemand.NONE,
            RoomPowerPolicy.evaluate(sessionActive = false),
        )
    }

    @Test
    fun everyActivePeerKeepsCpuAndWifiAvailable() {
        assertEquals(
            PowerDemand(wifi = true, cpu = true),
            RoomPowerPolicy.evaluate(sessionActive = true),
        )
    }
}
