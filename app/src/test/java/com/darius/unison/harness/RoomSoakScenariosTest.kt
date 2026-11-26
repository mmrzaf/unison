package com.darius.unison.harness

import com.darius.unison.model.AppCommand
import com.darius.unison.model.RepeatMode
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * S16: seeded random room traffic and faults on virtual time. The release gate runs a handful of
 * seeds ([SEEDS_PROPERTY], [MINUTES_PROPERTY]); every run must converge and fail no room event.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomSoakScenariosTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun seededChaosConvergesWithoutFailedEvents() {
        val seeds =
            (System.getProperty(SEEDS_PROPERTY) ?: DEFAULT_SEEDS).split(",").map {
                it.trim().toLong()
            }
        val minutes = (System.getProperty(MINUTES_PROPERTY) ?: DEFAULT_MINUTES).toInt()
        seeds.forEach { seed -> runSoak(seed, minutes) }
    }

    @Test
    fun theSameSeedProducesTheSameRoomHistory() {
        val first = runSoak(seed = 7L, minutes = 2)
        val second = runSoak(seed = 7L, minutes = 2)
        assertEquals("the simulation is deterministic", first, second)
    }

    /** Runs one seeded soak in its own test scope and returns a hash of every device's history. */
    private fun runSoak(seed: Long, minutes: Int): Int {
        var trace = 0
        val folder = temporaryFolder.newFolder("soak-$seed-${System.nanoTime()}")
        runTest {
            val holder = TemporaryFolder(folder).also { it.create() }
            withRoom(holder, guestCount = GUESTS) { harness, host, guests ->
                val random = Random(seed)
                val everyone = listOf(host) + guests
                val tracks =
                    (1..TRACKS).map { host.addLocalTrack("soak-$it", durationMs = 45_000L) }
                host.runtime.submit(AppCommand.AddTracks(tracks.take(4).map { it.trackId })).await()
                var lastRevision = everyone.map { 0L }.toMutableList()
                val endMs = testScheduler.currentTime + minutes * 60_000L
                while (testScheduler.currentTime < endMs) {
                    chaosStep(random, harness, host, guests, tracks.map { it.trackId })
                    settle(random.nextLong(500, 4_000))
                    everyone.forEachIndexed { index, device ->
                        val revision =
                            device.store.structure.value.snapshot?.sequence ?: return@forEachIndexed
                        assertTrue(
                            "seed $seed: device ${device.index} moved its canonical sequence backwards",
                            revision >= lastRevision[index],
                        )
                        lastRevision[index] = revision
                    }
                }

                harness.transfers.healLinks()
                settle(60_000)
                guests.forEach { guest ->
                    if (guest.roomId() == null) joinRoom(guest, host)
                }
                settle(60_000)

                val hostSnapshot = host.store.structure.value.snapshot!!
                assertTrue(
                    "seed $seed: the chaos actually changed the room (sequence ${hostSnapshot.sequence})",
                    hostSnapshot.sequence >= minutes,
                )
                guests.forEach { guest ->
                    val snapshot = guest.store.structure.value.snapshot
                    assertEquals(
                        "seed $seed: guest ${guest.index} converged",
                        hostSnapshot.sequence,
                        snapshot?.sequence,
                    )
                    assertEquals(
                        hostSnapshot.queue.map { it.queueItemId },
                        snapshot?.queue?.map { it.queueItemId },
                    )
                    assertEquals(hostSnapshot.playback.queueItemId, snapshot?.playback?.queueItemId)
                    assertEquals(hostSnapshot.playback.isPlaying, snapshot?.playback?.isPlaying)
                }
                assertEquals("seed $seed: everyone is a member", everyone.size, host.memberCount())
                RoomInvariants.assertNoFailedEvents(*everyone.toTypedArray())
                trace = everyone.sumOf { device ->
                    device.log.snapshot().map { it.eventName }.hashCode()
                }
            }
        }
        return trace
    }

    private suspend fun TestScope.chaosStep(
        random: Random,
        harness: RoomHarness,
        host: SimDevice,
        guests: List<SimDevice>,
        trackIds: List<com.darius.unison.model.TrackId>,
    ) {
        val snapshot = host.store.structure.value.snapshot ?: return
        val queue = snapshot.queue.map { it.queueItemId }
        val guest = guests.random(random)
        when (random.nextInt(18)) {
            0,
            1 ->
                queue.randomOrNull(random)?.let {
                    host.runtime.submit(AppCommand.PlayQueueItem(it))
                }
            2 -> host.runtime.submit(AppCommand.Pause())
            3 -> host.runtime.submit(AppCommand.Play())
            4 -> host.runtime.submit(AppCommand.Seek(positionMs = random.nextLong(0, 40_000)))
            5 -> host.runtime.submit(AppCommand.SkipNext())
            6 -> host.runtime.submit(AppCommand.SkipPrevious())
            7 -> host.runtime.submit(AppCommand.AddTracks(listOf(trackIds.random(random))))
            8 ->
                queue.randomOrNull(random)?.let {
                    host.runtime.submit(AppCommand.RemoveQueueItem(it))
                }
            9 ->
                queue.randomOrNull(random)?.let {
                    host.runtime.submit(AppCommand.MoveQueueItem(it, random.nextInt(queue.size)))
                }
            10 -> host.runtime.submit(AppCommand.ShuffleQueue)
            11 -> host.runtime.submit(AppCommand.SetRepeat(RepeatMode.entries.random(random)))
            12 -> guest.player.interrupt(transient = random.nextBoolean())
            13 -> guest.player.releaseSuppression()
            14 -> host.player.endNaturally()
            15 -> harness.hub.partitionAll()
            16 -> {
                guest.runtime.submit(AppCommand.LeaveRoom).await()
                settle(500)
                joinRoom(guest, host)
            }
            else -> harness.transfers.blockLink(host.address, guest.address)
        }
    }

    private companion object {
        const val GUESTS = 3
        const val TRACKS = 8
        const val SEEDS_PROPERTY = "unison.soak.seeds"
        const val MINUTES_PROPERTY = "unison.soak.minutes"
        const val DEFAULT_SEEDS = "1,2,3"
        const val DEFAULT_MINUTES = "5"
    }
}
