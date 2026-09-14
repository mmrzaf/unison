package com.darius.unison.room

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darius.unison.app.normalizeDisplayName
import com.darius.unison.app.unisonContainer
import com.darius.unison.model.AppCommand
import com.darius.unison.model.QueueItemId
import com.darius.unison.playback.LocalPlayableItem
import com.darius.unison.playback.PlaybackActivityState
import com.darius.unison.playback.PlaybackPauseCause
import com.darius.unison.playback.PlaybackSample
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRuntimeIdentityAndroidTest {
    @Test
    fun saveDisplayNameUpdatesPersistedAndRuntimeIdentityWithoutChangingPeerId() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = context.unisonContainer
        val original = container.settings.ensureIdentity()
        val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val runtime = RoomRuntime(context, container, FakePlayer(), runtimeScope)
        val rawName = "\u0000  Beta Seven ${"X".repeat(80)}  "
        val expectedName = normalizeDisplayName(rawName)

        try {
            withTimeout(COMMAND_TIMEOUT_MS) {
                runtime.handle(AppCommand.SaveDisplayName(rawName))
            }

            val persisted = container.settings.ensureIdentity()
            val published = container.roomStore.structure.value.localIdentity
            assertEquals(original.peerId, persisted.peerId)
            assertEquals(expectedName, persisted.displayName)
            assertEquals(persisted, published)
        } finally {
            runtime.close()
            runtimeScope.cancel()
            container.settings.saveDisplayName(original.displayName)
            val restored = container.settings.ensureIdentity()
            container.roomStore.update { it.copy(localIdentity = restored) }
        }
    }

    private companion object {
        const val COMMAND_TIMEOUT_MS = 10_000L
    }

    private class FakePlayer : PlayerPort {
        private val mutableState = MutableStateFlow(PlayerState())
        override val state: StateFlow<PlayerState> = mutableState

        override suspend fun samplePlayback(): PlaybackSample {
            val current = mutableState.value
            return PlaybackSample(
                queueItemId = current.queueItemId,
                positionMs = current.positionMs,
                durationMs = current.durationMs,
                sampledAtLocalNs = System.nanoTime(),
                playWhenReady = current.playWhenReady,
                isPlaying = current.isPlaying,
                activityState = current.activityState,
                playbackSpeed = current.playbackSpeed,
                outputRoute = current.outputRoute,
                seekRevision = current.seekRevision,
            )
        }

        override suspend fun setQueue(
            items: List<LocalPlayableItem>,
            currentQueueItemId: QueueItemId?,
            positionMs: Long,
        ) {
            mutableState.value =
                mutableState.value.copy(
                    queueItemId = currentQueueItemId,
                    positionMs = positionMs,
                    activityState = PlaybackActivityState.READY_PAUSED,
                    prepared = items.isNotEmpty(),
                )
        }

        override suspend fun play(): Boolean = true

        override suspend fun rejoinLivePlayback(
            queueItemId: QueueItemId,
            positionMs: Long,
        ): Boolean = true

        override suspend fun resetLocalPlaybackParticipation() = Unit

        override suspend fun pause(cause: PlaybackPauseCause) = Unit

        override suspend fun seekTo(positionMs: Long) {
            mutableState.value = mutableState.value.copy(positionMs = positionMs)
        }

        override suspend fun seekToItem(queueItemId: QueueItemId, positionMs: Long): Boolean {
            mutableState.value =
                mutableState.value.copy(queueItemId = queueItemId, positionMs = positionMs)
            return true
        }

        override suspend fun setRepeatCurrentItem(enabled: Boolean) = Unit

        override suspend fun setPlaybackSpeed(speed: Float) {
            mutableState.value = mutableState.value.copy(playbackSpeed = speed)
        }
    }
}
