package com.darius.unison.room

import com.darius.unison.model.AppCommand
import com.darius.unison.model.PeerId
import com.darius.unison.model.UserCommand
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportIntentCoordinatorTest {
    @Test
    fun latestPlayPauseIntentWinsBeforeCanonicalMutation() = runBlocking {
        val coordinator =
            TransportIntentCoordinator(playPauseDebounceMs = 20L, seekDebounceMs = 20L)
        val play = async { coordinator.awaitLatest(AppCommand.Play("play")) }
        delay(2L)
        val pause = async { coordinator.awaitLatest(AppCommand.Pause("pause")) }

        assertFalse(play.await())
        assertTrue(pause.await())
    }

    @Test
    fun rapidSeekKeepsOnlyFinalAbsoluteTarget() = runBlocking {
        val coordinator =
            TransportIntentCoordinator(playPauseDebounceMs = 10L, seekDebounceMs = 20L)
        val first = async { coordinator.awaitLatest(AppCommand.Seek(1_000L, "seek-1")) }
        delay(2L)
        val second = async { coordinator.awaitLatest(AppCommand.Seek(9_000L, "seek-2")) }

        assertFalse(first.await())
        assertTrue(second.await())
    }

    @Test
    fun navigationRemainsDiscrete() = runBlocking {
        val coordinator =
            TransportIntentCoordinator(playPauseDebounceMs = 20L, seekDebounceMs = 20L)
        assertTrue(coordinator.awaitLatest(AppCommand.SkipNext("next")))
        assertTrue(coordinator.awaitLatest(AppCommand.SkipPrevious("previous")))
    }

    @Test
    fun invalidationSupersedesWaitingIntent() = runBlocking {
        val coordinator =
            TransportIntentCoordinator(playPauseDebounceMs = 20L, seekDebounceMs = 20L)
        val play = async { coordinator.awaitLatest(AppCommand.Play("play")) }
        delay(2L)
        coordinator.invalidateAll()
        assertFalse(play.await())
    }

    @Test
    fun remoteAndLocalPlayPauseShareOneLatestIntentLane() = runBlocking {
        val coordinator =
            TransportIntentCoordinator(playPauseDebounceMs = 20L, seekDebounceMs = 20L)
        val peer = PeerId("peer-remote-12345")
        val remotePlay = async { coordinator.awaitLatest(UserCommand.Play("remote-play", peer)) }
        delay(2L)
        val localPause = async { coordinator.awaitLatest(AppCommand.Pause("local-pause")) }

        assertFalse(remotePlay.await())
        assertTrue(localPause.await())
        assertFalse(coordinator.isLatest(UserCommand.Play("remote-play", peer)))
    }

    @Test
    fun actorSideLatestCheckClosesPostDebounceRace() = runBlocking {
        val coordinator = TransportIntentCoordinator(playPauseDebounceMs = 1L, seekDebounceMs = 1L)
        val peer = PeerId("peer-remote-12345")
        val first = UserCommand.Play("first", peer)
        assertTrue(coordinator.awaitLatest(first))

        val newer = async { coordinator.awaitLatest(UserCommand.Pause("newer", peer)) }
        delay(1L)

        assertFalse(coordinator.isLatest(first))
        assertTrue(newer.await())
    }
}
