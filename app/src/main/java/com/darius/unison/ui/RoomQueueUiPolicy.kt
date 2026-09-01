package com.darius.unison.ui

import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomMediaReadiness
import com.darius.unison.model.TransferProgress

/** Pure queue/readiness presentation rules shared by room Compose surfaces and tests. */
internal object RoomQueueUiPolicy {
    data class MediaPresentation(
        val detail: String,
        val tapAction: TapAction,
    )

    enum class TapAction {
        PLAY,
        PREPARE,
        NONE,
    }

    fun mediaPresentation(
        readiness: RoomMediaReadiness,
        transfer: TransferProgress?,
        current: Boolean,
        playing: Boolean,
        blocked: Boolean = false,
    ): MediaPresentation {
        if (playing) return MediaPresentation("Playing", TapAction.PLAY)
        if (blocked) return MediaPresentation("Transfer blocked", TapAction.PREPARE)
        if (current && readiness == RoomMediaReadiness.READY) {
            return MediaPresentation("", TapAction.PLAY)
        }
        if (transfer?.state == MemberTrackState.FAILED) {
            return MediaPresentation("Preparation failed", TapAction.PREPARE)
        }
        if (
            transfer?.state == MemberTrackState.RECEIVING ||
                transfer?.state == MemberTrackState.VERIFYING ||
                transfer?.state == MemberTrackState.PREPARING_PLAYER
        ) {
            val detail =
                when (transfer.state) {
                    MemberTrackState.RECEIVING -> "Syncing · ${(transfer.fraction * 100).toInt()}%"
                    MemberTrackState.VERIFYING -> "Verifying…"
                    MemberTrackState.PREPARING_PLAYER -> "Finishing preparation…"
                    else -> "Preparing…"
                }
            return MediaPresentation(detail, TapAction.NONE)
        }
        return when (readiness) {
            RoomMediaReadiness.READY -> MediaPresentation("", TapAction.PLAY)

            RoomMediaReadiness.PREPARING -> {
                val detail =
                    when (transfer?.state) {
                        MemberTrackState.RECEIVING ->
                            "Syncing · ${(transfer.fraction * 100).toInt()}%"
                        MemberTrackState.VERIFYING,
                        MemberTrackState.PREPARING_PLAYER -> "Almost ready"
                        else -> "Preparing…"
                    }
                MediaPresentation(detail, TapAction.NONE)
            }

            RoomMediaReadiness.NEEDS_PREPARATION ->
                MediaPresentation("Needs preparation", TapAction.PREPARE)
        }
    }

    fun queueSummary(
        queueSize: Int,
        readiness: Collection<RoomMediaReadiness>,
        blockedCount: Int = 0,
    ): String {
        if (queueSize <= 0) return "0 songs"
        val blocked = blockedCount.coerceIn(0, queueSize)
        val syncing =
            (readiness.count { it == RoomMediaReadiness.PREPARING } - blocked)
                .coerceAtLeast(0)
                .coerceAtMost((queueSize - blocked).coerceAtLeast(0))
        val songCount = "$queueSize ${if (queueSize == 1) "song" else "songs"}"
        return buildList {
                add(songCount)
                if (syncing > 0) add("$syncing syncing")
                if (blocked > 0) add("$blocked blocked")
            }
            .joinToString(" · ")
    }

    fun showQueueToolbar(queueSize: Int): Boolean = queueSize > 0

    fun showQueueSearchField(
        queueSize: Int,
        query: String,
        searchExpanded: Boolean,
    ): Boolean = query.isNotBlank() || searchExpanded || queueSize >= 8

    fun roomPresenceLabel(lifecycle: RoomLifecycleState, memberCount: Int): String =
        when (lifecycle) {
            RoomLifecycleState.RECONNECTING -> "Reconnecting…"
            RoomLifecycleState.ENDING -> "Leaving room…"
            RoomLifecycleState.FAILED -> "Room unavailable"
            else -> if (memberCount == 1) "1 in room" else "$memberCount in room"
        }
}
