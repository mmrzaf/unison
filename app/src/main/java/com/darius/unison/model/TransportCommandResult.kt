package com.darius.unison.model

/** Terminal, typed result derived from a transport command's lifecycle status. */
sealed interface TransportCommandResult {
    val commandId: String
    val action: TransportAction

    data class Settled(
        override val commandId: String,
        override val action: TransportAction,
        val queueItemId: QueueItemId? = null,
    ) : TransportCommandResult

    data class Superseded(
        override val commandId: String,
        override val action: TransportAction,
        val message: String? = null,
    ) : TransportCommandResult

    data class Rejected(
        override val commandId: String,
        override val action: TransportAction,
        val message: String,
    ) : TransportCommandResult
}

fun TransportCommandStatus.resultOrNull(): TransportCommandResult? =
    when (phase) {
        TransportCommandPhase.SETTLED ->
            TransportCommandResult.Settled(
                commandId = commandId,
                action = action,
                queueItemId = queueItemId,
            )

        TransportCommandPhase.SUPERSEDED ->
            TransportCommandResult.Superseded(
                commandId = commandId,
                action = action,
                message = message,
            )

        TransportCommandPhase.REJECTED ->
            TransportCommandResult.Rejected(
                commandId = commandId,
                action = action,
                message = message ?: "Command rejected",
            )

        TransportCommandPhase.SUBMITTED,
        TransportCommandPhase.ACCEPTED,
        TransportCommandPhase.SCHEDULED,
        TransportCommandPhase.EXECUTING -> null
    }
