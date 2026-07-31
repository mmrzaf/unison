package com.darius.unison.playback

import com.darius.unison.model.QueueItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlayerStateEventPolicyTest {
    @Test
    fun `position duration suppression buffering route and speed do not enter room actor`() {
        val base = PlayerState(queueItemId = QueueItemId("item"), playWhenReady = true)
        val telemetryOnly =
            base.copy(
                positionMs = 90_000,
                durationMs = 300_000,
                isPlaying = true,
                locallySuppressed = true,
                playbackSpeed = 1.004f,
                prepared = true,
                buffering = true,
                activityState = PlaybackActivityState.BUFFERING,
                outputRoute = AudioOutputRoute.BLUETOOTH,
            )

        assertEquals(PlayerStateEventPolicy.key(base), PlayerStateEventPolicy.key(telemetryOnly))
    }

    @Test
    fun `canonical player transitions enter room actor`() {
        val base = PlayerState(queueItemId = QueueItemId("item"))
        val changes =
            listOf(
                base.copy(queueItemId = QueueItemId("next")),
                base.copy(playWhenReady = true),
                base.copy(ended = true),
                base.copy(error = "failed"),
                base.copy(seekRevision = 1),
                base.copy(
                    itemTransitionRevision = 1,
                    itemTransitionReason = PlayerItemTransitionReason.AUTO,
                ),
            )

        changes.forEach { changed ->
            assertFalse(PlayerStateEventPolicy.key(base) == PlayerStateEventPolicy.key(changed))
        }
    }
}
