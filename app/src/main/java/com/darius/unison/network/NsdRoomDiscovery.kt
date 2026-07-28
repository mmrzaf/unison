package com.darius.unison.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

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
            this.port = port
            setAttribute("rid", roomId)
            setAttribute("v", PROTOCOL_VERSION.toString())
            setAttribute("name", roomName.take(80))
            setAttribute("term", term.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                if (registrationListener !== this) return
                log.i(TAG, "NSD registered ${serviceInfo.serviceName}")
                onRegistered(serviceInfo.serviceName)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (registrationListener !== this) return
                log.w(TAG, "NSD registration failed code=$errorCode")
                registrationListener = null
                onError("Could not make this room visible")
                if (discoveryListener == null) locks.releaseMulticast()
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (registrationListener === this) {
                    log.w(TAG, "NSD unregister failed code=$errorCode")
                }
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
     * Runs one NSD browse session. [RoomRuntime] owns the user-triggered scan window and closes this
     * flow when that bounded search ends.
     *
     * Android 11-13's legacy resolveService API allows only one active resolution reliably on
     * several vendor builds. Resolutions are serialized and individually timed out so one missing
     * callback cannot block every room discovered after it.
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
            val next = synchronized(resolveLock) {
                if (!active.get() || resolvingServiceName != null || pending.isEmpty()) return@resolveNext
                pending.removeFirst().also { resolvingServiceName = it.serviceName }
            }
            val finished = AtomicBoolean(false)
            var timeoutJob: Job? = null
            var resolverCleanup: (() -> Unit)? = null

            fun finish(resolved: NsdServiceInfo? = null): Boolean {
                if (!finished.compareAndSet(false, true)) return false
                timeoutJob?.cancel()
                resolverCleanup?.invoke()
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
                log.w(TAG, "NSD resolve timed out for ${next.serviceName}; skipping service")
                finish()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val callback = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        log.w(TAG, "NSD info callback failed code=$errorCode for ${next.serviceName}")
                        finish()
                    }

                    override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                        finish(serviceInfo)
                    }

                    override fun onServiceLost() {
                        finish()
                    }

                    override fun onServiceInfoCallbackUnregistered() = Unit
                }
                resolverCleanup = { runCatching { nsd.unregisterServiceInfoCallback(callback) } }
                runCatching { nsd.registerServiceInfoCallback(next, mainExecutor, callback) }
                    .onFailure { error ->
                        log.w(TAG, "NSD info callback could not start for ${next.serviceName}", error)
                        finish()
                    }
            } else {
                val resolver = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        log.w(TAG, "NSD resolve failed code=$errorCode for ${serviceInfo.serviceName}")
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
                    log.w(TAG, "NSD resolver could not start for ${next.serviceName}", error)
                    finish()
                }
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                if (!active.get() || discoveryListener !== this) return
                log.i(TAG, "NSD discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!active.get() || discoveryListener !== this || serviceInfo.serviceType != SERVICE_TYPE) return
                val shouldResolve = synchronized(resolveLock) {
                    if (pending.size >= MAX_PENDING_RESOLUTIONS) return@synchronized false
                    if (!queuedNames.add(serviceInfo.serviceName)) return@synchronized false
                    pending.addLast(serviceInfo)
                    resolvingServiceName == null
                }
                if (shouldResolve) resolveNext()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (!active.get() || discoveryListener !== this) return
                synchronized(resolveLock) {
                    pending.removeAll { it.serviceName == serviceInfo.serviceName }
                    if (resolvingServiceName != serviceInfo.serviceName) {
                        queuedNames.remove(serviceInfo.serviceName)
                    }
                }
                trySend(NsdDiscoveryEvent.Lost(serviceInfo.serviceName))
            }

            override fun onDiscoveryStopped(serviceType: String) {
                if (!active.get() || discoveryListener !== this) return
                log.i(TAG, "NSD discovery stopped unexpectedly")
                discoveryListener = null
                if (registrationListener == null) locks.releaseMulticast()
                close(NsdDiscoveryException("Room discovery stopped unexpectedly", recoverable = true))
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (!active.get() || discoveryListener !== this) return
                log.w(TAG, "NSD discovery start failed code=$errorCode")
                discoveryListener = null
                if (registrationListener == null) locks.releaseMulticast()
                close(
                    NsdDiscoveryException(
                        message = "Could not start nearby room discovery (code $errorCode)",
                        recoverable = isRecoverable(errorCode),
                    )
                )
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (discoveryListener === this) {
                    log.w(TAG, "NSD discovery stop failed code=$errorCode")
                }
            }
        }
        discoveryListener = listener
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { error ->
                if (discoveryListener === listener) discoveryListener = null
                if (registrationListener == null) locks.releaseMulticast()
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
            }
            if (discoveryListener === listener) stopDiscovery()
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsd.stopServiceDiscovery(listener) }
                .onFailure { error -> log.w(TAG, "NSD discovery stop could not start", error) }
        }
        discoveryListener = null
        if (registrationListener == null) locks.releaseMulticast()
    }

    fun stopAdvertising() {
        registrationListener?.let { listener ->
            runCatching { nsd.unregisterService(listener) }
                .onFailure { error -> log.w(TAG, "NSD unregister could not start", error) }
        }
        registrationListener = null
        if (discoveryListener == null) locks.releaseMulticast()
    }


    private fun NsdServiceInfo.toDiscoveredRoom(): DiscoveredRoom? {
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hostAddresses.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            host
        } ?: return null
        if (!NetworkAddressPolicy.isAllowed(address)) return null

        val resolvedRoomId = (attribute("rid") ?: serviceName.substringAfter("Unison-", ""))
            .takeIf { it.length in 8..128 && ROOM_ID_PATTERN.matches(it) }
            ?: return null
        val resolvedRoomName = (attribute("name") ?: serviceName)
            .filterNot { it.isISOControl() }
            .trim()
            .take(80)
            .ifBlank { "Unison room" }
        val version = attribute("v")?.toIntOrNull()?.takeIf { it == PROTOCOL_VERSION } ?: return null
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

    private fun isRecoverable(errorCode: Int): Boolean =
        errorCode != NsdManager.FAILURE_BAD_PARAMETERS

    companion object {
        const val SERVICE_TYPE = "_unison._tcp."
        private const val TAG = "NsdRoomDiscovery"
        private const val MAX_PENDING_RESOLUTIONS = 32
        private const val RESOLUTION_TIMEOUT_MS = 3_500L
        private val ROOM_ID_PATTERN = Regex("[A-Za-z0-9-]+")
    }
}
