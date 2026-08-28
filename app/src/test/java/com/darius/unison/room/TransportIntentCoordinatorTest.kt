package com.darius.unison.room

import com.darius.unison.model.AppCommand
import com.darius.unison.model.PeerId
import com.darius.unison.model.UserCommand
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportIntentCoordinatorTest {
    @Test
    fun latestPlayPauseIntentWinsWithSingleOwner() = runBlocking {
        val fixture = fixture(playPauseDebounceMs = 20L)
        try {
            fixture.submit(AppCommand.Play("play"))
            delay(2L)
            fixture.submit(AppCommand.Pause("pause"))

            fixture.awaitAccepted(1)
            fixture.awaitSuperseded(1)
            assertEquals(listOf("pause"), fixture.accepted)
            assertEquals(listOf("play"), fixture.superseded)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun rapidSeekKeepsOnlyFinalAbsoluteTarget() = runBlocking {
        val fixture = fixture(seekDebounceMs = 20L)
        try {
            fixture.submit(AppCommand.Seek(1_000L, "seek-1"))
            delay(2L)
            fixture.submit(AppCommand.Seek(9_000L, "seek-2"))

            fixture.awaitAccepted(1)
            fixture.awaitSuperseded(1)
            assertEquals(listOf("seek-2"), fixture.accepted)
            assertEquals(listOf("seek-1"), fixture.superseded)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun navigationIsAnOrderingBarrierWithoutConcurrentWorkers() = runBlocking {
        val fixture = fixture(playPauseDebounceMs = 50L)
        try {
            fixture.submit(AppCommand.Play("play"))
            fixture.submit(AppCommand.Pause("pause"))
            fixture.submit(AppCommand.SkipNext("next"))

            fixture.awaitAccepted(2)
            fixture.awaitSuperseded(1)
            assertEquals(listOf("pause", "next"), fixture.accepted)
            assertEquals(listOf("play"), fixture.superseded)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun remoteAndLocalPlayPauseShareTheSameLane() = runBlocking {
        val fixture = fixture(playPauseDebounceMs = 20L)
        val peer = PeerId("peer-remote-12345")
        try {
            fixture.coordinator.submit(1L, UserCommand.Play("remote-play", peer))
            delay(2L)
            fixture.submit(AppCommand.Pause("local-pause"))

            fixture.awaitAccepted(1)
            fixture.awaitSuperseded(1)
            assertEquals(listOf("local-pause"), fixture.accepted)
            assertEquals(listOf("remote-play"), fixture.superseded)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun invalidationSupersedesPendingIntent() = runBlocking {
        val fixture = fixture(playPauseDebounceMs = 50L)
        try {
            fixture.submit(AppCommand.Play("play"))
            fixture.coordinator.invalidateAll()

            fixture.awaitSuperseded(1)
            assertEquals(emptyList<String>(), fixture.accepted)
            assertEquals(listOf("play"), fixture.superseded)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun ingressRemainsBoundedWhileAcceptedCommandIsRunning() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val coordinator =
            TransportIntentCoordinator(
                scope = scope,
                capacity = 1,
                onAccepted = { intent ->
                    if (intent.commandId == "first") {
                        started.complete(Unit)
                        release.await()
                    }
                    (intent as? TransportIntentCoordinator.Intent.Local)?.completion?.complete(Unit)
                },
                onSuperseded = { intent ->
                    (intent as? TransportIntentCoordinator.Intent.Local)?.completion?.complete(Unit)
                },
            )
        try {
            assertTrue(coordinator.submit(AppCommand.SkipNext("first"), CompletableDeferred()))
            started.await()
            assertTrue(coordinator.submit(AppCommand.SkipNext("queued"), CompletableDeferred()))
            assertFalse(coordinator.submit(AppCommand.SkipNext("overflow"), CompletableDeferred()))
            release.complete(Unit)
            Unit
        } finally {
            coordinator.close()
            scope.cancel()
        }
    }

    private fun fixture(
        playPauseDebounceMs: Long = 10L,
        seekDebounceMs: Long = 10L,
    ): Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val accepted = Collections.synchronizedList(mutableListOf<String>())
        val superseded = Collections.synchronizedList(mutableListOf<String>())
        val coordinator =
            TransportIntentCoordinator(
                scope = scope,
                playPauseDebounceMs = playPauseDebounceMs,
                seekDebounceMs = seekDebounceMs,
                onAccepted = { intent ->
                    accepted += intent.commandId
                    (intent as? TransportIntentCoordinator.Intent.Local)?.completion?.complete(Unit)
                },
                onSuperseded = { intent ->
                    superseded += intent.commandId
                    (intent as? TransportIntentCoordinator.Intent.Local)?.completion?.complete(Unit)
                },
            )
        return Fixture(scope, coordinator, accepted, superseded)
    }

    private class Fixture(
        private val scope: CoroutineScope,
        val coordinator: TransportIntentCoordinator,
        val accepted: MutableList<String>,
        val superseded: MutableList<String>,
    ) {
        fun submit(command: AppCommand.Transport) {
            coordinator.submit(command, CompletableDeferred())
        }

        suspend fun awaitAccepted(count: Int) =
            withTimeout(2_000L) {
                while (accepted.size < count) delay(2L)
            }

        suspend fun awaitSuperseded(count: Int) =
            withTimeout(2_000L) {
                while (superseded.size < count) delay(2L)
            }

        fun close() {
            coordinator.close()
            scope.cancel()
        }
    }
}
