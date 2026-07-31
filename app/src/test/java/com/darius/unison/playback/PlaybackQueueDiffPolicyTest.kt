package com.darius.unison.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueDiffPolicyTest {
    @Test
    fun metadataOnlyRefreshStaysIncremental() {
        assertFalse(PlaybackQueueDiffPolicy.shouldRebuild(listOf("a", "b"), listOf("a", "b")))
    }

    @Test
    fun smallMoveStaysIncremental() {
        assertFalse(
            PlaybackQueueDiffPolicy.shouldRebuild(
                listOf("a", "b", "c"),
                listOf("b", "a", "c"),
            )
        )
    }

    @Test
    fun largeShuffleUsesSingleRebuild() {
        val current = (0 until 1_000).map(Int::toString)
        assertTrue(PlaybackQueueDiffPolicy.shouldRebuild(current, current.reversed()))
    }

    @Test
    fun largeBulkAppendUsesSingleRebuild() {
        assertTrue(
            PlaybackQueueDiffPolicy.shouldRebuild(
                listOf("a"),
                (0 until 100).map(Int::toString),
            )
        )
    }
}
