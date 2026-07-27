package com.darius.unison.playback

import com.darius.unison.model.AppCommand

/** Pure mapping between Android media-control intent and synchronized room commands. */
object SystemMediaCommandPolicy {
    fun playWhenReady(value: Boolean): AppCommand = if (value) AppCommand.Play else AppCommand.Pause

    fun seek(positionMs: Long): AppCommand.Seek = AppCommand.Seek(positionMs.coerceAtLeast(0L))

    fun previous(currentPositionMs: Long, restartThresholdMs: Long): AppCommand =
        if (currentPositionMs > restartThresholdMs.coerceAtLeast(0L)) AppCommand.Seek(0L)
        else AppCommand.SkipPrevious
}
