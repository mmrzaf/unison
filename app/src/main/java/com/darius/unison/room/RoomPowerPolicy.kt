package com.darius.unison.room

import com.darius.unison.room.port.PowerDemand

/** Pure policy for radio and CPU ownership during an explicitly active room session. */
internal object RoomPowerPolicy {
    fun evaluate(sessionActive: Boolean): PowerDemand =
        if (sessionActive) {
            // Every peer must remain able to receive control messages and emit heartbeats while its
            // screen is off, including when playback is paused. The Media3 player-control
            // notification makes this ownership visible; the CPU lock's bounded timeout is a leak
            // backstop.
            PowerDemand(wifi = true, cpu = true)
        } else {
            PowerDemand.NONE
        }
}
