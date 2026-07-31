package com.darius.unison.app

import com.darius.unison.model.AppCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomCommandBusTest {
    @Test
    fun `accepted command remains outstanding until consumer completes it`() = runBlocking {
        val bus = RoomCommandBus()

        assertTrue(bus.trySend(AppCommand.Play()).isSuccess)
        assertTrue(bus.hasOutstandingCommands)
        assertEquals(1, bus.outstandingCount)
        assertTrue(bus.transportFlow.first() is AppCommand.Play)
        assertTrue(bus.hasOutstandingCommands)

        bus.complete()
        assertFalse(bus.hasOutstandingCommands)
    }

    @Test
    fun `rejected command does not leak lifecycle ownership`() {
        val bus = RoomCommandBus()
        repeat(RoomCommandBus.TRANSPORT_CAPACITY) {
            assertTrue(bus.trySend(AppCommand.Play()).isSuccess)
        }

        assertTrue(bus.trySend(AppCommand.Pause()).isFailure)
        assertEquals(RoomCommandBus.TRANSPORT_CAPACITY, bus.outstandingCount)
    }

    @Test
    fun `general command saturation cannot block pause`() {
        val bus = RoomCommandBus()
        repeat(RoomCommandBus.GENERAL_CAPACITY) {
            assertTrue(bus.trySend(AppCommand.StartDiscovery).isSuccess)
        }

        assertTrue(bus.trySend(AppCommand.Pause("pause-now")).isSuccess)
        assertEquals(RoomCommandBus.GENERAL_CAPACITY + 1, bus.outstandingCount)
    }

    @Test
    fun `queued commands are counted independently`() = runBlocking {
        val bus = RoomCommandBus()
        bus.send(AppCommand.Play())
        bus.send(AppCommand.Pause())
        assertEquals(2, bus.outstandingCount)

        bus.transportFlow.first()
        bus.complete()
        assertEquals(1, bus.outstandingCount)
    }

    @Test
    fun `service lanes can be consumed independently`() = runBlocking {
        val bus = RoomCommandBus()
        bus.send(AppCommand.StartDiscovery)
        bus.send(AppCommand.Pause("priority"))

        assertTrue(bus.transportFlow.first() is AppCommand.Pause)
        bus.complete()
        assertEquals(AppCommand.StartDiscovery, bus.generalFlow.first())
        bus.complete()
        assertFalse(bus.hasOutstandingCommands)
    }
}
