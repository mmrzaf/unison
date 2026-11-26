package com.darius.unison.harness

import com.darius.unison.model.AppCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** S15: a busy player thread must not starve the room actor's heartbeats or control flow. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomLoadScenariosTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun aSlowPlayerUnderACommandFloodKeepsTheRoomAliveAndResponsive() = runTest {
        withRoom(temporaryFolder, guestCount = 2) { harness, host, guests ->
            guests.forEach { it.player.mutationLatencyMs = 400L }
            host.player.mutationLatencyMs = 400L
            val tracks = (1..12).map { host.addLocalTrack("flood-$it") }
            host.runtime.submit(AppCommand.AddTracks(tracks.map { it.trackId })).await()
            settle(5_000)

            val queue = host.store.structure.value.snapshot!!.queue
            repeat(40) { round ->
                host.runtime.submit(AppCommand.PlayQueueItem(queue[round % queue.size].queueItemId))
                host.runtime.submit(AppCommand.Seek(positionMs = (round * 1_000L) % 60_000L))
                host.runtime.submit(AppCommand.SkipNext())
                settle(250)
            }
            settle(90_000)

            assertEquals("nobody was dropped by a stalled actor", 3, host.memberCount())
            guests.forEach { assertEquals(3, it.memberCount()) }
            val everyone = listOf(host) + guests
            everyone.forEach { device ->
                val worstQueueWaitMs =
                    device.log
                        .snapshot()
                        .filter { it.eventName == "room.event.queue_slow" }
                        .maxOfOrNull {
                            (it.attributes["operation.queue_wait_ms"] as? Number)?.toLong() ?: 0L
                        } ?: 0L
                assertTrue(
                    "device ${device.index}: an event waited ${worstQueueWaitMs}ms for the actor",
                    worstQueueWaitMs < MAX_ACTOR_WAIT_MS,
                )
            }
            RoomInvariants.assertNoFailedEvents(host, *guests.toTypedArray())
        }
    }

    private companion object {
        /** Well inside the 5 s heartbeat interval and the 30 s peer timeout. */
        const val MAX_ACTOR_WAIT_MS = 2_500L
    }
}
