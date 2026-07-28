package com.darius.unison.sync

import com.darius.unison.util.MonotonicClock
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

enum class ClockSyncState {
    UNSYNCHRONIZED,
    ACQUIRING,
    LOCKED,
    STALE,
}

data class ClockEstimate(
    /** Coordinator minus local time at [sampledAtLocalNs]. */
    val offsetNs: Long,
    val rate: Double,
    val rttNs: Long,
    val rttVariationNs: Long,
    val uncertaintyNs: Long,
    val sampledAtLocalNs: Long,
    val lastGoodSampleLocalNs: Long?,
    val sampleAgeNs: Long,
    val acceptedSampleCount: Int,
    val rejectedSampleCount: Int,
    val state: ClockSyncState,
)

data class ClockConversion(
    val timeNs: Long,
    val uncertaintyNs: Long,
    val state: ClockSyncState,
)

data class ClockSyncConfig(
    val maxSamples: Int = 64,
    val minimumLockSamples: Int = 5,
    val maxRoundTripNs: Long = 250_000_000L,
    val staleAfterNs: Long = 12_000_000_000L,
    val minimumRateFitSpanNs: Long = 5_000_000_000L,
    val maximumRateError: Double = 0.002,
    val lockedRateSmoothing: Double = 0.15,
    val maximumLockedMappingStepNs: Long = 2_000_000L,
    val minimumOutlierToleranceNs: Long = 20_000_000L,
) {
    init {
        require(maxSamples >= minimumLockSamples)
        require(minimumLockSamples >= 3)
        require(maxRoundTripNs > 0)
        require(staleAfterNs > 0)
        require(minimumRateFitSpanNs > 0)
        require(maximumRateError in 0.0..0.05)
        require(lockedRateSmoothing in 0.0..1.0)
        require(maximumLockedMappingStepNs > 0)
        require(minimumOutlierToleranceNs > 0)
    }
}

/**
 * Robust NTP-style monotonic clock mapper using an affine model:
 * coordinatorTime = anchorCoordinator + rate * (localTime - anchorLocal).
 */
