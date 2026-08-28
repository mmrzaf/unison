package com.darius.unison.playback

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.protocol.ProtocolBody
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalPlaybackDispatcherTest {
    @Test
    fun timelineBurstCollapsesToLatestSnapshot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reconciled = Collections.synchronizedList(mutableListOf<Long>())
        val dispatcher =
            CanonicalPlaybackDispatcher(
                scope = scope,
                applyExact = { _, _ -> },
                reconcileLatest = { work ->
                    reconciled += work.snapshot.sequence
                    if (reconciled.size == 1) {
                        started.complete(Unit)
                        release.await()
                    }
                },
                onFailure = { _, error -> throw error },
            )
        try {
            dispatcher.submit(ProtocolBody.QueueItemsAdded(emptyList()), snapshot(1))
            withTimeout(2_000) { started.await() }

            repeat(200) { index ->
                dispatcher.submit(
                    ProtocolBody.QueueItemMoved(QueueItemId("item"), 0),
                    snapshot(index.toLong() + 2),
                )
            }
            release.complete(Unit)

            withTimeout(2_000) {
                while (reconciled.lastOrNull() != 201L) delay(5)
            }
            assertEquals(listOf(1L, 201L), reconciled.toList())
        } finally {
            dispatcher.close()
            scope.cancel()
        }
    }

    @Test
    fun exactTransportWorkRemainsOrdered() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val applied = Collections.synchronizedList(mutableListOf<String>())
        val dispatcher =
            CanonicalPlaybackDispatcher(
                scope = scope,
                applyExact = { body, _ -> applied += body::class.simpleName.orEmpty() },
                reconcileLatest = {},
                onFailure = { _, error -> throw error },
            )
        try {
            val item = QueueItemId("item")
            dispatcher.submit(ProtocolBody.PlayScheduled(item, 0, 0, "play"), snapshot(1))
            dispatcher.submit(ProtocolBody.PauseScheduled(item, 0, 0, "pause"), snapshot(2))
            dispatcher.submit(ProtocolBody.SeekScheduled(item, 500, true, 0, "seek"), snapshot(3))

            withTimeout(2_000) {
                while (applied.size < 3) delay(5)
            }
            assertEquals(
                listOf("PlayScheduled", "PauseScheduled", "SeekScheduled"),
                applied.toList(),
            )
        } finally {
            dispatcher.close()
            scope.cancel()
        }
    }

    @Test
    fun ignoredProtocolTrafficDoesNotReachPlayback() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var calls = 0
        val dispatcher =
            CanonicalPlaybackDispatcher(
                scope = scope,
                applyExact = { _, _ -> calls++ },
                reconcileLatest = { calls++ },
                onFailure = { _, error -> throw error },
            )
        try {
            dispatcher.submit(ProtocolBody.ClockReady(true), snapshot(1))
            delay(50)
            assertEquals(0, calls)
        } finally {
            dispatcher.close()
            scope.cancel()
        }
    }

    @Test
    fun reconciliationFailureIsReportedAndWorkerContinues() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        var reconciliationCalls = 0
        val dispatcher =
            CanonicalPlaybackDispatcher(
                scope = scope,
                applyExact = { _, _ -> },
                reconcileLatest = {
                    reconciliationCalls++
                    if (reconciliationCalls == 1) error("broken")
                },
                onFailure = { _, error -> failures += error },
            )
        try {
            dispatcher.submit(ProtocolBody.QueueItemsAdded(emptyList()), snapshot(1))
            withTimeout(2_000) { while (failures.isEmpty()) delay(5) }
            dispatcher.submit(
                ProtocolBody.QueueItemsAdded(emptyList()),
                snapshot(2, playing = true),
            )
            withTimeout(2_000) { while (reconciliationCalls < 2) delay(5) }

            assertEquals(1, failures.size)
            assertTrue(failures.single().message == "broken")
        } finally {
            dispatcher.close()
            scope.cancel()
        }
    }

    @Test
    fun metricsExposeCollapsedAndAppliedWork() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val dispatcher =
            CanonicalPlaybackDispatcher(
                scope = scope,
                applyExact = { _, _ -> },
                reconcileLatest = {
                    if (!started.isCompleted) {
                        started.complete(Unit)
                        release.await()
                    }
                },
                onFailure = { _, error -> throw error },
            )
        try {
            dispatcher.submit(ProtocolBody.QueueItemsAdded(emptyList()), snapshot(1))
            withTimeout(2_000) { started.await() }
            repeat(20) { index ->
                dispatcher.submit(
                    ProtocolBody.QueueItemMoved(QueueItemId("item"), 0),
                    snapshot(index + 2L),
                )
            }
            release.complete(Unit)
            withTimeout(2_000) {
                while (dispatcher.metrics().reconciliationApplied < 2) delay(5)
            }

            val metrics = dispatcher.metrics()
            assertEquals(21L, metrics.reconciliationSubmitted)
            assertTrue(metrics.reconciliationCollapsed >= 19L)
            assertEquals(2L, metrics.reconciliationApplied)
            assertEquals(0L, metrics.failures)
        } finally {
            dispatcher.close()
            scope.cancel()
        }
    }

    @Test
    fun reconciliationCannotCrossExactTransportBarrier() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val dispatcher =
            CanonicalPlaybackDispatcher(
                scope = scope,
                applyExact = { _, snapshot -> order += "exact:${snapshot.sequence}" },
                reconcileLatest = { work ->
                    order += "reconcile:${work.snapshot.sequence}"
                    if (work.snapshot.sequence == 1L) {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                },
                onFailure = { _, error -> throw error },
            )
        try {
            dispatcher.submit(ProtocolBody.QueueItemsAdded(emptyList()), snapshot(1))
            withTimeout(2_000) { firstStarted.await() }

            dispatcher.submit(
                ProtocolBody.QueueItemMoved(QueueItemId("item"), 0),
                snapshot(2),
            )
            dispatcher.submit(
                ProtocolBody.PlayScheduled(QueueItemId("item"), 0, 0, "play"),
                snapshot(3),
            )
            dispatcher.submit(
                ProtocolBody.QueueItemMoved(QueueItemId("item"), 0),
                snapshot(4),
            )
            releaseFirst.complete(Unit)

            withTimeout(2_000) {
                while (order.size < 4) delay(5)
            }
            assertEquals(
                listOf("reconcile:1", "reconcile:2", "exact:3", "reconcile:4"),
                order.toList(),
            )
        } finally {
            dispatcher.close()
            scope.cancel()
        }
    }

    private fun snapshot(sequence: Long, playing: Boolean = false): RoomSnapshot {
        val peer = PeerId("peer")
        val item =
            QueueItem(
                queueItemId = QueueItemId("item-$sequence"),
                track = TrackDescriptor(TrackId("track-$sequence"), 100),
                addedByPeerId = peer,
                addedAtSequence = 1,
            )
        return RoomSnapshot(
            roomId = "room",
            roomName = "Room",
            term = CoordinatorTerm(1, peer),
            sequence = sequence,
            queue = listOf(item),
            playback = CanonicalPlaybackState(queueItemId = item.queueItemId, isPlaying = playing),
        )
    }
}
