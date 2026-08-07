package com.darius.unison.network

import androidx.annotation.RequiresApi
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

sealed interface NsdDiscoveryEvent {
    data class Found(val room: DiscoveredRoom) : NsdDiscoveryEvent

    data class Lost(val serviceName: String) : NsdDiscoveryEvent
}

class NsdDiscoveryException(
    message: String,
    val recoverable: Boolean,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class NsdRoomDiscovery(
    context: Context,
    private val locks: WifiLocks,
    private val log: DiagnosticLog,
    private val networkRouter: AndroidLocalNetworkRouter,
) {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(NsdManager::class.java)
    private val registrationListener = AtomicReference<NsdManager.RegistrationListener?>(null)
    private val discoveryListener = AtomicReference<NsdManager.DiscoveryListener?>(null)
    private val modernServiceCallbacks = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()

    fun advertise(
        roomId: String,
        roomName: String,
        port: Int,
        term: Long,
        onRegistered: (String) -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        stopAdvertising()
        locks.acquireMulticast()
        val info =
            NsdServiceInfo().apply {
                serviceName = "Unison-${roomId.take(8)}"
                serviceType = SERVICE_TYPE
                this.port = port
                setAttribute("rid", roomId)
                setAttribute("v", PROTOCOL_VERSION.toString())
                setAttribute("name", roomName.take(80))
                setAttribute("term", term.toString())
            }
        val listener =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    if (registrationListener.get() !== this) return
                    log.info(TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.registered")
                    onRegistered(serviceInfo.serviceName)
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (registrationListener.get() !== this) return
                    log.warn(
                        TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.registration_failed",
                        attributes = mapOf("nsd.error_code" to errorCode),
                    )
                    if (!registrationListener.compareAndSet(this, null)) return
                    onError("Could not make this room visible")
                    if (discoveryListener.get() == null) locks.releaseMulticast()
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (registrationListener.get() === this) {
                        log.warn(
                            TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.unregistration_failed",
                            attributes = mapOf("nsd.error_code" to errorCode),
                        )
                    }
                }
            }
        registrationListener.set(listener)
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { error ->
                if (registrationListener.compareAndSet(listener, null)) {
                    if (discoveryListener.get() == null) locks.releaseMulticast()
                    log.warn(
                        TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.registration_start_failed",
                        throwable = error,
                    )
                    onError("Could not make this room visible")
                }
            }
    }

    /**
     * Runs one bounded NSD browse session.
     *
     * Android 11–13 use serialized one-shot resolution because several vendor builds reject
     * concurrent resolveService calls. Android 14+ uses ServiceInfoCallback, which is the platform's
     * non-stale service-resolution API and delivers all host addresses plus the owning Network.
     * Both paths feed the same process-local network router; protocol/domain models remain unchanged.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun discover(): Flow<NsdDiscoveryEvent> = callbackFlow {
        stopDiscovery()
        networkRouter.clearDiscoveryRoutes()
        locks.acquireMulticast()
        val active = AtomicBoolean(true)
        val legacyResolver = LegacyResolverQueue(this, active)

        val listener =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    if (!active.get() || discoveryListener.get() !== this) return
                    log.info(TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.started")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (
                        !active.get() ||
                            discoveryListener.get() !== this ||
                            serviceInfo.serviceType != SERVICE_TYPE
                    ) return
                    if (Build.VERSION.SDK_INT >= 34) {
                        registerModernService(serviceInfo, active, this@callbackFlow)
                    } else {
                        legacyResolver.enqueue(serviceInfo)
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    if (!active.get() || discoveryListener.get() !== this) return
                    legacyResolver.remove(serviceInfo.serviceName)
                    if (Build.VERSION.SDK_INT >= 34) {
                        unregisterModernService(serviceInfo)
                    }
                    trySend(NsdDiscoveryEvent.Lost(serviceInfo.serviceName))
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    if (!active.get() || discoveryListener.get() !== this) return
                    log.warn(TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.stopped_unexpectedly")
                    if (!discoveryListener.compareAndSet(this, null)) return
                    unregisterAllModernServices()
                    if (registrationListener.get() == null) locks.releaseMulticast()
                    close(
                        NsdDiscoveryException(
                            "Room discovery stopped unexpectedly",
                            recoverable = true,
                        )
                    )
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    if (!active.get() || discoveryListener.get() !== this) return
                    log.warn(
                        TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.start_failed",
                        attributes = mapOf("nsd.error_code" to errorCode),
                    )
                    if (!discoveryListener.compareAndSet(this, null)) return
                    unregisterAllModernServices()
                    if (registrationListener.get() == null) locks.releaseMulticast()
                    close(
                        NsdDiscoveryException(
                            message = "Could not start nearby room discovery (code $errorCode)",
                            recoverable = true,
                        )
                    )
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    if (discoveryListener.get() === this) {
                        log.warn(
                            TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.stop_failed",
                            attributes = mapOf("nsd.error_code" to errorCode),
                        )
                    }
                }
            }
        discoveryListener.set(listener)
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { error ->
                if (!discoveryListener.compareAndSet(listener, null)) return@onFailure
                unregisterAllModernServices()
                if (registrationListener.get() == null) locks.releaseMulticast()
                close(
                    NsdDiscoveryException(
                        message = "Could not start nearby room discovery",
                        recoverable = error !is SecurityException,
                        cause = error,
                    )
                )
            }
        awaitClose {
            active.set(false)
            legacyResolver.clear()
            unregisterAllModernServices()
            if (discoveryListener.compareAndSet(listener, null)) {
                stopDiscoveryListener(listener)
                if (registrationListener.get() == null) locks.releaseMulticast()
            }
        }
    }

    fun stopDiscovery() {
        unregisterAllModernServices()
        val listener = discoveryListener.getAndSet(null) ?: return
        stopDiscoveryListener(listener)
        if (registrationListener.get() == null) locks.releaseMulticast()
    }

    private fun stopDiscoveryListener(listener: NsdManager.DiscoveryListener) {
        runCatching { nsd.stopServiceDiscovery(listener) }
            .onFailure { error ->
                log.warn(
                    TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.stop_start_failed", throwable = error,
                )
            }
    }

    fun stopAdvertising() {
        val listener = registrationListener.getAndSet(null) ?: return
        runCatching { nsd.unregisterService(listener) }
            .onFailure { error ->
                log.warn(
                    TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.unregister_start_failed", throwable = error,
                )
            }
        if (discoveryListener.get() == null) locks.releaseMulticast()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @RequiresApi(34)
    private fun registerModernService(
        serviceInfo: NsdServiceInfo,
        active: AtomicBoolean,
        producer: ProducerScope<NsdDiscoveryEvent>,
    ) {
        val callbackKey = modernServiceKey(serviceInfo)
        if (modernServiceCallbacks.containsKey(callbackKey)) return
        val callback =
            object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    modernServiceCallbacks.remove(callbackKey, this)
                    log.warn(
                        TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.service_info_failed",
                        attributes = mapOf("nsd.error_code" to errorCode),
                    )
                }

                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    if (!active.get()) return
                    serviceInfo.toDiscoveredRoom()?.let { producer.trySend(NsdDiscoveryEvent.Found(it)) }
                }

                override fun onServiceLost() {
                    if (!active.get()) return
                    producer.trySend(NsdDiscoveryEvent.Lost(serviceInfo.serviceName))
                }

                override fun onServiceInfoCallbackUnregistered() = Unit
            }
        if (modernServiceCallbacks.putIfAbsent(callbackKey, callback) != null) return
        runCatching {
            nsd.registerServiceInfoCallback(serviceInfo, appContext.mainExecutor, callback)
        }.onFailure { error ->
            modernServiceCallbacks.remove(callbackKey, callback)
            log.warn(
                TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.service_info_start_failed",
                throwable = error,
            )
        }
    }

    @RequiresApi(34)
    private fun unregisterModernService(serviceInfo: NsdServiceInfo) {
        val callback = modernServiceCallbacks.remove(modernServiceKey(serviceInfo)) ?: return
        runCatching { nsd.unregisterServiceInfoCallback(callback) }
            .onFailure { error ->
                log.debug(
                    TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.service_info_stop_failed",
                    throwable = error,
                )
            }
    }

    private fun unregisterAllModernServices() {
        if (Build.VERSION.SDK_INT < 34) return
        modernServiceCallbacks.entries.toList().forEach { (key, callback) ->
            if (!modernServiceCallbacks.remove(key, callback)) return@forEach
            runCatching { nsd.unregisterServiceInfoCallback(callback) }
                .onFailure { error ->
                    log.debug(
                        TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.service_info_stop_failed",
                        throwable = error,
                    )
                }
        }
    }

    @RequiresApi(34)
    private fun modernServiceKey(serviceInfo: NsdServiceInfo): String =
        "${serviceInfo.serviceName}|${serviceInfo.network?.networkHandle ?: 0L}"

    private fun NsdServiceInfo.toDiscoveredRoom(): DiscoveredRoom? {
        val addresses = resolvedAddresses()
        val resolvedNetwork = if (Build.VERSION.SDK_INT >= 33) network else null
        val address = networkRouter.rememberResolvedService(addresses, resolvedNetwork) ?: return null

        val resolvedRoomId =
            (attribute("rid") ?: serviceName.substringAfter("Unison-", "")).takeIf {
                it.length in 8..128 && ROOM_ID_PATTERN.matches(it)
            } ?: return null
        val resolvedRoomName =
            (attribute("name") ?: serviceName)
                .filterNot { it.isISOControl() }
                .trim()
                .take(80)
                .ifBlank { "Unison room" }
        val version =
            attribute("v")?.toIntOrNull()?.takeIf { it == PROTOCOL_VERSION } ?: return null
        val resolvedTerm = attribute("term")?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val resolvedPort = port.takeIf { it in 1..65535 } ?: return null
        val hostAddress = address.hostAddress ?: return null

        log.debug(
            TAG,
            DiagnosticCategory.DISCOVERY,
            "discovery.nsd.service_resolved",
            attributes = mapOf(
                "nsd.address_count" to addresses.size,
                "network.address_family" to if (address.address.size == 4) "IPV4" else "IPV6",
                "network.bound_available" to (resolvedNetwork != null),
            ),
        )
        return DiscoveredRoom(
            serviceName = serviceName,
            roomId = resolvedRoomId,
            roomName = resolvedRoomName,
            hostAddress = hostAddress,
            port = resolvedPort,
            protocolVersion = version,
            term = resolvedTerm,
        )
    }

    private fun NsdServiceInfo.resolvedAddresses(): List<InetAddress> =
        if (Build.VERSION.SDK_INT >= 34) {
            hostAddresses.filter(NetworkAddressPolicy::isAllowed)
        } else {
            @Suppress("DEPRECATION")
            listOfNotNull(host?.takeIf(NetworkAddressPolicy::isAllowed))
        }

    private fun NsdServiceInfo.attribute(key: String): String? =
        attributes[key]?.toString(StandardCharsets.UTF_8)

    /** Serialized resolver used only on Android 11–13. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private inner class LegacyResolverQueue(
        private val producer: ProducerScope<NsdDiscoveryEvent>,
        private val active: AtomicBoolean,
    ) {
        private val lock = Any()
        private val pending = ArrayDeque<NsdServiceInfo>()
        private val queuedNames = mutableSetOf<String>()
        private var resolvingServiceName: String? = null

        fun enqueue(serviceInfo: NsdServiceInfo) {
            val shouldResolve =
                synchronized(lock) {
                    if (pending.size >= MAX_PENDING_RESOLUTIONS) return@synchronized false
                    if (!queuedNames.add(serviceInfo.serviceName)) return@synchronized false
                    pending.addLast(serviceInfo)
                    resolvingServiceName == null
                }
            if (shouldResolve) resolveNext()
        }

        fun remove(serviceName: String) {
            synchronized(lock) {
                pending.removeAll { it.serviceName == serviceName }
                if (resolvingServiceName != serviceName) queuedNames.remove(serviceName)
            }
        }

        fun clear() {
            synchronized(lock) {
                pending.clear()
                queuedNames.clear()
                resolvingServiceName = null
            }
        }

        private fun resolveNext() {
            if (Build.VERSION.SDK_INT >= 34) return
            val next =
                synchronized(lock) {
                    if (!active.get() || resolvingServiceName != null || pending.isEmpty()) return
                    pending.removeFirst().also { resolvingServiceName = it.serviceName }
                }
            val finished = AtomicBoolean(false)
            var timeoutJob: Job? = null

            fun finish(resolved: NsdServiceInfo? = null): Boolean {
                if (!finished.compareAndSet(false, true)) return false
                timeoutJob?.cancel()
                try {
                    resolved?.toDiscoveredRoom()?.let { room ->
                        producer.trySend(NsdDiscoveryEvent.Found(room))
                    }
                } finally {
                    synchronized(lock) {
                        queuedNames.remove(next.serviceName)
                        resolvingServiceName = null
                    }
                    resolveNext()
                }
                return true
            }

            timeoutJob = producer.launch {
                delay(RESOLUTION_TIMEOUT_MS)
                log.warn(TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.resolve_timeout")
                finish()
            }
            val resolver =
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        log.warn(
                            TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.resolve_failed",
                            attributes = mapOf("nsd.error_code" to errorCode),
                        )
                        finish()
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        finish(resolved)
                    }
                }
            runCatching {
                @Suppress("DEPRECATION")
                nsd.resolveService(next, resolver)
            }.onFailure { error ->
                log.warn(
                    TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.resolve_start_failed",
                    throwable = error,
                )
                finish()
            }
        }
    }

    companion object {
        const val SERVICE_TYPE = "_unison._tcp."
        private const val TAG = "NsdRoomDiscovery"
        private const val MAX_PENDING_RESOLUTIONS = 32
        private const val RESOLUTION_TIMEOUT_MS = 3_500L
        private val ROOM_ID_PATTERN = Regex("[A-Za-z0-9-]+")
    }
}
