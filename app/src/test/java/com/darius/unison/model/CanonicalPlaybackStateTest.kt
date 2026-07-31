package com.darius.unison.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalPlaybackStateTest {
    @Test
    fun `state sync preserves a future play timestamp`() {
        val state =
            CanonicalPlaybackState(
                queueItemId = QueueItemId("item"),
                positionAtTimestampMs = 500,
                coordinatorTimestampNs = 2_000_000_000L,
                isPlaying = true,
            )

        assertEquals(state, state.forStateSync(1_000_000_000L))
    }

    @Test
    fun `state sync materializes an executed playing state`() {
        val state =
            CanonicalPlaybackState(
                queueItemId = QueueItemId("item"),
                positionAtTimestampMs = 500,
                coordinatorTimestampNs = 1_000_000_000L,
                isPlaying = true,
            )

        assertEquals(
            state.copy(positionAtTimestampMs = 1_500, coordinatorTimestampNs = 2_000_000_000L),
            state.forStateSync(2_000_000_000L),
        )
    }

    @Test
    fun `state sync preserves a future pause position`() {
        val state =
            CanonicalPlaybackState(
                queueItemId = QueueItemId("item"),
                positionAtTimestampMs = 4_200,
                coordinatorTimestampNs = 3_000_000_000L,
                isPlaying = false,
            )

        assertEquals(state, state.forStateSync(2_000_000_000L))
    }
}
