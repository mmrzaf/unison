package com.darius.unison.model

import java.util.Locale

/** Stable, typed issue identity for presentation, deduplication, recovery, and diagnostics. */
enum class RoomIssueCode {
    PLAYBACK_TRACK_UNAVAILABLE,
    PLAYBACK_ACTION_FAILED,
    PLAYBACK_CLOCK_UNAVAILABLE,
    PLAYBACK_RECOVERED,
    PLAYBACK_UNSTABLE,
    ROOM_NOT_ACTIVE,
    ROOM_QUEUE_FULL,
    TRACK_OPEN_FAILED,
    TRACK_PREPARATION_TIMED_OUT,
    PARTIAL_TRACK_IMPORT,
    CONNECTION_FAILED,
    CONNECTION_INTERRUPTED,
    COMMAND_REJECTED,
    INTERNAL_FAILURE,
}

enum class RoomIssueSeverity {
    INFO,
    WARNING,
    ERROR,
}

enum class RoomRecoveryAction {
    NONE,
    RETRY,
    RECONNECT,
    READD_TRACK,
    LEAVE_ROOM,
}

data class RoomIssue(
    val code: RoomIssueCode,
    val message: String,
    val severity: RoomIssueSeverity = RoomIssueSeverity.ERROR,
    val recoveryAction: RoomRecoveryAction = RoomRecoveryAction.NONE,
    val commandId: String? = null,
    val queueItemId: QueueItemId? = null,
    val deduplicationKey: String = buildString {
        append(code.name)
        commandId?.let { append(":command:").append(it) }
        queueItemId?.let { append(":item:").append(it.value) }
    },
) {
    companion object {
        fun internalFailure(message: String): RoomIssue =
            RoomIssue(
                code = RoomIssueCode.INTERNAL_FAILURE,
                message = message,
                deduplicationKey = "internal:${message.trim().lowercase(Locale.ROOT)}",
            )
    }
}
