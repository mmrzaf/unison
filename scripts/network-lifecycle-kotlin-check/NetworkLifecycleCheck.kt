package com.darius.unison.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiManager
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.InetAddress

fun main() = runBlocking {
    hotspotRejectsLateStartCallback()
    hotspotIgnoresStaleFailureCallback()
    discoveryCancellationStopsClassicResolution()
    println("NETWORK_LIFECYCLE_TESTS_OK")
}

private fun hotspotRejectsLateStartCallback() {
    val wifi = FakeWifiManager()
    val controller = LocalHotspotController(ServiceContext(wifi = wifi), DiagnosticLog())
    controller.start()
    val callback = wifi.callbacks.single()
    controller.stop()
    val lateReservation = FakeReservation()
    callback.onStarted(lateReservation)
    check(lateReservation.closeCount == 1) { "Late hotspot reservation was not closed" }
    check(controller.state.value == null) { "Late hotspot callback restored stopped state" }
}

private fun hotspotIgnoresStaleFailureCallback() {
    val wifi = FakeWifiManager()
    val controller = LocalHotspotController(ServiceContext(wifi = wifi), DiagnosticLog())
    var errors = 0
    controller.start { errors++ }
    val staleCallback = wifi.callbacks.single()
    controller.stop()
    controller.start { errors++ }
    val currentCallback = wifi.callbacks.last()
    val currentReservation = FakeReservation()
    currentCallback.onStarted(currentReservation)
    staleCallback.onFailed(1)
    check(errors == 0) { "Stale hotspot failure reached the current request" }
    check(controller.state.value != null) { "Stale hotspot callback cleared current state" }
    controller.stop()
    check(currentReservation.closeCount == 1) { "Current hotspot reservation was not closed exactly once" }
}

private suspend fun discoveryCancellationStopsClassicResolution() {
    val nsd = FakeNsdManager()
    val locks = WifiLocks()
    val discovery = NsdRoomDiscovery(ServiceContext(nsd = nsd), locks, DiagnosticLog())
    var foundCount = 0
    val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext()).launch {
        discovery.discover().collect { foundCount++ }
    }
    awaitCondition { nsd.discoveryListener != null }
    val service = NsdServiceInfo().apply {
        serviceName = "Unison-deadbeef"
        serviceType = "_unison._tcp."
        port = 4567
        host = InetAddress.getByName("192.168.1.10")
        setAttribute("rid", "deadbeef1234")
        setAttribute("v", "1")
        setAttribute("term", "1")
    }
    nsd.discoveryListener!!.onServiceFound(service)
    awaitCondition { nsd.resolveListener != null }
    val lateResolver = checkNotNull(nsd.resolveListener)
    collector.cancelAndJoin()
    lateResolver.onServiceResolved(service)
    delay(10)
    check(foundCount == 0) { "Late Android 11-13 NSD resolution escaped a cancelled scan" }
    check(nsd.stoppedDiscoveryListeners.size == 1) { "Discovery listener was not stopped" }
    check(locks.acquireCount == 1 && locks.releaseCount == 1) {
        "Multicast lock lifecycle was unbalanced: acquire=${locks.acquireCount} release=${locks.releaseCount}"
    }
}

private suspend fun awaitCondition(predicate: () -> Boolean) {
    repeat(100) {
        if (predicate()) return
        delay(5)
    }
    error("Timed out waiting for lifecycle callback")
}

private class ServiceContext(
    private val nsd: NsdManager? = null,
    private val wifi: WifiManager? = null,
) : Context() {
    @Suppress("UNCHECKED_CAST")
    override fun <T> getSystemService(clazz: Class<T>): T = when (clazz) {
        NsdManager::class.java -> checkNotNull(nsd)
        WifiManager::class.java -> checkNotNull(wifi)
        else -> error("Unexpected service ${clazz.name}")
    } as T
}

private class FakeReservation : WifiManager.LocalOnlyHotspotReservation() {
    var closeCount = 0
        private set

    override val softApConfiguration: SoftApConfiguration = object : SoftApConfiguration() {
        override val ssid: String = "Unison"
        override val passphrase: String = "password"
        override val securityType: Int = 1
    }

    override fun close() {
        closeCount++
    }
}

private class FakeWifiManager : WifiManager() {
    val callbacks = mutableListOf<LocalOnlyHotspotCallback>()

    override fun startLocalOnlyHotspot(callback: LocalOnlyHotspotCallback, handler: android.os.Handler) {
        callbacks += callback
    }
}

private class FakeNsdManager : NsdManager() {
    var discoveryListener: DiscoveryListener? = null
    var resolveListener: ResolveListener? = null
    val stoppedDiscoveryListeners = mutableListOf<DiscoveryListener>()

    override fun discoverServices(type: String, protocol: Int, listener: DiscoveryListener) {
        discoveryListener = listener
    }

    override fun stopServiceDiscovery(listener: DiscoveryListener) {
        stoppedDiscoveryListeners += listener
        if (discoveryListener === listener) discoveryListener = null
    }

    override fun resolveService(info: NsdServiceInfo, listener: ResolveListener) {
        resolveListener = listener
    }
}
