package com.darius.unison.app

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.darius.unison.BuildConfig
import com.darius.unison.network.AndroidLocalNetworkRouter
import com.darius.unison.network.ControlHandshake
import com.darius.unison.network.LocalHotspotController
import com.darius.unison.network.NsdRoomDiscovery
import com.darius.unison.network.SocketPeerNetwork
import com.darius.unison.network.WifiLocks
import com.darius.unison.room.port.DeviceInfo
import com.darius.unison.room.port.RoomPorts
import com.darius.unison.room.port.TransferPortFactory
import com.darius.unison.transfer.TransferManager
import com.darius.unison.util.DiagnosticLog
import com.darius.unison.util.DiagnosticSeverity
import com.darius.unison.util.MonotonicClock
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

/** Elapsed-realtime clock: monotonic and advancing through deep sleep. */
object AndroidMonotonicClock : MonotonicClock {
    override fun nowNs(): Long = SystemClock.elapsedRealtimeNanos()
}

/** The app's single diagnostics sink: app-private NDJSON mirrored to Logcat. */
internal fun androidDiagnosticLog(context: Context): DiagnosticLog =
    DiagnosticLog(
        file = File(context.filesDir, "diagnostics/unison.ndjson"),
        clock = AndroidMonotonicClock,
        console = ::writeLogcat,
    )

private fun writeLogcat(severity: DiagnosticSeverity, line: String) {
    when (severity) {
        DiagnosticSeverity.DEBUG -> Log.d(LOGCAT_TAG, line)
        DiagnosticSeverity.INFO -> Log.i(LOGCAT_TAG, line)
        DiagnosticSeverity.WARN -> Log.w(LOGCAT_TAG, line)
        DiagnosticSeverity.ERROR -> Log.e(LOGCAT_TAG, line)
    }
}

private const val LOGCAT_TAG = "Unison"

/**
 * Builds one room runtime's ports. Platform components are created per runtime because each owns
 * session-scoped radio, NSD, and hotspot registrations that the runtime releases when it closes.
 */
internal fun AppContainer.roomPorts(scope: CoroutineScope): RoomPorts {
    val power = WifiLocks(appContext)
    val router = AndroidLocalNetworkRouter(appContext, diagnostics)
    val dispatcher = Dispatchers.IO
    val network =
        SocketPeerNetwork(
            scope = scope,
            log = diagnostics,
            handshake = ControlHandshake(scope, dispatcher, AndroidMonotonicClock, diagnostics),
            dispatcher = dispatcher,
            socketProvider = router,
        )
    return RoomPorts(
        clock = AndroidMonotonicClock,
        ioDispatcher = Dispatchers.IO,
        device =
            DeviceInfo(
                model = Build.MODEL.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT,
                appVersion = BuildConfig.VERSION_NAME,
            ),
        log = diagnostics,
        roomStore = roomStore,
        identity = settings,
        tracks = trackRepository,
        fileStore = fileStore,
        network = network,
        transfers =
            TransferPortFactory { host ->
                TransferManager(
                    localIdentity = host.localIdentity,
                    localDisplayName = host.localDisplayName,
                    listeningPort = host.listeningPort,
                    appVersion = BuildConfig.VERSION_NAME,
                    trackRepository = trackRepository,
                    fileStore = fileStore,
                    scope = scope,
                    dispatcher = dispatcher,
                    clock = AndroidMonotonicClock,
                    log = diagnostics,
                    socketProvider = router,
                    retentionPolicyProvider = { settings.retentionPolicy.first() },
                    onProgress = host.onProgress,
                    onCompleted = host.onCompleted,
                    onFailed = host.onFailed,
                    capacityPolicy = host.capacityPolicy,
                )
            },
        localNetwork = router,
        discovery = NsdRoomDiscovery(appContext, power, diagnostics, router),
        hotspot = LocalHotspotController(appContext, diagnostics),
        power = power,
    )
}
