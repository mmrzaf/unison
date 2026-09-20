package com.darius.unison.transfer

import java.io.IOException
import java.net.SocketException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferManagerWatchdogClassificationTest {
    @Test
    fun socketExceptionAfterWatchdogTimeoutIsClassifiedAsWatchdogClose() {
        assertTrue(
            TransferManager.isWatchdogInducedCloseFailure(
                uploadTimedOut = true,
                error = SocketException("Socket closed"),
            )
        )
    }

    @Test
    fun genericIOExceptionAfterWatchdogTimeoutIsClassifiedAsWatchdogClose() {
        // The watchdog forces the socket closed while an encrypted write is in flight. Depending
        // on platform, that surfaces as a SocketException or a more generic IOException (for
        // example "Broken pipe"). Both must be treated as the same expected fallout.
        assertTrue(
            TransferManager.isWatchdogInducedCloseFailure(
                uploadTimedOut = true,
                error = IOException("Broken pipe"),
            )
        )
    }

    @Test
    fun ioExceptionWithoutWatchdogTimeoutIsNotClassifiedAsWatchdogClose() {
        assertFalse(
            TransferManager.isWatchdogInducedCloseFailure(
                uploadTimedOut = false,
                error = SocketException("Socket closed"),
            )
        )
    }

    @Test
    fun nonIOFailureAfterWatchdogTimeoutIsNotClassifiedAsWatchdogClose() {
        assertFalse(
            TransferManager.isWatchdogInducedCloseFailure(
                uploadTimedOut = true,
                error = IllegalStateException("Unrelated failure"),
            )
        )
    }

    @Test
    fun cancellationIsNeverClassifiedAsWatchdogCloseEvenAfterTimeout() {
        // Cancellation must always propagate unclassified so it is never swallowed as a "handled"
        // watchdog close.
        assertFalse(
            TransferManager.isWatchdogInducedCloseFailure(
                uploadTimedOut = true,
                error = CancellationException("Scope cancelled"),
            )
        )
    }
}
