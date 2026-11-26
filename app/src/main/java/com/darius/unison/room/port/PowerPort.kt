package com.darius.unison.room.port

/** Radio and CPU ownership while a room session is active. */
interface PowerPort : AutoCloseable {
    fun apply(demand: PowerDemand)
}

data class PowerDemand(
    val wifi: Boolean,
    val cpu: Boolean,
) {
    companion object {
        val NONE = PowerDemand(wifi = false, cpu = false)
    }
}
