package com.darius.unison.sync

import com.darius.unison.model.QueueItemId
import com.darius.unison.playback.AudioOutputRoute
import com.darius.unison.playback.PlaybackActivityState
import com.darius.unison.playback.PlaybackSample
import com.darius.unison.playback.PlaybackSpeedCommandGate
import com.darius.unison.util.MonotonicClock
import kotlin.math.abs

class FakeMonotonicClock(var now: Long = 0L) : MonotonicClock {
    override fun nowNs(): Long = now

    fun advanceNs(deltaNs: Long) {
        now += deltaNs
    }
}

class FakeSynchronizedPlayer(
    private val queueItemId: QueueItemId,
    private val hardwareRate: Double,
    var outputRoute: AudioOutputRoute = AudioOutputRoute.BUILT_IN_SPEAKER,
) {
    var positionMs: Double = 0.0
    var playbackSpeed: Float = 1f
    var activityState: PlaybackActivityState = PlaybackActivityState.READY_PLAYING
    var playWhenReady: Boolean = true
    var seekRevision: Long = 0L
    var hardSeekCount: Int = 0
        private set

    var speedCommandCount: Int = 0
        private set

    fun advance(deltaMs: Long) {
        if (activityState == PlaybackActivityState.READY_PLAYING && playWhenReady) {
            positionMs += deltaMs * hardwareRate * playbackSpeed
        }
    }

    fun sample(atLocalNs: Long): PlaybackSample =
        PlaybackSample(
            queueItemId = queueItemId,
            positionMs = positionMs.toLong(),
            durationMs = Long.MAX_VALUE,
            sampledAtLocalNs = atLocalNs,
            playWhenReady = playWhenReady,
            isPlaying = activityState == PlaybackActivityState.READY_PLAYING && playWhenReady,
            activityState = activityState,
            playbackSpeed = playbackSpeed,
            outputRoute = outputRoute,
            seekRevision = seekRevision,
        )

    fun apply(
        decision: PlaybackSyncDecision,
        speedGate: PlaybackSpeedCommandGate? = null,
        nowNs: Long = 0L,
    ) {
        when (val action = decision.action) {
            is SyncAction.SetSpeed -> applySpeed(action.speed, speedGate, nowNs)
            is SyncAction.Seek -> {
                positionMs = action.positionMs.toDouble()
                applySpeed(decision.baselineSpeed, speedGate, nowNs)
                seekRevision++
                hardSeekCount++
            }
            is SyncAction.Hold -> applySpeed(action.baselineSpeed, speedGate, nowNs)
        }
    }

    private fun applySpeed(
        requestedSpeed: Float,
        speedGate: PlaybackSpeedCommandGate?,
        nowNs: Long,
    ) {
        val selected =
            if (speedGate == null) {
                requestedSpeed
            } else {
                speedGate.select(requestedSpeed, playbackSpeed, nowNs) ?: return
            }
        if (selected != playbackSpeed) {
            playbackSpeed = selected
            speedCommandCount++
        }
    }
}

class SyncMetricsCollector {
    private val absoluteDrifts = mutableListOf<Long>()
    var minSpeed: Float = Float.MAX_VALUE
        private set

    var maxSpeed: Float = -Float.MAX_VALUE
        private set

    fun record(decision: PlaybackSyncDecision) {
        decision.rawDriftMs?.let { absoluteDrifts += abs(it) }
        minSpeed = minOf(minSpeed, decision.selectedSpeed)
        maxSpeed = maxOf(maxSpeed, decision.selectedSpeed)
    }

    fun result(hardSeeks: Int): SyncScenarioMetrics {
        val sorted = absoluteDrifts.sorted()
        val p95 = if (sorted.isEmpty()) 0L else sorted[((sorted.size - 1) * 0.95).toInt()]
        return SyncScenarioMetrics(
            medianAbsoluteDriftMs = percentile(sorted, 0.5),
            p95AbsoluteDriftMs = p95,
            maximumAbsoluteDriftMs = sorted.lastOrNull() ?: 0L,
            hardSeekCount = hardSeeks,
            minimumSpeed = if (minSpeed == Float.MAX_VALUE) 1f else minSpeed,
            maximumSpeed = if (maxSpeed == -Float.MAX_VALUE) 1f else maxSpeed,
        )
    }

