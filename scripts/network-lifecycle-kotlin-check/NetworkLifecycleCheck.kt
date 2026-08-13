package com.darius.unison.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import com.darius.unison.util.DiagnosticLog
import java.net.InetAddress
import java.net.Socket
import java.net.SocketException
import javax.net.SocketFactory
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    hotspotRejectsLateStartCallback()
    hotspotIgnoresStaleFailureCallback()
    discoveryCancellationStopsClassicResolution()
    legacyDiscoveryInfersWifiNetwork()
    modernDiscoveryUsesServiceInfoAndNetwork()
    modernDiscoveryKeepsSameServicePerNetwork()
    nonDefaultLanUsesExplicitNetworkBinding()
    bindFailureFallsBackToReachableActiveLan()
    remoteSelectionRejectsLoopbackOnlyCandidates()
    routeFallsBackWhenAndroidExposesNoNetwork()
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
    Build.VERSION.SDK_INT = 33
    val nsd = FakeNsdManager()
    val locks = WifiLocks()
    val cm = FakeConnectivityManager.wifiLan()
    val context = ServiceContext(nsd = nsd, connectivity = cm)
    val router = AndroidLocalNetworkRouter(context, DiagnosticLog())
    val discovery = NsdRoomDiscovery(context, locks, DiagnosticLog(), router)
    var foundCount = 0
    val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext()).launch {
        discovery.discover().collect { foundCount++ }
    }
    awaitCondition { nsd.discoveryListener != null }
    val service = serviceInfo("192.168.1.10")
    nsd.discoveryListener!!.onServiceFound(service)
    awaitCondition { nsd.resolveListener != null }
    val lateResolver = checkNotNull(nsd.resolveListener)
    collector.cancelAndJoin()
    lateResolver.onServiceResolved(service)
    delay(10)
    check(foundCount == 0) { "Late Android 11-13 NSD resolution escaped a cancelled scan" }
    check(nsd.stoppedDiscoveryListeners.size == 1) { "Discovery listener was not stopped" }
    check(nsd.serviceInfoCallbacks.isEmpty()) { "Legacy discovery registered a modern callback" }
    check(locks.acquireCount == 1 && locks.releaseCount == 1) {
        "Multicast lock lifecycle was unbalanced: acquire=${locks.acquireCount} release=${locks.releaseCount}"
    }
}

private suspend fun modernDiscoveryUsesServiceInfoAndNetwork() {
    Build.VERSION.SDK_INT = 34
    val nsd = FakeNsdManager()
    val locks = WifiLocks()
    val cm = FakeConnectivityManager.wifiLan(networkHandle = 42)
    val context = ServiceContext(nsd = nsd, connectivity = cm)
    val router = AndroidLocalNetworkRouter(context, DiagnosticLog())
    val discovery = NsdRoomDiscovery(context, locks, DiagnosticLog(), router)
    val found = mutableListOf<NsdDiscoveryEvent>()
    val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext()).launch {
        discovery.discover().collect { found += it }
    }
    awaitCondition { nsd.discoveryListener != null }
    val network = checkNotNull(cm.activeNetwork)
    val service = serviceInfo("192.168.1.10").apply {
        hostAddresses = mutableListOf(
            InetAddress.getByName("192.168.1.10"),
            InetAddress.getByName("fe80::10"),
        )
        this.network = network
    }
    nsd.discoveryListener!!.onServiceFound(service)
    awaitCondition { nsd.serviceInfoCallbacks.isNotEmpty() }
    check(nsd.resolveListener == null) { "Android 14+ discovery fell back to deprecated resolveService" }
    nsd.serviceInfoCallbacks.values.single().onServiceUpdated(service)
    awaitCondition { found.any { it is NsdDiscoveryEvent.Found } }
    val routed = router.createSocket(InetAddress.getByName("192.168.1.10"), "modern_test")
    check(routed.routeMode == LocalNetworkRouteMode.SYSTEM_DEFAULT) {
        "Active Android Network should use the system-default socket"
    }
    check(routed.networkId == network.networkHandle.toString(16)) { "Wrong NSD Network selected" }
    collector.cancelAndJoin()
    check(nsd.unregisteredServiceInfoCallbacks == 1) { "Modern service callback was not unregistered" }
    check(locks.acquireCount == 1 && locks.releaseCount == 1) { "Modern multicast lifecycle was unbalanced" }
    Build.VERSION.SDK_INT = 33
}

