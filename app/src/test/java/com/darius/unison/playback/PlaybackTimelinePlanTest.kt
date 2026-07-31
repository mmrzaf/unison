package com.darius.unison.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimelinePlanTest {
    @Test
    fun alreadyAlignedTimelineIsNoOp() {
        assertEquals(
            PlaybackTimelinePlan.Action.NO_OP,
            PlaybackTimelinePlan.decide(
                currentIds = listOf("a", "b"),
                desiredIds = listOf("a", "b"),
                currentId = "a",
                targetId = "a",
                currentPositionMs = 1_000,
                targetPositionMs = 1_180,
                playerIdle = false,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun idleAlignedTimelineStillPrepares() {
        assertEquals(
            PlaybackTimelinePlan.Action.RECONCILE,
            PlaybackTimelinePlan.decide(
                currentIds = listOf("a"),
                desiredIds = listOf("a"),
                currentId = "a",
                targetId = "a",
                currentPositionMs = 0,
                targetPositionMs = 0,
                playerIdle = true,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun emptyAlreadyIdleTimelineIsNoOp() {
        assertEquals(
            PlaybackTimelinePlan.Action.NO_OP,
            PlaybackTimelinePlan.decide(
                currentIds = emptyList(),
                desiredIds = emptyList(),
                currentId = null,
                targetId = null,
                currentPositionMs = 0,
                targetPositionMs = 0,
                playerIdle = true,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun smallTimelineMutationPatches() {
        assertEquals(
            PlaybackTimelinePlan.Action.PATCH,
            PlaybackTimelinePlan.decide(
                currentIds = listOf("a", "b"),
                desiredIds = listOf("a", "c", "b"),
                currentId = "a",
                targetId = "a",
                currentPositionMs = 0,
                targetPositionMs = 0,
                playerIdle = false,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun largeTimelineMutationRebuilds() {
        val current = (0 until 40).map { "old-$it" }
        val desired = (0 until 40).map { "new-$it" }
        assertEquals(
            PlaybackTimelinePlan.Action.REBUILD,
            PlaybackTimelinePlan.decide(
                currentIds = current,
                desiredIds = desired,
                currentId = current.first(),
                targetId = desired.first(),
                currentPositionMs = 0,
                targetPositionMs = 0,
                playerIdle = false,
                playWhenReady = false,
            ),
        )
    }
}
