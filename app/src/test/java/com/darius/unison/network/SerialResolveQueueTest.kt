package com.darius.unison.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SerialResolveQueueTest {
    /** A fake platform that, like Android 11–13, rejects a second concurrent resolution. */
    private class FakePlatform {
        val started = mutableListOf<String>()
        val completions = mutableMapOf<String, () -> Unit>()
        var concurrentRejections = 0
        private var outstanding = 0

        fun start(name: String, complete: () -> Unit) {
            if (outstanding > 0) concurrentRejections++
            outstanding++
            started += name
            completions[name] = {
                outstanding--
                complete()
            }
        }

        fun finish(name: String) = checkNotNull(completions.remove(name)).invoke()
    }

    private fun queue(platform: FakePlatform, maxPending: Int = 32) =
        SerialResolveQueue<String>(maxPending, keyOf = { it }, start = platform::start)

    @Test
    fun slowResolutionKeepsTheSlotUntilThePlatformReportsCompletion() {
        val platform = FakePlatform()
        val queue = queue(platform)

        queue.enqueue("room-a")
        queue.enqueue("room-b")

        assertEquals(listOf("room-a"), platform.started)
        assertEquals("room-a", queue.inFlight)

        platform.finish("room-a")

        assertEquals(listOf("room-a", "room-b"), platform.started)
        assertEquals(0, platform.concurrentRejections)
    }

    @Test
    fun clearingPendingWorkDoesNotReleaseTheInFlightSlot() {
        val platform = FakePlatform()
        val queue = queue(platform)
        queue.enqueue("old-scan-room")
        queue.enqueue("old-scan-other")

        queue.clearPending()
        queue.enqueue("new-scan-room")

        assertEquals(listOf("old-scan-room"), platform.started)
        platform.finish("old-scan-room")
        assertEquals(listOf("old-scan-room", "new-scan-room"), platform.started)
        assertEquals(0, platform.concurrentRejections)
    }

    @Test
    fun duplicateKeysAreIgnoredWhilePendingOrInFlight() {
        val platform = FakePlatform()
        val queue = queue(platform)

        queue.enqueue("room-a")
        queue.enqueue("room-a")
        queue.enqueue("room-b")
        queue.enqueue("room-b")

        assertEquals(1, queue.pendingCount)
        platform.finish("room-a")
        platform.finish("room-b")
        assertEquals(listOf("room-a", "room-b"), platform.started)
        assertNull(queue.inFlight)
    }

    @Test
    fun removedServiceIsNeverResolvedAndRepeatedCompletionIsIgnored() {
        val platform = FakePlatform()
        val queue = queue(platform)
        queue.enqueue("room-a")
        queue.enqueue("lost-room")
        queue.enqueue("room-c")

        queue.remove("lost-room")
        val completeA = checkNotNull(platform.completions["room-a"])
        completeA()
        completeA()

        assertEquals(listOf("room-a", "room-c"), platform.started)
        assertEquals("room-c", queue.inFlight)
    }

    @Test
    fun synchronousStartFailureAdvancesToTheNextService() {
        val started = mutableListOf<String>()
        val queue =
            SerialResolveQueue<String>(
                maxPending = 32,
                keyOf = { it },
                start = { name, complete ->
                    started += name
                    complete()
                },
            )

        queue.enqueue("room-a")
        queue.enqueue("room-b")

        assertEquals(listOf("room-a", "room-b"), started)
        assertNull(queue.inFlight)
    }

    @Test
    fun pendingQueueIsBounded() {
        val platform = FakePlatform()
        val queue = queue(platform, maxPending = 2)

        queue.enqueue("in-flight")
        repeat(5) { queue.enqueue("room-$it") }

        assertEquals(2, queue.pendingCount)
    }
}
