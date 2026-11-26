package com.darius.unison.harness

import com.darius.unison.model.AppCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** S14: phones whose monotonic clocks are unrelated still execute scheduled commands together. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomClockScenariosTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun anOffsetAndDriftingGuestExecutesScheduledPlayOnTime() = runTest {
        withRoom(temporaryFolder, guestCount = 0) { harness, host, _ ->
            val ahead = harness.device("Ahead", clockOffsetNs = 7_000_000_000L, clockDriftPpm = 200)
            val behind =
                harness.device("Behind", clockOffsetNs = 1_000_000_000L, clockDriftPpm = -150)
            joinRoom(ahead, host)
            joinRoom(behind, host)
            settle(30_000)

            val track = host.addLocalTrack("timed", durationMs = 120_000L)
            host.runtime.submit(AppCommand.AddTracks(listOf(track.trackId))).await()
            settle(15_000)
            val item = host.store.structure.value.snapshot!!.queue.single().queueItemId
            host.runtime.submit(AppCommand.PlayQueueItem(item)).await()
            settle(8_000)

            listOf(host, ahead, behind).forEach { device ->
                val lateMs =
                    device.log
                        .snapshot()
                        .filter { it.eventName == "playback.command.executing" }
                        .map { (it.attributes["playback.late_ms"] as Number).toLong() }
                assertTrue(
                    "device ${device.index} executed the scheduled play",
                    lateMs.isNotEmpty(),
                )
                assertTrue(
                    "device ${device.index} started ${lateMs.max()}ms late",
                    lateMs.max() <= MAX_LATE_MS,
                )
                assertTrue(
                    "device ${device.index} is audibly playing",
                    device.player.state.value.isPlaying,
                )
            }
            RoomInvariants.assertNoFailedEvents(host, ahead, behind)
        }
    }

    private companion object {
        /** Scheduled starts carry lead time; anything beyond this is a mapping error. */
        const val MAX_LATE_MS = 250L
    }
}
