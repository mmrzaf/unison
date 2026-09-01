import org.junit.After
import org.junit.Before
import org.junit.Test

fun main() {
    val classes = listOf(
        com.darius.unison.app.RoomStoreTest::class.java,
        com.darius.unison.app.RoomCommandBusTest::class.java,
        com.darius.unison.library.LibrarySearchTest::class.java,
        com.darius.unison.library.M3uCodecTest::class.java,
        com.darius.unison.library.M3uResolutionPolicyTest::class.java,
        com.darius.unison.library.PlaylistPathPolicyTest::class.java,
        com.darius.unison.library.PlaylistMatchPolicyTest::class.java,
        com.darius.unison.library.UriPermissionLedgerTest::class.java,
        com.darius.unison.model.CanonicalPlaybackStateTest::class.java,
        com.darius.unison.model.RoomUiStateTest::class.java,
        com.darius.unison.model.RoomStateSeparationTest::class.java,
        com.darius.unison.model.RoomIssueTest::class.java,
        com.darius.unison.model.TransportCommandResultTest::class.java,
        com.darius.unison.ui.PermissionControllerTest::class.java,
        com.darius.unison.ui.RoomPlaybackUiPolicyTest::class.java,
        com.darius.unison.ui.RoomQueueUiPolicyTest::class.java,
        com.darius.unison.network.DiscoveredRoomRegistryTest::class.java,
        com.darius.unison.network.NetworkAddressPolicyTest::class.java,
        com.darius.unison.network.LocalNetworkRoutePolicyTest::class.java,
        com.darius.unison.transfer.LocalNetworkTransferFailurePolicyTest::class.java,
        com.darius.unison.util.AudioMimePolicyTest::class.java,
        com.darius.unison.playback.SystemMediaCommandPolicyTest::class.java,
        com.darius.unison.playback.OutputRouteQueryPolicyTest::class.java,
        com.darius.unison.playback.RoomServicePolicyTest::class.java,
        com.darius.unison.protocol.CryptoTest::class.java,
        com.darius.unison.protocol.PinPakeTest::class.java,
        com.darius.unison.protocol.Srp6aCoreRfc5054Test::class.java,
        com.darius.unison.protocol.AuthenticatedFileStreamCodecTest::class.java,
        com.darius.unison.protocol.RoomSnapshotValidatorTest::class.java,
        com.darius.unison.protocol.EnvelopeReplayProtectorTest::class.java,
        com.darius.unison.protocol.PlaybackTelemetryProtocolTest::class.java,
        com.darius.unison.network.ControlTrafficClassifierTest::class.java,
        com.darius.unison.playback.PlaybackQueueDiffPolicyTest::class.java,
        com.darius.unison.playback.MediaNotificationUpdatePolicyTest::class.java,
        com.darius.unison.playback.PlaybackTimelinePlanTest::class.java,
        com.darius.unison.playback.PlaybackSpeedCommandGateTest::class.java,
        com.darius.unison.playback.PlaybackIntentReconciliationPolicyTest::class.java,
        com.darius.unison.playback.ExpectedPlayerIntentTrackerTest::class.java,
        com.darius.unison.playback.PlaybackPausePolicyTest::class.java,
        com.darius.unison.playback.PlaybackReconciliationKeyTest::class.java,
        com.darius.unison.playback.CanonicalPlaybackDispatcherTest::class.java,
        com.darius.unison.playback.PlayerStateEventPolicyTest::class.java,
        com.darius.unison.playback.NaturalBoundaryLatchTest::class.java,
        com.darius.unison.playback.PlayerEventInterpreterTest::class.java,
        com.darius.unison.room.PlaybackQueuePolicyTest::class.java,
        com.darius.unison.room.TerminalReplayPolicyTest::class.java,
        com.darius.unison.room.PlaybackSyncCadencePolicyTest::class.java,
        com.darius.unison.room.PlaybackSynchronizationRuntimeTest::class.java,
        com.darius.unison.room.LocalPlaybackSyncControllerTest::class.java,
        com.darius.unison.room.PlaybackConvergencePolicyTest::class.java,
        com.darius.unison.room.PlaybackSessionCoordinatorTest::class.java,
        com.darius.unison.room.LocalPlaybackParticipationCoordinatorTest::class.java,
        com.darius.unison.room.PlaybackRequestPolicyTest::class.java,
        com.darius.unison.room.RoomReducerTest::class.java,
        com.darius.unison.room.RoomEngineValidationTest::class.java,
        com.darius.unison.room.PeerPlaybackHealthRegistryTest::class.java,
        com.darius.unison.room.PeerRegistryTest::class.java,
        com.darius.unison.room.RoomRoleEnginesTest::class.java,
        com.darius.unison.room.RoomMessageRouterTest::class.java,
        com.darius.unison.room.AdmissionGuardTest::class.java,
        com.darius.unison.room.ControlAdmissionControllerTest::class.java,
        com.darius.unison.room.RoomIngressAuthorityTest::class.java,
        com.darius.unison.room.PeerEndpointAuthorityTest::class.java,
        com.darius.unison.room.RoomLifecycleSeamRegressionTest::class.java,
        com.darius.unison.room.SerializedEventLoopTest::class.java,
        com.darius.unison.room.SessionJobRegistryTest::class.java,
        com.darius.unison.room.JoinRetryPolicyTest::class.java,
        com.darius.unison.room.RoomPowerPolicyTest::class.java,
        com.darius.unison.room.HeartbeatLivenessPolicyTest::class.java,
        com.darius.unison.room.RoomReconnectPolicyTest::class.java,
        com.darius.unison.room.QueueSearchIndexTest::class.java,
        com.darius.unison.room.QueueDragPolicyTest::class.java,
        com.darius.unison.room.QueueShufflePolicyTest::class.java,
        com.darius.unison.room.TransferCoordinatorTest::class.java,
        com.darius.unison.room.TrackPrefetchPolicyTest::class.java,
        com.darius.unison.room.TransportIntentCoordinatorTest::class.java,
        com.darius.unison.room.RoomMediaReadinessPolicyTest::class.java,
        com.darius.unison.room.ReliabilityStressTest::class.java,
        com.darius.unison.room.TransportLeadTimePolicyTest::class.java,
        com.darius.unison.room.TransportTargetPolicyTest::class.java,
        com.darius.unison.room.TransportCommandTrackerTest::class.java,
        com.darius.unison.playback.PlayerExecutorTest::class.java,
        com.darius.unison.sync.ClockSyncEngineTest::class.java,
        com.darius.unison.sync.PlaybackSyncTuningTest::class.java,
        com.darius.unison.sync.PlaybackSyncEngineTest::class.java,
        com.darius.unison.sync.SynchronizationSimulationTest::class.java,
        com.darius.unison.sync.ManyParticipantSynchronizationSimulationTest::class.java,
        com.darius.unison.sync.SynchronizationDiagnosticsTest::class.java,
        com.darius.unison.storage.ManagedFileStoreTest::class.java,
        com.darius.unison.transfer.TransferCancellationRegistryTest::class.java,
        com.darius.unison.transfer.TransferAuthorizationRegistryTest::class.java,
        com.darius.unison.util.DiagnosticSanitizerTest::class.java,
    )
    var passed = 0
    classes.forEach { type ->
        val beforeMethods = type.declaredMethods
            .filter { it.getAnnotation(Before::class.java) != null }
            .sortedBy { it.name }
        val afterMethods = type.declaredMethods
            .filter { it.getAnnotation(After::class.java) != null }
            .sortedByDescending { it.name }
        type.declaredMethods
            .filter { it.getAnnotation(Test::class.java) != null }
            .sortedBy { it.name }
            .forEach { method ->
                val instance = type.getDeclaredConstructor().newInstance()
                try {
                    beforeMethods.forEach { before ->
                        before.isAccessible = true
                        before.invoke(instance)
                    }
                    method.isAccessible = true
                    method.invoke(instance)
                    passed++
                } catch (error: java.lang.reflect.InvocationTargetException) {
                    throw AssertionError("${type.simpleName}.${method.name} failed", error.targetException)
                } finally {
                    afterMethods.forEach { after ->
                        after.isAccessible = true
                        after.invoke(instance)
                    }
                }
            }
    }
    println("CORE_TESTS_OK ($passed tests)")
}
