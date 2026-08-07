package com.darius.unison.room

import com.darius.unison.sync.PlaybackSyncProfile
import com.darius.unison.sync.PlaybackSyncState
import com.darius.unison.sync.tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSynchronizationRuntimeTest {
    @Test
    fun balancedIsTheDefaultProfile() {
        val runtime = PlaybackSynchronizationRuntime()

        assertEquals(PlaybackSyncProfile.BALANCED, runtime.profile)
        assertEquals(PlaybackSyncProfile.BALANCED.tuning(), runtime.tuning)
    }

    @Test
    fun profileChangeReacquiresAndInstallsOneSharedTuning() {
        val runtime = PlaybackSynchronizationRuntime(PlaybackSyncProfile.TIGHT)
        assertTrue(runtime.updateProfile(PlaybackSyncProfile.SMOOTH))

        assertEquals(PlaybackSyncProfile.SMOOTH, runtime.profile)
        assertEquals(PlaybackSyncProfile.SMOOTH.tuning(), runtime.tuning)
        assertEquals(PlaybackSyncState.ACQUIRING, runtime.state)
        assertEquals(
            1.0035f,
            runtime.selectSpeed(requestedSpeed = 1.02f, actualSpeed = 1f, nowNs = 1L),
        )
    }

    @Test
    fun selectingTheCurrentProfileIsANoOp() {
        val runtime = PlaybackSynchronizationRuntime(PlaybackSyncProfile.BALANCED)
        assertFalse(runtime.updateProfile(PlaybackSyncProfile.BALANCED))
    }
}
