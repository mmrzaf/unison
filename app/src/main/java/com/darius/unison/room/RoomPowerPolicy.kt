package com.darius.unison.room

/** Pure policy for radio and CPU ownership during an explicitly active room session. */
internal object RoomPowerPolicy {
    data class Demand(
        val wifi: Boolean,
        val cpu: Boolean,
    )

    fun evaluate(sessionActive: Boolean): Demand =
        if (sessionActive) {
            // Every peer must remain able to receive control messages and emit heartbeats while its
            // screen is off, including when playback is paused. The Media3 player-control
            // notification makes
            // this ownership visible; WifiLocks uses a bounded wake-lock timeout as a leak
            // backstop.
            Demand(wifi = true, cpu = true)
        } else {
            Demand(wifi = false, cpu = false)
        }
}
