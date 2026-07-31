package com.darius.unison.room

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomPowerPolicyTest {
    @Test
    fun inactiveRoomReleasesEverything() {
        assertEquals(
            RoomPowerPolicy.Demand(wifi = false, cpu = false),
            RoomPowerPolicy.evaluate(sessionActive = false),
        )
    }

    @Test
    fun everyActivePeerKeepsCpuAndWifiAvailable() {
        assertEquals(
            RoomPowerPolicy.Demand(wifi = true, cpu = true),
            RoomPowerPolicy.evaluate(sessionActive = true),
        )
    }
}
