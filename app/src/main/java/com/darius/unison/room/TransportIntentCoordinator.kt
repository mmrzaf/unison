package com.darius.unison.room

import com.darius.unison.model.AppCommand
import com.darius.unison.model.UserCommand
import kotlinx.coroutines.delay

/**
 * Coalesces high-frequency transport intent before it reaches canonical room state.
 *
 * Play/Pause share one latest-intent lane and Seek has its own lane. Navigation remains discrete:
 * repeated Next/Previous presses intentionally advance more than one item.
 */
class TransportIntentCoordinator(
    private val playPauseDebounceMs: Long = 45L,
    private val seekDebounceMs: Long = 90L,
) {
    private val lock = Any()
    private var playPauseGeneration = 0L
    private var seekGeneration = 0L
    private var latestPlayPauseCommandId: String? = null
    private var latestSeekCommandId: String? = null

    suspend fun awaitLatest(command: AppCommand.Transport): Boolean {
        return awaitLatest(command.commandId, lane(command))
    }

    suspend fun awaitLatest(command: UserCommand): Boolean {
        return awaitLatest(command.commandId, lane(command))
    }

    fun isLatest(command: UserCommand): Boolean {
        val lane = lane(command) ?: return true
        return synchronized(lock) {
            when (lane) {
                Lane.PLAY_PAUSE -> latestPlayPauseCommandId == command.commandId
                Lane.SEEK -> latestSeekCommandId == command.commandId
            }
        }
    }

    private suspend fun awaitLatest(commandId: String, lane: Lane?): Boolean {
        lane ?: return true
        val generation =
            synchronized(lock) {
                when (lane) {
                    Lane.PLAY_PAUSE -> {
                        latestPlayPauseCommandId = commandId
                        ++playPauseGeneration
                    }

                    Lane.SEEK -> {
                        latestSeekCommandId = commandId
                        ++seekGeneration
                    }
                }
            }
        delay(if (lane == Lane.SEEK) seekDebounceMs else playPauseDebounceMs)
        return synchronized(lock) {
            when (lane) {
                Lane.PLAY_PAUSE ->
                    generation == playPauseGeneration && latestPlayPauseCommandId == commandId
                Lane.SEEK -> generation == seekGeneration && latestSeekCommandId == commandId
            }
        }
    }

    fun invalidateAll() =
        synchronized(lock) {
            playPauseGeneration++
            seekGeneration++
            latestPlayPauseCommandId = null
            latestSeekCommandId = null
        }

    private fun lane(command: AppCommand.Transport): Lane? =
        when (command) {
            is AppCommand.Play,
            is AppCommand.Pause -> Lane.PLAY_PAUSE
            is AppCommand.Seek -> Lane.SEEK
            is AppCommand.SkipNext,
            is AppCommand.SkipPrevious,
            is AppCommand.PlayQueueItem -> null
        }

    private fun lane(command: UserCommand): Lane? =
        when (command) {
            is UserCommand.Play,
            is UserCommand.Pause -> Lane.PLAY_PAUSE
            is UserCommand.Seek -> Lane.SEEK
            is UserCommand.SkipNext,
            is UserCommand.SkipPrevious,
            is UserCommand.PlayQueueItem,
            is UserCommand.QueueAdd,
            is UserCommand.QueueRemove,
            is UserCommand.QueueMove,
            is UserCommand.QueueMoveAfterCurrent,
            is UserCommand.QueueClearPlayed,
            is UserCommand.QueueClear,
            is UserCommand.QueueShuffle,
            is UserCommand.RepeatModeChange,
            is UserCommand.OptionsChange -> null
        }

    private enum class Lane {
        PLAY_PAUSE,
        SEEK,
    }
}
