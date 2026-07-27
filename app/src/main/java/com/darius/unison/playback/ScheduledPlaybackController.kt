package com.darius.unison.playback

import com.darius.unison.model.QueueItemId
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.util.DiagnosticLog
import com.darius.unison.util.MonotonicClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

class ScheduledPlaybackController(
    private val player: PlayerPort,
    private val clock: MonotonicClock,
    private val clockSync: ClockSyncEngine,
    private val scope: CoroutineScope,
    private val log: DiagnosticLog,
    private val onError: (String) -> Unit,
) {
    private var scheduled: Job? = null

    fun schedulePlay(queueItemId: QueueItemId, positionMs: Long, executeAtCoordinatorNs: Long) {
        log.i(
            TAG,
            "Schedule play item=${queueItemId.value.take(8)} positionMs=$positionMs executeAtNs=$executeAtCoordinatorNs"
        )
        schedule("play", executeAtCoordinatorNs) {
            val lateMs = ((clock.nowNs() - clockSync.toLocalTime(executeAtCoordinatorNs)).coerceAtLeast(0) / 1_000_000L)
            if (!player.seekToItem(queueItemId, positionMs + lateMs)) {
                fail("This song is not ready yet")
                return@schedule
            }
            player.setPlaybackSpeed(1f)
            if (!player.play()) fail("This song is not ready yet")
        }
    }

    fun schedulePause(positionMs: Long, executeAtCoordinatorNs: Long) {
        log.i(TAG, "Schedule pause positionMs=$positionMs executeAtNs=$executeAtCoordinatorNs")
        schedule("pause", executeAtCoordinatorNs) {
            player.seekTo(positionMs)
            player.pause()
            player.setPlaybackSpeed(1f)
        }
    }

    fun scheduleSeek(
        queueItemId: QueueItemId,
        positionMs: Long,
        resume: Boolean,
        executeAtCoordinatorNs: Long,
    ) {
        log.i(
            TAG,
            "Schedule seek item=${queueItemId.value.take(8)} positionMs=$positionMs resume=$resume executeAtNs=$executeAtCoordinatorNs",
        )
        schedule("seek", executeAtCoordinatorNs) {
            val lateMs = if (resume) {
                ((clock.nowNs() - clockSync.toLocalTime(executeAtCoordinatorNs)).coerceAtLeast(0) / 1_000_000L)
            } else 0L
            if (!player.seekToItem(queueItemId, positionMs + lateMs)) {
                fail("This song is not ready yet")
                return@schedule
            }
            player.setPlaybackSpeed(1f)
            if (resume) {
                if (!player.play()) fail("This song is not ready yet")
            } else {
                player.pause()
            }
        }
    }

    fun cancel() {
        scheduled?.cancel()
        scheduled = null
    }

    private fun schedule(name: String, executeAtCoordinatorNs: Long, action: suspend () -> Unit) {
        scheduled?.cancel()
        scheduled = scope.launch(Dispatchers.Default) {
            try {
                val localTarget = clockSync.toLocalTime(executeAtCoordinatorNs)
                while (isActive) {
                    val remainingNs = localTarget - clock.nowNs()
                    if (remainingNs <= 0) break
                    if (remainingNs > 2_000_000L) {
                        delay(((remainingNs - 1_000_000L) / 1_000_000L).coerceAtLeast(1L))
                    } else {
                        // Keep the final sub-millisecond wait away from the Android main thread.
                        yield()
                    }
                }
                log.i(TAG, "Execute $name lateMs=${((clock.nowNs() - localTarget).coerceAtLeast(0) / 1_000_000L)}")
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                log.e(TAG, "Scheduled $name failed", error)
                onError("Playback could not start")
            }
        }
    }

    private fun fail(message: String) {
        log.e(TAG, message)
        onError(message)
    }

    private companion object {
        const val TAG = "UnisonScheduler"
    }
}
