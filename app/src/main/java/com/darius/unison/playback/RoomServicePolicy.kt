package com.darius.unison.playback

/** Pure lifecycle decisions for the MediaSessionService. */
object RoomServicePolicy {
    fun requiresRoomForeground(sessionActive: Boolean, hotspotActive: Boolean): Boolean =
        sessionActive || hotspotActive

    /** Delayed shutdown must always be tied to an Android service start ID. */
    fun canScheduleIdleStop(startId: Int): Boolean = startId > 0

    /** A stale playWhenReady flag without a current item must not keep the service alive. */
    fun playbackActive(queueItemPresent: Boolean, playWhenReady: Boolean): Boolean =
        queueItemPresent && playWhenReady

    fun shouldStop(
        operationActive: Boolean,
        hotspotActive: Boolean,
        playbackActive: Boolean,
        commandOutstanding: Boolean = false,
    ): Boolean = !operationActive && !hotspotActive && !playbackActive && !commandOutstanding
}
