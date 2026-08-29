package com.darius.unison.transfer

import com.darius.unison.model.PeerId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferUploadGateTest {
    @Test
    fun differentPeersCanUseIndependentGlobalSlots() = runTest {
        val gate = TransferUploadGate(maxConcurrentUploads = 2)
        val release = CompletableDeferred<Unit>()
        val firstEntered = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()

        val first = async {
            gate.withPermit(PeerId("peer-a")) {
                firstEntered.complete(Unit)
                release.await()
            }
        }
        val second = async {
            gate.withPermit(PeerId("peer-b")) {
                secondEntered.complete(Unit)
                release.await()
            }
        }

        firstEntered.await()
        secondEntered.await()
        release.complete(Unit)
        first.await()
        second.await()
        assertEquals(0, gate.trackedPeerCount)
    }

    @Test
    fun defensiveTryAdmissionRejectsBusyPairWithoutWaiting() = runTest {
        val gate = TransferUploadGate(maxConcurrentUploads = 3, maxConcurrentPerDestination = 1)
        val release = CompletableDeferred<Unit>()
        val firstEntered = CompletableDeferred<Unit>()
        val first = async {
            gate.withPermit(PeerId("peer-a")) {
                firstEntered.complete(Unit)
                release.await()
            }
        }
        firstEntered.await()

        var duplicateRan = false
        val admitted = gate.tryWithPermit(PeerId("peer-a")) { duplicateRan = true }

        assertFalse(admitted)
        assertFalse(duplicateRan)
        release.complete(Unit)
        first.await()
        assertEquals(0, gate.trackedPeerCount)
    }

    @Test
    fun duplicateRequestsForOnePeerAreSerializedAndEntriesAreReleased() = runTest {
        val gate = TransferUploadGate(maxConcurrentUploads = 3)
        val releaseFirst = CompletableDeferred<Unit>()
        val firstEntered = CompletableDeferred<Unit>()
        var secondEntered = false

        val first = async {
            gate.withPermit(PeerId("peer-a")) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        val second = async {
            gate.withPermit(PeerId("peer-a")) { secondEntered = true }
        }

        firstEntered.await()
        repeat(10) { yield() }
        assertFalse(secondEntered)
        assertEquals(1, gate.trackedPeerCount)

        releaseFirst.complete(Unit)
        first.await()
        second.await()
        assertTrue(secondEntered)
        assertEquals(0, gate.trackedPeerCount)
    }
}
