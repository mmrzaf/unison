package com.darius.unison.app

import com.darius.unison.model.AppCommand
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class RoomCommandBus {
    private val commands = Channel<AppCommand>(capacity = 64)
    val flow: Flow<AppCommand> = commands.receiveAsFlow()

    suspend fun send(command: AppCommand) = commands.send(command)
    fun trySend(command: AppCommand) = commands.trySend(command)
}
