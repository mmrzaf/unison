package com.darius.unison.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UriPermissionLedgerTest {
    @Test
    fun releasesOnlyAfterFinalOwner() {
        val ledger = UriPermissionLedger()
        assertTrue(ledger.acquire("content://tree/music"))
        assertFalse(ledger.acquire("content://tree/music"))
        assertFalse(ledger.release("content://tree/music"))
        assertTrue(ledger.isActive("content://tree/music"))
        assertTrue(ledger.release("content://tree/music"))
        assertFalse(ledger.isActive("content://tree/music"))
    }

    @Test
    fun unknownReleaseDoesNothing() {
        val ledger = UriPermissionLedger()
        assertFalse(ledger.release("content://tree/missing"))
    }
}
