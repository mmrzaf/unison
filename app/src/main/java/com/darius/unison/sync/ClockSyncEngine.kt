package com.darius.unison.sync

import com.darius.unison.util.MonotonicClock
import java.util.ArrayDeque
import java.util.UUID

/**
 * Maintains the mapping: coordinatorTimeNs = localTimeNs + offsetNs.
 */
class ClockSyncEngine(
    private val clock: MonotonicClock,
    private val maxSamples: Int = 20,
) {
    data class PendingPing(val pingId: String, val localSendNs: Long)
    data class Sample(val offsetNs: Long, val roundTripNs: Long, val createdLocalNs: Long)

    private val pending = linkedMapOf<String, Long>()
    private val samples = ArrayDeque<Sample>()
    var offsetNs: Long = 0
        private set
    var roundTripNs: Long = Long.MAX_VALUE
        private set
    val synchronized: Boolean get() = samples.size >= 3

    fun createPing(): PendingPing {
        val ping = PendingPing(UUID.randomUUID().toString(), clock.nowNs())
        pending[ping.pingId] = ping.localSendNs
        while (pending.size > MAX_PENDING_PINGS) pending.remove(pending.keys.first())
        return ping
    }

    fun recordPong(
        pingId: String,
        echoedGuestSendNs: Long,
        coordinatorReceiveNs: Long,
        coordinatorSendNs: Long,
        localReceiveNs: Long = clock.nowNs(),
    ): Sample? {
        val localSendNs = pending.remove(pingId) ?: return null
        if (localSendNs != echoedGuestSendNs) return null
        val processing = (coordinatorSendNs - coordinatorReceiveNs).coerceAtLeast(0)
        val rtt = ((localReceiveNs - localSendNs) - processing).coerceAtLeast(0)
        val offset = ((coordinatorReceiveNs - localSendNs) + (coordinatorSendNs - localReceiveNs)) / 2
        val sample = Sample(offset, rtt, localReceiveNs)
        samples.addLast(sample)
        while (samples.size > maxSamples) samples.removeFirst()
        recompute()
        return sample
    }

    fun toCoordinatorTime(localTimeNs: Long): Long = localTimeNs + offsetNs
    fun toLocalTime(coordinatorTimeNs: Long): Long = coordinatorTimeNs - offsetNs
    fun coordinatorNowNs(): Long = toCoordinatorTime(clock.nowNs())

    fun reset() {
        pending.clear()
        samples.clear()
        offsetNs = 0
        roundTripNs = Long.MAX_VALUE
    }

    private fun recompute() {
        if (samples.isEmpty()) return
        val sortedByRtt = samples.sortedBy { it.roundTripNs }
        val best = sortedByRtt.take(maxOf(3, sortedByRtt.size / 2))
        val medianOffset = best.map { it.offsetNs }.sorted().let { values ->
            if (values.size % 2 == 1) values[values.size / 2]
            else (values[values.size / 2 - 1] + values[values.size / 2]) / 2
        }
        // Avoid abrupt scheduler jumps after initial convergence.
        offsetNs = if (samples.size < 3 || offsetNs == 0L) medianOffset else {
            val delta = medianOffset - offsetNs
            offsetNs + delta.coerceIn(-2_000_000L, 2_000_000L)
        }
        roundTripNs = best.map { it.roundTripNs }.sorted()[best.size / 2]
    }

    private companion object {
        const val MAX_PENDING_PINGS = 64
    }

}
