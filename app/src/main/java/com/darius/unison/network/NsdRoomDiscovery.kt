package com.darius.unison.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val registrationListener = AtomicReference<NsdManager.RegistrationListener?>(null)
    private val discoveryListener = AtomicReference<NsdManager.DiscoveryListener?>(null)

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
     * Runs one NSD browse session. [RoomRuntime] owns the user-triggered scan window and closes
     * this flow when that bounded search ends.
     *
     * Android 11–13's resolveService API allows only one active resolution reliably on several
     * vendor builds. Resolutions are serialized and individually timed out so one missing callback
     * cannot block every room discovered after it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun discover(): Flow<NsdDiscoveryEvent> = callbackFlow {
        stopDiscovery()
        locks.acquireMulticast()
        val active = AtomicBoolean(true)
        val resolveLock = Any()
        val pending = ArrayDeque<NsdServiceInfo>()
        val queuedNames = mutableSetOf<String>()
        var resolvingServiceName: String? = null
        lateinit var resolveNext: () -> Unit

        resolveNext = resolveNext@{
            val next =
                synchronized(resolveLock) {
                    if (!active.get() || resolvingServiceName != null || pending.isEmpty())
                        return@resolveNext
                    pending.removeFirst().also { resolvingServiceName = it.serviceName }
                }
            val finished = AtomicBoolean(false)
            var timeoutJob: Job? = null

            fun finish(resolved: NsdServiceInfo? = null): Boolean {
                if (!finished.compareAndSet(false, true)) return false
                timeoutJob?.cancel()
                try {
                    resolved?.toDiscoveredRoom()?.let { room ->
                        trySend(NsdDiscoveryEvent.Found(room))
                    }
                } finally {
                    synchronized(resolveLock) {
                        queuedNames.remove(next.serviceName)
                        resolvingServiceName = null
                    }
                    resolveNext()
                }
                return true
            }

            timeoutJob = launch {
                delay(RESOLUTION_TIMEOUT_MS)
                // A broken advertisement must not terminate the user's whole bounded search.
                // Drop only this resolution and continue with the remaining services.
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
                    @Suppress("DEPRECATION") nsd.resolveService(next, resolver)
                }
                .onFailure { error ->
                    log.warn(
                        TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.resolve_start_failed",
                        throwable = error,
                    )
                    finish()
                }
        }

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
                    )
                        return
                    val shouldResolve =
                        synchronized(resolveLock) {
                            if (pending.size >= MAX_PENDING_RESOLUTIONS) return@synchronized false
                            if (!queuedNames.add(serviceInfo.serviceName)) return@synchronized false
                            pending.addLast(serviceInfo)
                            resolvingServiceName == null
                        }
                    if (shouldResolve) resolveNext()
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    if (!active.get() || discoveryListener.get() !== this) return
                    synchronized(resolveLock) {
                        pending.removeAll { it.serviceName == serviceInfo.serviceName }
                        if (resolvingServiceName != serviceInfo.serviceName) {
                            queuedNames.remove(serviceInfo.serviceName)
                        }
                    }
                    trySend(NsdDiscoveryEvent.Lost(serviceInfo.serviceName))
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    if (!active.get() || discoveryListener.get() !== this) return
                    log.warn(TAG, DiagnosticCategory.DISCOVERY, "discovery.nsd.stopped_unexpectedly")
                    if (!discoveryListener.compareAndSet(this, null)) return
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
            synchronized(resolveLock) {
                pending.clear()
                queuedNames.clear()
                resolvingServiceName = null
            }
            if (discoveryListener.compareAndSet(listener, null)) {
                stopDiscoveryListener(listener)
                if (registrationListener.get() == null) locks.releaseMulticast()
            }
        }
    }

    fun stopDiscovery() {
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

    private fun NsdServiceInfo.toDiscoveredRoom(): DiscoveredRoom? {
        @Suppress("DEPRECATION")
        val address = host?.takeIf(NetworkAddressPolicy::isAllowed) ?: return null

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

    private fun NsdServiceInfo.attribute(key: String): String? =
        attributes[key]?.toString(StandardCharsets.UTF_8)

    companion object {
        const val SERVICE_TYPE = "_unison._tcp."
        private const val TAG = "NsdRoomDiscovery"
        private const val MAX_PENDING_RESOLUTIONS = 32
        private const val RESOLUTION_TIMEOUT_MS = 3_500L
        private val ROOM_ID_PATTERN = Regex("[A-Za-z0-9-]+")
    }
}
