package com.darius.unison.playback

import kotlin.math.abs

/** Pure decision policy that prevents no-op Media3 timeline work and log amplification. */
internal object PlaybackTimelinePlan {
    enum class Action {
        NO_OP,
        CLEAR,
        RECONCILE,
        REBUILD,
        PATCH,
    }

    fun decide(
        currentIds: List<String>,
        desiredIds: List<String>,
        currentId: String?,
        targetId: String?,
        currentPositionMs: Long,
        targetPositionMs: Long,
        playerIdle: Boolean,
        playWhenReady: Boolean,
        positionToleranceMs: Long = DEFAULT_POSITION_TOLERANCE_MS,
    ): Action {
        require(positionToleranceMs >= 0)
        if (desiredIds.isEmpty()) {
            return if (currentIds.isEmpty() && playerIdle && !playWhenReady) Action.NO_OP
            else Action.CLEAR
        }
        require(targetId != null && targetId in desiredIds)
        if (currentIds == desiredIds) {
            val aligned =
                currentId == targetId &&
                    abs(currentPositionMs - targetPositionMs) <= positionToleranceMs
            return if (aligned && !playerIdle) Action.NO_OP else Action.RECONCILE
        }
        return if (PlaybackQueueDiffPolicy.shouldRebuild(currentIds, desiredIds)) Action.REBUILD
        else Action.PATCH
    }

    const val DEFAULT_POSITION_TOLERANCE_MS = 250L
}