class ClockSyncEngine(
    private val clock: MonotonicClock,
    private val config: ClockSyncConfig = ClockSyncConfig(),
) {
    data class PendingPing(val pingId: String, val localSendNs: Long)

    data class Sample(
        val localMidpointNs: Long,
        val coordinatorMidpointNs: Long,
        val offsetNs: Long,
        val roundTripNs: Long,
        val createdLocalNs: Long,
    )

    private val pending = linkedMapOf<String, Long>()
    private val samples = ArrayDeque<Sample>()
    private var anchorLocalNs = 0L
    private var anchorCoordinatorNs = 0L
    private var fittedRate = 1.0
    private var fittedRttNs = Long.MAX_VALUE
    private var fittedRttVariationNs = Long.MAX_VALUE
    private var baseUncertaintyNs = Long.MAX_VALUE
    private var lastGoodSampleLocalNs: Long? = null
    private var rejected = 0

    val state: ClockSyncState get() = estimate().state
    val synchronized: Boolean get() = state == ClockSyncState.LOCKED
    val offsetNs: Long get() {
        val now = clock.nowNs()
        return toCoordinatorTime(now) - now
    }
    val rate: Double get() = fittedRate
    val roundTripNs: Long get() = fittedRttNs
    val uncertaintyNs: Long get() = estimate().uncertaintyNs

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
        val localSendNs = pending.remove(pingId) ?: return reject()
        if (localSendNs != echoedGuestSendNs) return reject()
        val previousGoodSample = lastGoodSampleLocalNs
        if (previousGoodSample != null && localReceiveNs - previousGoodSample > config.staleAfterNs) {
            // A sleep/wake or long disconnect invalidates the old regression window. Reacquire
            // instead of combining pre-gap and post-gap timing into a misleading clock rate.
            samples.clear()
            anchorLocalNs = 0L
            anchorCoordinatorNs = 0L
            fittedRate = 1.0
            fittedRttNs = Long.MAX_VALUE
            fittedRttVariationNs = Long.MAX_VALUE
            baseUncertaintyNs = Long.MAX_VALUE
        }
        if (localReceiveNs < localSendNs) return reject()
        if (coordinatorSendNs < coordinatorReceiveNs) return reject()

        val processingNs = coordinatorSendNs - coordinatorReceiveNs
        val elapsedLocalNs = localReceiveNs - localSendNs
        if (processingNs > elapsedLocalNs) return reject()
        val roundTripNs = elapsedLocalNs - processingNs
        if (roundTripNs < 0L || roundTripNs > config.maxRoundTripNs) return reject()

        val localMidpointNs = midpoint(localSendNs, localReceiveNs)
        val coordinatorMidpointNs = midpoint(coordinatorReceiveNs, coordinatorSendNs)
        val offsetNs = coordinatorMidpointNs - localMidpointNs
        val sample = Sample(
            localMidpointNs = localMidpointNs,
            coordinatorMidpointNs = coordinatorMidpointNs,
            offsetNs = offsetNs,
            roundTripNs = roundTripNs,
            createdLocalNs = localReceiveNs,
        )

        if (samples.size >= config.minimumLockSamples) {
            val predicted = toCoordinatorTime(localMidpointNs)
            val residual = abs(coordinatorMidpointNs - predicted)
            val tolerance = max(config.minimumOutlierToleranceNs, safeMultiply(baseUncertaintyNs, 4L))
            // High-delay samples are especially vulnerable to asymmetry. Reject them when their
            // implied offset sharply disagrees with the established low-RTT model.
            if (residual > tolerance) return reject()
        }

        samples.addLast(sample)
        while (samples.size > config.maxSamples) samples.removeFirst()
        lastGoodSampleLocalNs = localReceiveNs
        recompute(localReceiveNs)
        return sample
    }

    fun toCoordinatorTime(localTimeNs: Long): Long {
        if (anchorLocalNs == 0L && anchorCoordinatorNs == 0L) return localTimeNs
        val delta = localTimeNs - anchorLocalNs
        return anchorCoordinatorNs + (delta.toDouble() * fittedRate).toLong()
    }

    fun toLocalTime(coordinatorTimeNs: Long): Long {
        if (anchorLocalNs == 0L && anchorCoordinatorNs == 0L) return coordinatorTimeNs
        val delta = coordinatorTimeNs - anchorCoordinatorNs
        return anchorLocalNs + (delta.toDouble() / fittedRate).toLong()
    }

    fun coordinatorNowNs(): Long = toCoordinatorTime(clock.nowNs())

    fun toCoordinatorTimeWithUncertainty(localTimeNs: Long): ClockConversion {
        val estimate = estimate(localTimeNs)
        return ClockConversion(toCoordinatorTime(localTimeNs), estimate.uncertaintyNs, estimate.state)
    }

    fun toLocalTimeWithUncertainty(coordinatorTimeNs: Long): ClockConversion {
        val local = toLocalTime(coordinatorTimeNs)
        val estimate = estimate(local)
        return ClockConversion(local, estimate.uncertaintyNs, estimate.state)
    }

    fun estimate(atLocalNs: Long = clock.nowNs()): ClockEstimate {
        val last = lastGoodSampleLocalNs
        val age = last?.let { (atLocalNs - it).coerceAtLeast(0L) } ?: Long.MAX_VALUE
        val state = when {
            samples.isEmpty() -> ClockSyncState.UNSYNCHRONIZED
            age > config.staleAfterNs -> ClockSyncState.STALE
            samples.size < config.minimumLockSamples -> ClockSyncState.ACQUIRING
            else -> ClockSyncState.LOCKED
        }
        val ageUncertainty = if (age == Long.MAX_VALUE) Long.MAX_VALUE else {
            // Clock-rate error accumulated since the last accepted sample.
            (age.toDouble() * abs(fittedRate - 1.0)).toLong()
        }
        val uncertainty = safeAdd(baseUncertaintyNs, ageUncertainty)
        return ClockEstimate(
            offsetNs = if (last == null) 0L else toCoordinatorTime(atLocalNs) - atLocalNs,
            rate = fittedRate,
            rttNs = fittedRttNs,
            rttVariationNs = fittedRttVariationNs,
            uncertaintyNs = uncertainty,
            sampledAtLocalNs = atLocalNs,
            lastGoodSampleLocalNs = last,
            sampleAgeNs = age,
            acceptedSampleCount = samples.size,
            rejectedSampleCount = rejected,
            state = state,
        )
    }

    fun reset() {
        pending.clear()
        samples.clear()
        anchorLocalNs = 0L
        anchorCoordinatorNs = 0L
        fittedRate = 1.0
        fittedRttNs = Long.MAX_VALUE
        fittedRttVariationNs = Long.MAX_VALUE
        baseUncertaintyNs = Long.MAX_VALUE
        lastGoodSampleLocalNs = null
        rejected = 0
    }

    private fun recompute(nowLocalNs: Long) {
        if (samples.isEmpty()) return
        val orderedByRtt = samples.sortedBy { it.roundTripNs }
        val selectedCount = max(
            config.minimumLockSamples.coerceAtMost(orderedByRtt.size),
            ceil(orderedByRtt.size * 0.6).toInt(),
        ).coerceAtMost(orderedByRtt.size)
        val selected = orderedByRtt.take(selectedCount).sortedBy { it.localMidpointNs }

        val medianRtt = median(selected.map { it.roundTripNs })
        val rttMad = median(selected.map { abs(it.roundTripNs - medianRtt) })
        val rttLimit = safeAdd(medianRtt, max(2_000_000L, safeMultiply(rttMad, 3L)))
        val lowRtt = selected.filter { it.roundTripNs <= rttLimit }.ifEmpty { selected }

        val firstX = lowRtt.first().localMidpointNs
        val lastX = lowRtt.last().localMidpointNs
        val spanNs = lastX - firstX
        val rawRate = if (lowRtt.size >= config.minimumLockSamples && spanNs >= config.minimumRateFitSpanNs) {
            linearRate(lowRtt).coerceIn(1.0 - config.maximumRateError, 1.0 + config.maximumRateError)
        } else {
            1.0
        }
        val rawAnchorLocal = lowRtt.last().localMidpointNs
        val rawAnchorCoordinator = predictCoordinator(lowRtt, rawRate, rawAnchorLocal)

        val previouslyLocked = samples.size > config.minimumLockSamples && anchorLocalNs != 0L
        if (!previouslyLocked) {
            anchorLocalNs = rawAnchorLocal
            anchorCoordinatorNs = rawAnchorCoordinator
            fittedRate = rawRate
        } else {
            val currentAtNow = toCoordinatorTime(nowLocalNs)
            val rawAtNow = rawAnchorCoordinator +
                ((nowLocalNs - rawAnchorLocal).toDouble() * rawRate).toLong()
            val boundedStep = (rawAtNow - currentAtNow).coerceIn(
                -config.maximumLockedMappingStepNs,
                config.maximumLockedMappingStepNs,
            )
            fittedRate += (rawRate - fittedRate) * config.lockedRateSmoothing
            fittedRate = fittedRate.coerceIn(
                1.0 - config.maximumRateError,
                1.0 + config.maximumRateError,
            )
            anchorLocalNs = nowLocalNs
            anchorCoordinatorNs = currentAtNow + boundedStep
        }

        val residuals = lowRtt.map { sample ->
            abs(sample.coordinatorMidpointNs - toCoordinatorTime(sample.localMidpointNs))
        }
        val residualMedian = median(residuals)
        fittedRttNs = medianRtt
        fittedRttVariationNs = rttMad
        baseUncertaintyNs = safeAdd(
            medianRtt / 2L,
            safeAdd(residualMedian, rttMad / 2L),
        )
    }

    private fun linearRate(samples: List<Sample>): Double {
        val xOrigin = samples.first().localMidpointNs
        val yOrigin = samples.first().coordinatorMidpointNs
        val xs = samples.map { (it.localMidpointNs - xOrigin).toDouble() }
        val ys = samples.map { (it.coordinatorMidpointNs - yOrigin).toDouble() }
        val meanX = xs.average()
        val meanY = ys.average()
        var numerator = 0.0
        var denominator = 0.0
        for (index in xs.indices) {
            val dx = xs[index] - meanX
            numerator += dx * (ys[index] - meanY)
            denominator += dx * dx
        }
        return if (denominator <= 0.0) 1.0 else numerator / denominator
    }

    private fun predictCoordinator(samples: List<Sample>, rate: Double, localNs: Long): Long {
        val projectedOffsets = samples.map { sample ->
            sample.coordinatorMidpointNs -
                ((sample.localMidpointNs - localNs).toDouble() * rate).toLong()
        }
        return median(projectedOffsets)
    }

    private fun reject(): Sample? {
        rejected++
        return null
    }

    private fun midpoint(a: Long, b: Long): Long = a + (b - a) / 2L

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return Long.MAX_VALUE
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else (sorted[middle - 1] / 2L) + (sorted[middle] / 2L)
    }

    private fun safeAdd(a: Long, b: Long): Long {
        if (a == Long.MAX_VALUE || b == Long.MAX_VALUE) return Long.MAX_VALUE
        return if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
    }

    private fun safeMultiply(value: Long, factor: Long): Long {
        if (value == Long.MAX_VALUE) return Long.MAX_VALUE
        if (value <= 0L || factor <= 0L) return 0L
        return if (value > Long.MAX_VALUE / factor) Long.MAX_VALUE else value * factor
    }

    private companion object {
        const val MAX_PENDING_PINGS = 64
    }
}
