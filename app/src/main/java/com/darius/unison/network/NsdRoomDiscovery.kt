package com.darius.unison.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

sealed interface NsdDiscoveryEvent {
    data class Found(val room: DiscoveredRoom) : NsdDiscoveryEvent
    data class Lost(val serviceName: String) : NsdDiscoveryEvent
}

class NsdRoomDiscovery(
    context: Context,
    private val locks: WifiLocks,
    private val log: DiagnosticLog,
) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val mainExecutor = context.mainExecutor
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

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
        val info = NsdServiceInfo().apply {
            serviceName = "Unison-${roomId.take(8)}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("rid", roomId)
            setAttribute("v", PROTOCOL_VERSION.toString())
            setAttribute("name", roomName.take(80))
            setAttribute("term", term.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                log.i(TAG, "NSD registered ${serviceInfo.serviceName}")
                onRegistered(serviceInfo.serviceName)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                log.w(TAG, "NSD registration failed code=$errorCode")
                if (registrationListener === this) registrationListener = null
                onError("Could not make this room visible")
                if (discoveryListener == null) locks.releaseMulticast()
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                log.w(TAG, "NSD unregister failed code=$errorCode")
            }
        }
        registrationListener = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { error ->
                if (registrationListener === listener) registrationListener = null
                locks.releaseMulticast()
                log.w(TAG, "NSD registration could not start", error)
                onError("Could not make this room visible")
            }
    }

    /**
     * Android 11-13's legacy resolveService API allows only one active resolution reliably on
     * several vendor builds. Service-found callbacks can arrive in bursts, so resolutions are
     * serialized here rather than racing one resolver per callback.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun discover(): Flow<NsdDiscoveryEvent> = callbackFlow {
        stopDiscovery()
        locks.acquireMulticast()
        val active = AtomicBoolean(true)
        val resolveLock = Any()
        val pending = ArrayDeque<NsdServiceInfo>()
        val queuedNames = mutableSetOf<String>()
        var resolving = false
        lateinit var resolveNext: () -> Unit

        fun finishResolution(serviceName: String) {
            synchronized(resolveLock) {
                queuedNames.remove(serviceName)
                resolving = false
            }
            resolveNext()
        }

        resolveNext = resolveNext@{
            val next = synchronized(resolveLock) {
                if (!active.get() || resolving || pending.isEmpty()) return@resolveNext
                resolving = true
                pending.removeFirst()
            }
            fun handleResolved(resolved: NsdServiceInfo) {
                try {
                    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        resolved.hostAddresses.firstOrNull()
                    } else {
                        @Suppress("DEPRECATION")
                        resolved.host
                    } ?: return
                    if (!NetworkAddressPolicy.isAllowed(address)) return
                    val roomId = (resolved.attribute("rid") ?: resolved.serviceName.substringAfter("Unison-", ""))
                        .takeIf { it.length in 8..128 && ROOM_ID_PATTERN.matches(it) }
                        ?: return
                    val roomName = (resolved.attribute("name") ?: resolved.serviceName)
                        .filterNot { it.isISOControl() }
                        .trim()
                        .take(80)
                        .ifBlank { "Unison room" }
                    val version = resolved.attribute("v")?.toIntOrNull() ?: return
                    if (version != PROTOCOL_VERSION) return
                    val term = resolved.attribute("term")?.toLongOrNull()?.takeIf { it > 0 } ?: return
                    if (resolved.port !in 1..65535) return
                    trySend(
                        NsdDiscoveryEvent.Found(
                            DiscoveredRoom(
                                serviceName = resolved.serviceName,
                                roomId = roomId,
                                roomName = roomName,
                                hostAddress = address.hostAddress ?: return,
                                port = resolved.port,
                                protocolVersion = version,
                                term = term,
                            )
                        )
                    )
                } finally {
                    finishResolution(next.serviceName)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val callback = object : NsdManager.ServiceInfoCallback {
                    private val finished = AtomicBoolean(false)

                    private fun finish(resolved: NsdServiceInfo? = null) {
                        if (!finished.compareAndSet(false, true)) return
                        runCatching { nsd.unregisterServiceInfoCallback(this) }
                        if (resolved == null) finishResolution(next.serviceName) else handleResolved(resolved)
                    }

                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        log.w(TAG, "NSD info callback failed code=$errorCode for ${next.serviceName}")
                        finish()
                    }

                    override fun onServiceUpdated(serviceInfo: NsdServiceInfo) = finish(serviceInfo)
                    override fun onServiceLost() = finish()
                    override fun onServiceInfoCallbackUnregistered() = Unit
                }
                runCatching { nsd.registerServiceInfoCallback(next, mainExecutor, callback) }
                    .onFailure {
                        log.w(TAG, "NSD info callback could not start for ${next.serviceName}", it)
                        finishResolution(next.serviceName)
                    }
            } else {
                val resolver = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        log.w(TAG, "NSD resolve failed code=$errorCode for ${serviceInfo.serviceName}")
                        finishResolution(next.serviceName)
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) = handleResolved(resolved)
                }
                runCatching {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(next, resolver)
                }.onFailure {
                    log.w(TAG, "NSD resolver could not start for ${next.serviceName}", it)
                    finishResolution(next.serviceName)
                }
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = log.i(TAG, "NSD discovery started")

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!active.get() || serviceInfo.serviceType != SERVICE_TYPE) return
                val shouldResolve = synchronized(resolveLock) {
                    if (pending.size >= MAX_PENDING_RESOLUTIONS) return@synchronized false
                    if (!queuedNames.add(serviceInfo.serviceName)) return@synchronized false
                    pending.addLast(serviceInfo)
                    !resolving
                }
                if (shouldResolve) resolveNext()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(resolveLock) {
                    pending.removeAll { it.serviceName == serviceInfo.serviceName }
                    if (!resolving) queuedNames.remove(serviceInfo.serviceName)
                }
                trySend(NsdDiscoveryEvent.Lost(serviceInfo.serviceName))
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("Could not find nearby rooms"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discoveryListener = listener
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { error ->
                if (discoveryListener === listener) discoveryListener = null
                locks.releaseMulticast()
                close(IllegalStateException("Could not find nearby rooms", error))
            }
        awaitClose {
            active.set(false)
            synchronized(resolveLock) {
                pending.clear()
                queuedNames.clear()
            }
            if (discoveryListener === listener) stopDiscovery()
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
        if (registrationListener == null) locks.releaseMulticast()
    }

    fun stopAdvertising() {
        registrationListener?.let { listener -> runCatching { nsd.unregisterService(listener) } }
        registrationListener = null
        if (discoveryListener == null) locks.releaseMulticast()
    }

    private fun NsdServiceInfo.attribute(key: String): String? = attributes[key]?.toString(StandardCharsets.UTF_8)

    companion object {
        const val SERVICE_TYPE = "_unison._tcp."
        private const val TAG = "NsdRoomDiscovery"
        private const val MAX_PENDING_RESOLUTIONS = 100
        private val ROOM_ID_PATTERN = Regex("[A-Za-z0-9-]+")
    }
}