    private fun percentile(sorted: List<Long>, fraction: Double): Long =
        if (sorted.isEmpty()) 0L else sorted[((sorted.size - 1) * fraction).toInt()]
}

data class SyncScenarioMetrics(
    val medianAbsoluteDriftMs: Long,
    val p95AbsoluteDriftMs: Long,
    val maximumAbsoluteDriftMs: Long,
    val hardSeekCount: Int,
    val minimumSpeed: Float,
    val maximumSpeed: Float,
)

class PlaybackScenarioRunner(
    private val controller: PlaybackSyncController,
    private val player: FakeSynchronizedPlayer,
    private val queueItemId: QueueItemId,
    private val speedGate: PlaybackSpeedCommandGate? = null,
) {
    fun run(
        durationMs: Long,
        clockUncertaintyNs: Long = 2_000_000L,
        coordinatorUsesLocalClock: Boolean = false,
        expectedPositionNoiseMs: (tick: Int) -> Long = { 0L },
        onTick: (tick: Int, player: FakeSynchronizedPlayer) -> Unit = { _, _ -> },
    ): SyncScenarioMetrics {
        val tickMs = controller.tuning.activeCorrectionIntervalMs
        val ticks = (durationMs / tickMs).toInt()
        val metrics = SyncMetricsCollector()
        repeat(ticks) { tick ->
            if (tick > 0) player.advance(tickMs)
            onTick(tick, player)
            val nowMs = tick * tickMs
            val decision =
                controller.evaluate(
                    PlaybackSyncInput(
                        canonicalQueueItemId = queueItemId,
                        expectedPositionMs = nowMs + expectedPositionNoiseMs(tick),
                        sample = player.sample(nowMs * 1_000_000L),
                        connected = true,
                        clockState = ClockSyncState.LOCKED,
                        clockUncertaintyNs = clockUncertaintyNs,
                        coordinatorUsesLocalClock = coordinatorUsesLocalClock,
                    )
                )
            player.apply(decision, speedGate, nowMs * 1_000_000L)
            metrics.record(decision)
        }
        return metrics.result(player.hardSeekCount)
    }
}

/** A deterministic guest monotonic clock expressed against coordinator simulation time. */
class AffineGuestClock(
    var coordinatorNowNs: Long = 0L,
    private val localRate: Double = 1.0,
    private val localOffsetNs: Long = 0L,
) : MonotonicClock {
    override fun nowNs(): Long = (coordinatorNowNs.toDouble() * localRate).toLong() + localOffsetNs

    fun advanceCoordinatorNs(deltaNs: Long) {
        coordinatorNowNs += deltaNs
    }
}

/** Drives real ClockSyncEngine ping/pong math without wall-clock waits or sockets. */
class ClockNetworkScenario(
    val clock: AffineGuestClock,
    val engine: ClockSyncEngine = ClockSyncEngine(clock),
) {
    fun exchange(
        outboundNs: Long,
        inboundNs: Long,
        coordinatorProcessingNs: Long = 1_000_000L,
    ): ClockSyncEngine.Sample? {
        val coordinatorSendStartNs = clock.coordinatorNowNs
        val ping = engine.createPing()
        val coordinatorReceiveNs = coordinatorSendStartNs + outboundNs
        val coordinatorSendNs = coordinatorReceiveNs + coordinatorProcessingNs
        clock.coordinatorNowNs = coordinatorSendNs + inboundNs
        return engine.recordPong(
            pingId = ping.pingId,
            echoedGuestSendNs = ping.localSendNs,
            coordinatorReceiveNs = coordinatorReceiveNs,
            coordinatorSendNs = coordinatorSendNs,
            localReceiveNs = clock.nowNs(),
        )
    }

    fun exchangeSeries(
        count: Int,
        intervalNs: Long = 1_000_000_000L,
        delays: (Int) -> Pair<Long, Long>,
    ) {
        repeat(count) { index ->
            val (outbound, inbound) = delays(index)
            exchange(outbound, inbound)
            clock.advanceCoordinatorNs(intervalNs)
        }
    }

    fun mappingErrorNs(): Long =
        abs(engine.toCoordinatorTime(clock.nowNs()) - clock.coordinatorNowNs)
}
