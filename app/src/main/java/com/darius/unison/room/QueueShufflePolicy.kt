package com.darius.unison.room

import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId

/** Pure one-shot queue shuffle policy. Played items and the current item never move. */
object QueueShufflePolicy {
    fun canShuffle(queue: List<QueueItem>, currentId: QueueItemId?): Boolean =
        queue.size - fixedCount(queue, currentId) >= 2

    fun shuffledOrder(
        queue: List<QueueItem>,
        currentId: QueueItemId?,
        seed: Long,
        preserveNextQueueItemId: QueueItemId? = null,
    ): List<QueueItemId>? {
        val currentFixedCount = fixedCount(queue, currentId)
        val preserveNext =
            preserveNextQueueItemId != null &&
                queue.getOrNull(currentFixedCount)?.queueItemId == preserveNextQueueItemId
        val fixedCount = currentFixedCount + if (preserveNext) 1 else 0
        if (queue.size - fixedCount < 2) return null

        val fixed = queue.take(fixedCount).map { it.queueItemId }
        val originalFuture = queue.drop(fixedCount).map { it.queueItemId }
        val future = originalFuture.toMutableList()
        var state = seed.takeIf { it != 0L } ?: DEFAULT_SEED

        fun nextLong(): Long {
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)
            return state
        }

        for (index in future.lastIndex downTo 1) {
            val selected = ((nextLong() ushr 1) % (index + 1).toLong()).toInt()
            val tmp = future[index]
            future[index] = future[selected]
            future[selected] = tmp
        }

        // A shuffle button must visibly shuffle. Fisher-Yates can legally return the identity
        // permutation, so rotate once in that rare case while preserving the fixed prefix.
        if (future == originalFuture) {
            future.add(future.removeAt(0))
        }
        return fixed + future
    }

    private fun fixedCount(queue: List<QueueItem>, currentId: QueueItemId?): Int {
        val currentIndex = queue.indexOfFirst { it.queueItemId == currentId }
        return if (currentIndex >= 0) currentIndex + 1 else 0
    }

    private const val DEFAULT_SEED = 0x6A09E667F3BCC909L
}
