import org.junit.Test

fun main() {
    val classes = listOf(
        com.darius.unison.library.M3uCodecTest::class.java,
        com.darius.unison.library.M3uResolutionPolicyTest::class.java,
        com.darius.unison.library.PlaylistPathPolicyTest::class.java,
        com.darius.unison.library.PlaylistMatchPolicyTest::class.java,
        com.darius.unison.library.UriPermissionLedgerTest::class.java,
        com.darius.unison.model.CanonicalPlaybackStateTest::class.java,
        com.darius.unison.model.RoomUiStateTest::class.java,
        com.darius.unison.model.RoomStateSeparationTest::class.java,
        com.darius.unison.network.DiscoveredRoomRegistryTest::class.java,
        com.darius.unison.network.NetworkAddressPolicyTest::class.java,
        com.darius.unison.playback.SystemMediaCommandPolicyTest::class.java,
        com.darius.unison.playback.PlaybackSpeedCommandGateTest::class.java,
        com.darius.unison.protocol.CryptoTest::class.java,
        com.darius.unison.protocol.RoomSnapshotValidatorTest::class.java,
        com.darius.unison.protocol.EnvelopeReplayProtectorTest::class.java,
        com.darius.unison.protocol.PlaybackTelemetryProtocolTest::class.java,
        com.darius.unison.network.ControlTrafficClassifierTest::class.java,
        com.darius.unison.room.PlaybackQueuePolicyTest::class.java,
        com.darius.unison.room.PlaybackRequestPolicyTest::class.java,
        com.darius.unison.room.RoomReducerTest::class.java,
        com.darius.unison.room.RoomEngineValidationTest::class.java,
        com.darius.unison.room.PeerRegistryTest::class.java,
        com.darius.unison.room.RoomRoleEnginesTest::class.java,
        com.darius.unison.room.RoomMessageRouterTest::class.java,
        com.darius.unison.room.AdmissionGuardTest::class.java,
        com.darius.unison.room.ControlAdmissionControllerTest::class.java,
        com.darius.unison.room.SerializedEventLoopTest::class.java,
        com.darius.unison.sync.ClockSyncEngineTest::class.java,
        com.darius.unison.sync.PlaybackSyncEngineTest::class.java,
        com.darius.unison.sync.SynchronizationSimulationTest::class.java,
        com.darius.unison.storage.ManagedFileStoreTest::class.java,
        com.darius.unison.storage.ArtworkRetryPolicyTest::class.java,
        com.darius.unison.transfer.TransferCancellationRegistryTest::class.java,
    )
    var passed = 0
    classes.forEach { type ->
        val instance = type.getDeclaredConstructor().newInstance()
        type.declaredMethods
            .filter { it.getAnnotation(Test::class.java) != null }
            .sortedBy { it.name }
            .forEach { method ->
                try {
                    method.isAccessible = true
                    method.invoke(instance)
                    passed++
                } catch (error: java.lang.reflect.InvocationTargetException) {
                    throw AssertionError("${type.simpleName}.${method.name} failed", error.targetException)
                }
            }
    }
    println("CORE_TESTS_OK ($passed tests)")
}