private suspend fun modernDiscoveryKeepsSameServicePerNetwork() {
    Build.VERSION.SDK_INT = 34
    val firstNetwork = Network(101)
    val secondNetwork = Network(202)
    val wifiCaps = NetworkCapabilities(setOf(NetworkCapabilities.TRANSPORT_WIFI))
    fun properties(prefix: String, local: String) = object : LinkProperties() {
        override val interfaceName: String = "wlan0"
        override val linkAddresses = listOf(LinkAddress(InetAddress.getByName(local)))
        override val routes = listOf(RouteInfo { address -> address.hostAddress?.startsWith(prefix) == true })
    }
    val cm = FakeConnectivityManager(
        activeNetwork = firstNetwork,
        allNetworks = arrayOf(firstNetwork, secondNetwork),
        capabilities = mapOf(firstNetwork to wifiCaps, secondNetwork to wifiCaps),
        properties = mapOf(
            firstNetwork to properties("192.168.1.", "192.168.1.50"),
            secondNetwork to properties("192.168.2.", "192.168.2.50"),
        ),
    )
    val nsd = FakeNsdManager()
    val context = ServiceContext(nsd = nsd, connectivity = cm)
    val discovery = NsdRoomDiscovery(context, WifiLocks(), DiagnosticLog(), AndroidLocalNetworkRouter(context, DiagnosticLog()))
    val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext()).launch {
        discovery.discover().collect { }
    }
    awaitCondition { nsd.discoveryListener != null }
    val first = serviceInfo("192.168.1.10").apply {
        hostAddresses = mutableListOf(InetAddress.getByName("192.168.1.10"))
        network = firstNetwork
    }
    val second = serviceInfo("192.168.2.10").apply {
        hostAddresses = mutableListOf(InetAddress.getByName("192.168.2.10"))
        network = secondNetwork
    }
    nsd.discoveryListener!!.onServiceFound(first)
    nsd.discoveryListener!!.onServiceFound(second)
    awaitCondition { nsd.serviceInfoCallbacks.size == 2 }
    collector.cancelAndJoin()
    check(nsd.unregisteredServiceInfoCallbacks == 2) {
        "Same service on two Android Networks did not retain independent callbacks"
    }
    Build.VERSION.SDK_INT = 33
}

private suspend fun legacyDiscoveryInfersWifiNetwork() {
    Build.VERSION.SDK_INT = 30
    val nsd = FakeNsdManager()
    val locks = WifiLocks()
    val cm = FakeConnectivityManager.wifiLan(networkHandle = 30)
    val context = ServiceContext(nsd = nsd, connectivity = cm)
    val router = AndroidLocalNetworkRouter(context, DiagnosticLog())
    val discovery = NsdRoomDiscovery(context, locks, DiagnosticLog(), router)
    val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext()).launch {
        discovery.discover().collect { }
    }
    awaitCondition { nsd.discoveryListener != null }
    val service = serviceInfo("192.168.1.10")
    nsd.discoveryListener!!.onServiceFound(service)
    awaitCondition { nsd.resolveListener != null }
    nsd.resolveListener!!.onServiceResolved(service)
    delay(10)
    val routed = router.createSocket(InetAddress.getByName("192.168.1.10"), "legacy_test")
    check(routed.routeMode == LocalNetworkRouteMode.SYSTEM_DEFAULT) {
        "Android 11 active LAN should use the system-default socket"
    }
    check(routed.networkId == "1e") { "Android 11 selected the wrong network" }
    collector.cancelAndJoin()
    Build.VERSION.SDK_INT = 33
}

