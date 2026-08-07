package com.darius.unison.room

import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueShufflePolicyTest {
    private val peer = PeerId("peer-a")

    @Test
    fun requiresAtLeastTwoUpcomingItems() {
        val queue = queue(3)
        assertTrue(QueueShufflePolicy.canShuffle(queue, queue[0].queueItemId))
        assertFalse(QueueShufflePolicy.canShuffle(queue, queue[1].queueItemId))
        assertFalse(QueueShufflePolicy.canShuffle(queue, queue[2].queueItemId))
    }

    @Test
    fun keepsPlayedAndCurrentPrefixFixed() {
        val queue = queue(8)
        val order = QueueShufflePolicy.shuffledOrder(queue, queue[2].queueItemId, 42L)!!
        assertEquals(queue.take(3).map { it.queueItemId }, order.take(3))
        assertTrue(queue.drop(3).map { it.queueItemId } != order.drop(3))
        assertEquals(queue.map { it.queueItemId }.toSet(), order.toSet())
    }

    @Test
    fun sameSeedProducesSameOrder() {
        val queue = queue(8)
        assertEquals(
            QueueShufflePolicy.shuffledOrder(queue, queue[1].queueItemId, 77L),
            QueueShufflePolicy.shuffledOrder(queue, queue[1].queueItemId, 77L),
        )
    }

    @Test
    fun noCurrentItemShufflesTheWholeQueue() {
        val queue = queue(5)
        val order = QueueShufflePolicy.shuffledOrder(queue, null, 1L)!!
        assertTrue(queue.map { it.queueItemId } != order)
        assertEquals(queue.map { it.queueItemId }.toSet(), order.toSet())
    }

    private fun queue(count: Int): List<QueueItem> =
        List(count) { index ->
            QueueItem.create(
                TrackDescriptor(
                    trackId = TrackId(index.toString(16).padStart(64, '0')),
                    sizeBytes = 1_024,
                    title = "Track $index",
                ),
                peer,
                index.toLong(),
            )
        }
}
