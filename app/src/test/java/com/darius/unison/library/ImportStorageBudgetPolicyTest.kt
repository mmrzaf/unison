package com.darius.unison.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportStorageBudgetPolicyTest {
    @Test
    fun unknownProviderSizeStillRespectsReserve() {
        assertEquals(
            68L,
            ImportStorageBudgetPolicy.copyLimitBytes(
                availableBytes = 100L,
                declaredSizeBytes = null,
                maximumTrackBytes = 1_000L,
                reserveBytes = 32L,
            ),
        )
    }

    @Test
    fun copyLimitNeverExceedsMaximumTrackSize() {
        assertEquals(
            1_000L,
            ImportStorageBudgetPolicy.copyLimitBytes(
                availableBytes = 10_000L,
                declaredSizeBytes = null,
                maximumTrackBytes = 1_000L,
                reserveBytes = 32L,
            ),
        )
    }

    @Test
    fun underreportedProviderCannotIncreaseActualStreamLimit() {
        assertEquals(
            68L,
            ImportStorageBudgetPolicy.copyLimitBytes(
                availableBytes = 100L,
                declaredSizeBytes = 10L,
                maximumTrackBytes = 1_000L,
                reserveBytes = 32L,
            ),
        )
    }

    @Test
    fun declaredFileLargerThanWritableBudgetIsRejectedEarly() {
        assertThrows(IllegalArgumentException::class.java) {
            ImportStorageBudgetPolicy.copyLimitBytes(
                availableBytes = 100L,
                declaredSizeBytes = 69L,
                maximumTrackBytes = 1_000L,
                reserveBytes = 32L,
            )
        }
    }

    @Test
    fun reserveConsumesAllSpaceRejectsImport() {
        assertThrows(IllegalArgumentException::class.java) {
            ImportStorageBudgetPolicy.copyLimitBytes(
                availableBytes = 32L,
                declaredSizeBytes = null,
                maximumTrackBytes = 1_000L,
                reserveBytes = 32L,
            )
        }
    }

    @Test
    fun gateSerializesCopiesAndRecalculatesCapacity() = runTest {
        var available = 100L
        val gate = ImportStorageGate(1_000L, 20L) { available }
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()

        val first = async {
            gate.withBudget(null) { limit ->
                assertEquals(80L, limit)
                firstStarted.complete(Unit)
                releaseFirst.await()
                available = 50L
            }
        }
        firstStarted.await()
        val second = async {
            gate.withBudget(null) { limit ->
                secondStarted.complete(Unit)
                assertEquals(30L, limit)
            }
        }

        assertFalse(secondStarted.isCompleted)
        releaseFirst.complete(Unit)
        first.await()
        second.await()
        assertTrue(secondStarted.isCompleted)
    }

    @Test
    fun gateReleasesCapacityLockAfterCopyFailure() = runTest {
        var available = 100L
        val gate = ImportStorageGate(1_000L, 20L) { available }

        val failure =
            runCatching {
                    gate.withBudget(null) { limit ->
                        assertEquals(80L, limit)
                        error("copy failed")
                    }
                }
                .exceptionOrNull()
        assertTrue(failure is IllegalStateException)

        available = 60L
        val secondLimit = gate.withBudget(null) { it }
        assertEquals(40L, secondLimit)
    }

    @Test
    fun emptyOrOversizedDeclaredFilesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ImportStorageBudgetPolicy.copyLimitBytes(100L, 0L, 1_000L, 10L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportStorageBudgetPolicy.copyLimitBytes(10_000L, 1_001L, 1_000L, 10L)
        }
    }
}
