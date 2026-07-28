package com.darius.unison.room

import com.darius.unison.sync.ClockEstimate
import com.darius.unison.sync.ClockSyncState

/** Role-specific clock and scheduling policy used by the shared room runtime. */
internal sealed interface RoomRoleEngine {
    val isCoordinator: Boolean
    fun coordinatorTimeNs(localTimeNs: Long, guestEstimate: ClockEstimate): Long?
    fun canApplyScheduledCommand(guestEstimate: ClockEstimate): Boolean
}

internal object CoordinatorEngine : RoomRoleEngine {
    override val isCoordinator: Boolean = true
    override fun coordinatorTimeNs(localTimeNs: Long, guestEstimate: ClockEstimate): Long = localTimeNs
    override fun canApplyScheduledCommand(guestEstimate: ClockEstimate): Boolean = true
}

internal object ParticipantEngine : RoomRoleEngine {
    override val isCoordinator: Boolean = false
    override fun coordinatorTimeNs(localTimeNs: Long, guestEstimate: ClockEstimate): Long? =
        if (guestEstimate.state == ClockSyncState.LOCKED) {
            val elapsed = localTimeNs - guestEstimate.sampledAtLocalNs
            guestEstimate.sampledAtLocalNs + guestEstimate.offsetNs + (elapsed * guestEstimate.rate).toLong()
        } else null

    override fun canApplyScheduledCommand(guestEstimate: ClockEstimate): Boolean =
        guestEstimate.state == ClockSyncState.LOCKED
}
