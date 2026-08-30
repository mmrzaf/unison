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
    ): MediaPresentation {
        if (playing) return MediaPresentation("Playing", TapAction.PLAY)
        if (current && readiness == RoomMediaReadiness.READY) {
            return MediaPresentation("Ready", TapAction.PLAY)
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
                    MemberTrackState.RECEIVING ->
                        "Preparing · ${(transfer.fraction * 100).toInt()}%"
                    MemberTrackState.VERIFYING -> "Verifying…"
                    MemberTrackState.PREPARING_PLAYER -> "Finishing preparation…"
                }
            return MediaPresentation(detail, TapAction.NONE)
        }
        return when (readiness) {
            RoomMediaReadiness.READY -> MediaPresentation("Ready", TapAction.PLAY)

            RoomMediaReadiness.PREPARING -> {
                val detail =
                    when (transfer?.state) {
                        MemberTrackState.RECEIVING ->
                            "Preparing · ${(transfer.fraction * 100).toInt()}%"
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
    ): String {
        if (queueSize <= 0) return "0 songs"
        val ready = readiness.count { it == RoomMediaReadiness.READY }.coerceAtMost(queueSize)
        val preparing =
            readiness
                .count { it == RoomMediaReadiness.PREPARING }
                .coerceAtMost((queueSize - ready).coerceAtLeast(0))
        val songCount = "$queueSize ${if (queueSize == 1) "song" else "songs"}"
        return buildList {
                add(songCount)
                if (ready > 0) add("$ready ready")
                if (preparing > 0) add("$preparing preparing")
            }
            .joinToString(" · ")
    }

    fun roomPresenceLabel(lifecycle: RoomLifecycleState, memberCount: Int): String =
        when (lifecycle) {
            RoomLifecycleState.RECONNECTING -> "Reconnecting…"
            RoomLifecycleState.ENDING -> "Leaving room…"
            RoomLifecycleState.FAILED -> "Room unavailable"
            else -> if (memberCount == 1) "1 in room" else "$memberCount in room"
        }
}