private fun nonDefaultLanUsesExplicitNetworkBinding() {
    val activeNetwork = Network(301)
    val roomNetwork = Network(302)
    val wifiCaps = NetworkCapabilities(setOf(NetworkCapabilities.TRANSPORT_WIFI))
    fun properties(prefix: String, local: String) = object : LinkProperties() {
        override val interfaceName: String = "wlan0"
        override val linkAddresses = listOf(LinkAddress(InetAddress.getByName(local)))
        override val routes = listOf(RouteInfo { address -> address.hostAddress?.startsWith(prefix) == true })
    }
    val cm =
        FakeConnectivityManager(
            activeNetwork = activeNetwork,
            allNetworks = arrayOf(activeNetwork, roomNetwork),
            capabilities = mapOf(activeNetwork to wifiCaps, roomNetwork to wifiCaps),
            properties =
                mapOf(
                    activeNetwork to properties("192.168.1.", "192.168.1.50"),
                    roomNetwork to properties("192.168.2.", "192.168.2.50"),
                ),
        )
    val context = ServiceContext(connectivity = cm)
    val router = AndroidLocalNetworkRouter(context, DiagnosticLog())
    val remote = InetAddress.getByName("192.168.2.10")
    router.rememberResolvedService(listOf(remote), roomNetwork)

    val routed = router.createSocket(remote, "non_default_test")

    check(routed.routeMode == LocalNetworkRouteMode.NETWORK_BOUND) {
        "Non-default room LAN must remain explicitly network-bound"
    }
    check(routed.networkId == roomNetwork.networkHandle.toString(16)) {
        "Non-default room LAN selected the wrong Android Network"
    }
    routed.socket.close()
}

private fun bindFailureFallsBackToReachableActiveLan() {
    val activeNetwork = Network(401)
    val failingNetwork =
        Network(
            402,
            socketFactory =
                object : SocketFactory() {
                    override fun createSocket(): Socket =
                        throw SocketException("simulated bind failure")

                    override fun createSocket(host: String?, port: Int): Socket =
                        throw UnsupportedOperationException()

                    override fun createSocket(
                        host: String?,
                        port: Int,
                        localHost: InetAddress?,
                        localPort: Int,
                    ): Socket = throw UnsupportedOperationException()

                    override fun createSocket(host: InetAddress?, port: Int): Socket =
                        throw UnsupportedOperationException()

                    override fun createSocket(
                        address: InetAddress?,
                        port: Int,
                        localAddress: InetAddress?,
                        localPort: Int,
                    ): Socket = throw UnsupportedOperationException()
                },
        )
    val wifiCaps = NetworkCapabilities(setOf(NetworkCapabilities.TRANSPORT_WIFI))
    val properties = object : LinkProperties() {
        override val interfaceName: String = "wlan0"
        override val linkAddresses = listOf(LinkAddress(InetAddress.getByName("192.168.1.50")))
        override val routes = listOf(RouteInfo { address -> address.hostAddress?.startsWith("192.168.1.") == true })
    }
    val cm =
        FakeConnectivityManager(
            activeNetwork = activeNetwork,
            allNetworks = arrayOf(activeNetwork, failingNetwork),
            capabilities = mapOf(activeNetwork to wifiCaps, failingNetwork to wifiCaps),
            properties = mapOf(activeNetwork to properties, failingNetwork to properties),
        )
    val context = ServiceContext(connectivity = cm)
    val router = AndroidLocalNetworkRouter(context, DiagnosticLog())
    val remote = InetAddress.getByName("192.168.1.10")
    router.rememberResolvedService(listOf(remote), failingNetwork)

    val routed = router.createSocket(remote, "bind_failure_test")

    check(routed.routeMode == LocalNetworkRouteMode.SYSTEM_DEFAULT) {
        "Failed explicit bind should fall back to a reachable active LAN"
    }
    check(routed.networkId == activeNetwork.networkHandle.toString(16)) {
        "Bind fallback did not select the active LAN"
    }
    routed.socket.close()
}

