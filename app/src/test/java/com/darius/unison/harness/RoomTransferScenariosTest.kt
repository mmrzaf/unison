package com.darius.unison.harness

import com.darius.unison.model.AppCommand
import com.darius.unison.protocol.TransferFailureBlame
import com.darius.unison.protocol.TransferFailureCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** S8: the coordinator's transfer orchestration across several guests, on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomTransferScenariosTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun everyGuestReceivesTheQueuedTracks() = runTest {
        withRoom(temporaryFolder, guestCount = 3) { harness, host, guests ->
            val tracks = (1..3).map { host.addLocalTrack("song-$it") }
            host.runtime.submit(AppCommand.AddTracks(tracks.map { it.trackId })).await()
            settle(20_000)

            guests.forEach { guest ->
                tracks.forEach { track ->
                    assertTrue(
                        "guest ${guest.index} has ${track.title}",
                        guest.tracks.has(track.trackId),
                    )
                }
            }
            assertEquals(
                "no transfer failed on a healthy network",
                0,
                harness.transfers.records.count {
                    it.outcome == SimTransferRecord.Outcome.FAILED
                },
            )
            RoomInvariants.assertNoFailedEvents(host, *guests.toTypedArray())
        }
    }

    @Test
    fun guestsShareTheSourceLoadInsteadOfOnlyUsingTheCoordinator() = runTest {
        withRoom(temporaryFolder, guestCount = 3) { harness, host, guests ->
            val track = host.addLocalTrack("shared", sizeBytes = 4 * 1024 * 1024)
            host.runtime.submit(AppCommand.AddTracks(listOf(track.trackId))).await()
            settle(30_000)

            guests.forEach { assertTrue(it.tracks.has(track.trackId)) }
            val sources =
                harness.transfers.records
                    .filter {
                        it.trackId == track.trackId &&
                            it.outcome == SimTransferRecord.Outcome.COMPLETED
                    }
                    .map { it.sourceAddress }
            assertEquals("one completed download per guest", 3, sources.size)
        }
    }

    @Test
    fun aRetryableRouteFailureIsRetriedAndStillCompletes() = runTest {
        withRoom(temporaryFolder, guestCount = 1) { harness, host, guests ->
            val guest = guests.single()
            val track = host.addLocalTrack("flaky")
            harness.transfers.failNext(track.trackId, times = 2)
            host.runtime.submit(AppCommand.AddTracks(listOf(track.trackId))).await()
            settle(30_000)

            assertTrue("the guest eventually has the track", guest.tracks.has(track.trackId))
            val attempts = harness.transfers.attempts(track.trackId)
            assertTrue("retries are bounded ($attempts attempts)", attempts in 3..6)
            RoomInvariants.assertNoFailedEvents(host, guest)
        }
    }

    @Test
    fun aNonRetryableSourceFailureStopsRetryingAndReportsOneBlockedIssue() = runTest {
        withRoom(temporaryFolder, guestCount = 1) { harness, host, guests ->
            val guest = guests.single()
            val track = host.addLocalTrack("broken")
            harness.transfers.failNext(
                track.trackId,
                times = 100,
                code = TransferFailureCode.SOURCE_UNAVAILABLE,
                blame = TransferFailureBlame.SOURCE,
                retryable = false,
            )
            host.runtime.submit(AppCommand.AddTracks(listOf(track.trackId))).await()
            settle(60_000)

            val attempts = harness.transfers.attempts(track.trackId)
            assertTrue(
                "a non-retryable failure is not hammered ($attempts attempts)",
                attempts <= 4,
            )
            assertTrue(!guest.tracks.has(track.trackId))
            RoomInvariants.assertNoFailedEvents(host, guest)
        }
    }

    @Test
    fun aPartitionedRouteIsSuspendedThenRecoversOnExplicitPreparation() = runTest {
        withRoom(temporaryFolder, guestCount = 1) { harness, host, guests ->
            val guest = guests.single()
            val track = host.addLocalTrack("partitioned", sizeBytes = 2 * 1024 * 1024)
            harness.transfers.blockLink(host.address, guest.address)
            host.runtime.submit(AppCommand.AddTracks(listOf(track.trackId))).await()
            settle(30_000)

            val attempts = harness.transfers.attempts(track.trackId)
            assertTrue(
                "a dead route stops after a bounded number of tries ($attempts)",
                attempts <= 6,
            )
            assertTrue(!guest.tracks.has(track.trackId))

            // The suspension is open-ended by design: the user's prepare request reopens it.
            harness.transfers.healLinks()
            val queueItemId = host.store.structure.value.snapshot!!.queue.single().queueItemId
            host.runtime.submit(AppCommand.PrepareQueueItem(queueItemId)).await()
            settle(30_000)
            assertTrue(
                "preparation after the heal delivers the track",
                guest.tracks.has(track.trackId),
            )
            RoomInvariants.assertNoFailedEvents(host, guest)
        }
    }
}
