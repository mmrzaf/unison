package com.darius.unison.playback

import com.darius.unison.model.QueueItemId

/**
 * Makes a physical Media3 end-of-item signal idempotent until playback genuinely leaves or
 * restarts the ended item. Reconciliation attempts near the old end must never manufacture a new
 * canonical boundary.
 */
internal class NaturalBoundaryLatch(
    private val replayRearmMarginMs: Long = DEFAULT_REPLAY_REARM_MARGIN_MS,
) {
    private var latchedQueueItemId: QueueItemId? = null

    fun tryLatch(queueItemId: QueueItemId): Boolean {
        if (latchedQueueItemId == queueItemId) return false
        latchedQueueItemId = queueItemId
        return true
    }

    fun onSelectedItemChanged(queueItemId: QueueItemId?) {
        if (queueItemId != latchedQueueItemId) latchedQueueItemId = null
    }

    /** A real replay/seek well before the end permits this item to produce a future boundary. */
    fun onSeek(queueItemId: QueueItemId, positionMs: Long, durationMs: Long) {
        if (latchedQueueItemId != queueItemId || durationMs <= 0L) return
        val latestRearmPosition = (durationMs - replayRearmMarginMs).coerceAtLeast(0L)
        if (positionMs.coerceAtLeast(0L) < latestRearmPosition) latchedQueueItemId = null
    }

    fun clear() {
        latchedQueueItemId = null
    }

    companion object {
        private const val DEFAULT_REPLAY_REARM_MARGIN_MS = 1_000L
    }
}
