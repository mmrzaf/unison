package com.darius.unison.harness

import com.darius.unison.model.AppCommand
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** S10: natural item ends are decided by canonical room state, never by a phone's player. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomPlaybackScenariosTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun anItemEndingNaturallyAdvancesEveryPhoneToTheNextItem() = runTest {
        withRoom(temporaryFolder, guestCount = 2) { _, host, guests ->
            val queue = startPlayback(host, guests, trackCount = 3)

            host.player.endNaturally()
            settle(10_000)

            (listOf(host) + guests).forEach { device ->
                val playback = device.store.structure.value.snapshot!!.playback
                assertEquals(
                    "device ${device.index} follows the canonical successor",
                    queue[1],
                    playback.queueItemId,
                )
                assertTrue("playback continues", playback.isPlaying)
            }
            RoomInvariants.assertNoFailedEvents(host, *guests.toTypedArray())
        }
    }

    @Test
    fun theFinalItemEndingStopsPlaybackAndAllowsAReplay() = runTest {
        withRoom(temporaryFolder, guestCount = 1) { _, host, guests ->
            val queue = startPlayback(host, guests, trackCount = 1)

            host.player.endNaturally()
            settle(5_000)
            val ended = host.store.structure.value.snapshot!!.playback
            assertFalse("the room stops after its last song", ended.isPlaying)
            assertEquals(queue.single(), ended.queueItemId)

            host.runtime.submit(AppCommand.Play()).await()
            settle(5_000)
            val replay = host.store.structure.value.snapshot!!.playback
            assertTrue("a second play restarts the song", replay.isPlaying)
            assertTrue(replay.projectedPositionMs(replay.coordinatorTimestampNs) < 5_000L)
            RoomInvariants.assertNoFailedEvents(host, *guests.toTypedArray())
        }
    }

    @Test
    fun repeatOneReplaysTheSameItemFromTheStart() = runTest {
        withRoom(temporaryFolder, guestCount = 1) { _, host, guests ->
            val queue = startPlayback(host, guests, trackCount = 2)
            host.runtime.submit(AppCommand.SetRepeat(RepeatMode.ONE)).await()
            settle(2_000)

            host.player.endNaturally()
            settle(10_000)

            val playback = host.store.structure.value.snapshot!!.playback
            assertEquals("repeat-one stays on the item", queue[0], playback.queueItemId)
            assertTrue(playback.isPlaying)
            RoomInvariants.assertNoFailedEvents(host, *guests.toTypedArray())
        }
    }

    /** S11: a phone losing its audio output never changes what the rest of the room is playing. */
    @Test
    fun aTransientInterruptionRejoinsOnceAfterTheRealRelease() = runTest {
        withRoom(temporaryFolder, guestCount = 2) { _, host, guests ->
            val queue = startPlayback(host, guests, trackCount = 2)
            val interrupted = guests.first()
            val roomRevision = host.store.structure.value.snapshot!!.playback.revision
            val rejoinsBefore = interrupted.player.rejoinCalls.get()

            interrupted.player.interrupt(transient = true)
            settle(8_000)
            assertEquals(
                "no rejoin while the other app still holds the output",
                rejoinsBefore,
                interrupted.player.rejoinCalls.get(),
            )
            assertEquals(
                "the room keeps playing for everyone else",
                roomRevision,
                host.store.structure.value.snapshot!!.playback.revision,
            )

            interrupted.player.releaseSuppression()
            settle(5_000)
            assertEquals(
                "exactly one automatic rejoin after the release",
                rejoinsBefore + 1,
                interrupted.player.rejoinCalls.get(),
            )
            assertEquals(
                com.darius.unison.model.LocalPlaybackParticipation.ACTIVE,
                interrupted.player.state.value.participation,
            )
            assertEquals(queue[0], interrupted.player.state.value.queueItemId)
            RoomInvariants.assertNoFailedEvents(host, *guests.toTypedArray())
        }
    }

    @Test
    fun aPermanentInterruptionWaitsForTheUserToRejoin() = runTest {
        withRoom(temporaryFolder, guestCount = 1) { _, host, guests ->
            startPlayback(host, guests, trackCount = 2)
            val guest = guests.single()
            val rejoinsBefore = guest.player.rejoinCalls.get()

            guest.player.interrupt(transient = false)
            settle(15_000)
            assertEquals(
                "becoming-noisy never resumes by itself",
                rejoinsBefore,
                guest.player.rejoinCalls.get(),
            )

            guest.runtime.submit(AppCommand.Play()).await()
            settle(5_000)
            assertEquals(
                "an explicit play rejoins the live room",
                rejoinsBefore + 1,
                guest.player.rejoinCalls.get(),
            )
            RoomInvariants.assertNoFailedEvents(host, guest)
        }
    }

    private suspend fun TestScope.startPlayback(
        host: SimDevice,
        guests: List<SimDevice>,
        trackCount: Int,
    ): List<QueueItemId> {
        val tracks = (1..trackCount).map { host.addLocalTrack("song-$it", durationMs = 30_000L) }
        host.runtime.submit(AppCommand.AddTracks(tracks.map { it.trackId })).await()
        settle(15_000)
        guests.forEach { guest -> tracks.forEach { assertTrue(guest.tracks.has(it.trackId)) } }
        val queue = host.store.structure.value.snapshot!!.queue.map { it.queueItemId }
        host.runtime.submit(AppCommand.PlayQueueItem(queue.first())).await()
        settle(5_000)
        assertTrue(host.store.structure.value.snapshot!!.playback.isPlaying)
        return queue
    }
}