private fun remoteSelectionRejectsLoopbackOnlyCandidates() {
    val selected =
        NetworkAddressPolicy.chooseRemoteAddress(
            listOf(
                InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("::1"),
            )
        )
    check(selected == null) { "Loopback-only NSD endpoints must not be selected" }
}

private fun routeFallsBackWhenAndroidExposesNoNetwork() {
    val context = ServiceContext(connectivity = FakeConnectivityManager.empty())
    val router = AndroidLocalNetworkRouter(context, DiagnosticLog())
    val routed = router.createSocket(InetAddress.getByName("192.168.43.2"), "hotspot_test")
    check(routed.routeMode == LocalNetworkRouteMode.ENDPOINT_FALLBACK) {
        "Hotspot/downstream endpoint should use the explicit fallback when no Network exists"
    }
}

private fun serviceInfo(host: String) = NsdServiceInfo().apply {
    serviceName = "Unison-deadbeef"
    serviceType = "_unison._tcp."
    port = 4567
    this.host = InetAddress.getByName(host)
    setAttribute("rid", "deadbeef1234")
    setAttribute("v", "1")
    setAttribute("term", "1")
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
    private val connectivity: ConnectivityManager? = null,
) : Context() {
    @Suppress("UNCHECKED_CAST")
    override fun <T> getSystemService(clazz: Class<T>): T = when (clazz) {
        NsdManager::class.java -> checkNotNull(nsd)
        WifiManager::class.java -> checkNotNull(wifi)
        ConnectivityManager::class.java -> checkNotNull(connectivity)
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

    override fun close() { closeCount++ }
}

private class FakeWifiManager : WifiManager() {
    val callbacks = mutableListOf<LocalOnlyHotspotCallback>()

    override fun startLocalOnlyHotspot(callback: LocalOnlyHotspotCallback, handler: android.os.Handler) {
        callbacks += callback
    }
}

private class FakeConnectivityManager(
    override val activeNetwork: Network?,
    override val allNetworks: Array<Network>,
    private val capabilities: Map<Network, NetworkCapabilities>,
    private val properties: Map<Network, LinkProperties>,
) : ConnectivityManager() {
    override fun getNetworkCapabilities(network: Network): NetworkCapabilities? = capabilities[network]
    override fun getLinkProperties(network: Network): LinkProperties? = properties[network]

    companion object {
        fun wifiLan(networkHandle: Long = 1): FakeConnectivityManager {
            val network = Network(networkHandle)
            val caps = NetworkCapabilities(setOf(NetworkCapabilities.TRANSPORT_WIFI))
            val props = object : LinkProperties() {
                override val interfaceName: String = "wlan0"
                override val linkAddresses = listOf(LinkAddress(InetAddress.getByName("192.168.1.50")))
                override val routes = listOf(
                    RouteInfo { address -> address.hostAddress?.startsWith("192.168.1.") == true }
                )
            }
            return FakeConnectivityManager(
                activeNetwork = network,
                allNetworks = arrayOf(network),
                capabilities = mapOf(network to caps),
                properties = mapOf(network to props),
            )
        }

        fun empty(): FakeConnectivityManager =
            FakeConnectivityManager(null, emptyArray(), emptyMap(), emptyMap())
    }
}

private class FakeNsdManager : NsdManager() {
    var discoveryListener: DiscoveryListener? = null
    var resolveListener: ResolveListener? = null
    val stoppedDiscoveryListeners = mutableListOf<DiscoveryListener>()
    val serviceInfoCallbacks = linkedMapOf<NsdServiceInfo, ServiceInfoCallback>()
    var unregisteredServiceInfoCallbacks = 0

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

    override fun registerServiceInfoCallback(
        info: NsdServiceInfo,
        executor: java.util.concurrent.Executor,
        callback: ServiceInfoCallback,
    ) {
        serviceInfoCallbacks[info] = callback
    }

    override fun unregisterServiceInfoCallback(callback: ServiceInfoCallback) {
        serviceInfoCallbacks.entries.removeAll { it.value === callback }
        unregisteredServiceInfoCallbacks++
    }
}
