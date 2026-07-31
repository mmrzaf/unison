package com.darius.unison.playback

import com.darius.unison.model.QueueItemId

/** Playback-domain failures. Presentation wording is intentionally resolved outside playback. */
sealed interface PlaybackFailure {
    val commandId: String?

    data class TrackUnavailable(
        override val commandId: String?,
        val queueItemId: QueueItemId? = null,
    ) : PlaybackFailure

    data class ClockUnavailable(override val commandId: String?) : PlaybackFailure

    data class ActionFailed(
        override val commandId: String?,
        val action: String,
        val cause: Throwable? = null,
    ) : PlaybackFailure
}
