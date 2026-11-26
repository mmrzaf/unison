package com.darius.unison.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises natural-boundary attribution against the actual pinned Media3 ExoPlayer rather than a
 * callback-order fake. These tests guard the dual END_OF_MEDIA_ITEM / media-item-transition paths
 * in [Media3PlayerAdapter].
 */
@RunWith(AndroidJUnit4::class)
class Media3NaturalBoundaryIntegrationTest {
    @Test
    fun twoItemBoundaryRemainsOwnedByFirstAcrossResumeTransition() = runBlocking {
        withMedia3Adapter { fixture ->
            val first = fixture.item("first", durationMs = 1_300)
            val second = fixture.item("second", durationMs = 4_000)
            fixture.adapter.setQueue(listOf(first, second), first.queueItemId, 0)

            val startRevision = fixture.adapter.state.value.itemBoundaryRevision
            assertTrue(fixture.adapter.play())
            val boundary = fixture.awaitBoundaryAfter(startRevision)
            assertEquals(first.queueItemId, boundary.boundaryEndedQueueItemId)

            val transitionRevision = boundary.itemTransitionRevision
            assertTrue(fixture.adapter.play())
            val transitioned =
                withTimeout(BOUNDARY_TIMEOUT_MS) {
                    fixture.adapter.state.first {
                        it.queueItemId == second.queueItemId &&
                            it.itemTransitionRevision > transitionRevision
                    }
                }
            assertEquals(second.queueItemId, transitioned.queueItemId)

            // The resume transition may report AUTO for the item that already ended, but must not
            // manufacture a second boundary or misattribute that boundary to the newly selected
            // item.
            delay(NO_DUPLICATE_SETTLE_MS)
            assertEquals(
                boundary.itemBoundaryRevision,
                fixture.adapter.state.value.itemBoundaryRevision,
            )
            assertEquals(first.queueItemId, fixture.adapter.state.value.boundaryEndedQueueItemId)
        }
    }

    @Test
    fun finalItemProducesExactlyOneBoundary() = runBlocking {
        withMedia3Adapter { fixture ->
            val only = fixture.item("only", durationMs = 1_300)
            fixture.adapter.setQueue(listOf(only), only.queueItemId, 0)

            val startRevision = fixture.adapter.state.value.itemBoundaryRevision
            assertTrue(fixture.adapter.play())
            val boundary = fixture.awaitBoundaryAfter(startRevision)
            assertEquals(only.queueItemId, boundary.boundaryEndedQueueItemId)

            delay(NO_DUPLICATE_SETTLE_MS)
            assertEquals(
                boundary.itemBoundaryRevision,
                fixture.adapter.state.value.itemBoundaryRevision,
            )
            assertEquals(only.queueItemId, fixture.adapter.state.value.boundaryEndedQueueItemId)
        }
    }

    @Test
    fun repeatOneBoundaryStaysOnRepeatedItemAndCanRearm() = runBlocking {
        withMedia3Adapter { fixture ->
            val repeated = fixture.item("repeat", durationMs = 1_400)
            fixture.adapter.setQueue(listOf(repeated), repeated.queueItemId, 0)
            fixture.adapter.setRepeatCurrentItem(true)

            assertTrue(fixture.adapter.play())
            val firstBoundary = fixture.awaitBoundaryAfter(0)
            assertEquals(repeated.queueItemId, firstBoundary.boundaryEndedQueueItemId)

            // Canonical replay/repeat work positions the item back before the re-arm margin. The
            // same physical item must then be allowed to produce one future natural boundary.
            fixture.adapter.seekTo(0)
            assertTrue(fixture.adapter.play())
            val secondBoundary = fixture.awaitBoundaryAfter(firstBoundary.itemBoundaryRevision)
            assertEquals(repeated.queueItemId, secondBoundary.boundaryEndedQueueItemId)
            assertEquals(
                firstBoundary.itemBoundaryRevision + 1,
                secondBoundary.itemBoundaryRevision,
            )
        }
    }

    @Test
    fun explicitReplaySeekRearmsFinalBoundary() = runBlocking {
        withMedia3Adapter { fixture ->
            val only = fixture.item("replay", durationMs = 1_400)
            fixture.adapter.setQueue(listOf(only), only.queueItemId, 0)

            assertTrue(fixture.adapter.play())
            val firstBoundary = fixture.awaitBoundaryAfter(0)
            assertEquals(only.queueItemId, firstBoundary.boundaryEndedQueueItemId)

            fixture.adapter.seekTo(0)
            assertTrue(fixture.adapter.play())
            val secondBoundary = fixture.awaitBoundaryAfter(firstBoundary.itemBoundaryRevision)
            assertEquals(only.queueItemId, secondBoundary.boundaryEndedQueueItemId)
            assertEquals(
                firstBoundary.itemBoundaryRevision + 1,
                secondBoundary.itemBoundaryRevision,
            )
        }
    }

    @Test
    fun playlistMutationBeforeBoundaryDoesNotInventEndedItem() = runBlocking {
        withMedia3Adapter { fixture ->
            val first = fixture.item("mutating-first", durationMs = 1_600)
            val second = fixture.item("mutating-second", durationMs = 4_000)
            val inserted = fixture.item("inserted", durationMs = 4_000)
            fixture.adapter.setQueue(listOf(first, second), first.queueItemId, 0)
            assertTrue(fixture.adapter.play())

            delay(QUEUE_MUTATION_LEAD_MS)
            val livePosition = fixture.adapter.samplePlayback().positionMs
            fixture.adapter.setQueue(
                listOf(first, inserted, second),
                first.queueItemId,
                livePosition,
            )

            // PLAYLIST_CHANGED must not itself look like a natural end.
            delay(QUEUE_MUTATION_SETTLE_MS)
            assertEquals(0L, fixture.adapter.state.value.itemBoundaryRevision)

            val boundary = fixture.awaitBoundaryAfter(0)
            assertEquals(first.queueItemId, boundary.boundaryEndedQueueItemId)
        }
    }

    private companion object {
        const val NO_DUPLICATE_SETTLE_MS = 300L
        const val QUEUE_MUTATION_LEAD_MS = 350L
        const val QUEUE_MUTATION_SETTLE_MS = 150L
    }
}
