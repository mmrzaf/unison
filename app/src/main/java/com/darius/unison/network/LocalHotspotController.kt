package com.darius.unison.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.darius.unison.model.HotspotInfo
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalHotspotController(
    context: Context,
    private val log: DiagnosticLog,
) {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val stateLock = Any()
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var starting = false
    private var generation = 0L
    private val _state = MutableStateFlow<HotspotInfo?>(null)
    val state: StateFlow<HotspotInfo?> = _state.asStateFlow()

    @Suppress("MissingPermission")
    fun start(onError: (String) -> Unit = {}) {
        val requestGeneration =
            synchronized(stateLock) {
                if (reservation != null || starting) return
                starting = true
                ++generation
            }

        try {
            wifi.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                        val accepted =
                            synchronized(stateLock) {
                                if (requestGeneration != generation) {
                                    false
                                } else {
                                    starting = false
                                    reservation = value
                                    val config = value.softApConfiguration
                                    _state.value =
                                        HotspotInfo(
                                            ssid =
                                                if (
                                                    Build.VERSION.SDK_INT >=
                                                        Build.VERSION_CODES.TIRAMISU
                                                ) {
                                                    config.wifiSsid?.toString()
                                                } else {
                                                    @Suppress("DEPRECATION") config.ssid
                                                } ?: "Unison",
                                            passphrase = config.passphrase,
                                            securityType = config.securityType,
                                        )
                                    true
                                }
                            }
                        if (!accepted) {
                            runCatching { value.close() }
                            return
                        }
                        log.info(TAG, DiagnosticCategory.NETWORK, "network.hotspot.started")
                    }

                    override fun onStopped() {
                        val current =
                            synchronized(stateLock) {
                                if (requestGeneration != generation) {
                                    false
                                } else {
                                    starting = false
                                    reservation = null
                                    _state.value = null
                                    true
                                }
                            }
                        if (current)
                            log.info(TAG, DiagnosticCategory.NETWORK, "network.hotspot.stopped")
                    }

                    override fun onFailed(reason: Int) {
                        val current =
                            synchronized(stateLock) {
                                if (requestGeneration != generation) {
                                    false
                                } else {
                                    starting = false
                                    reservation = null
                                    _state.value = null
                                    true
                                }
                            }
                        if (!current) return
                        log.warn(
                            TAG,
                            DiagnosticCategory.NETWORK,
                            "network.hotspot.failed",
                            attributes = mapOf("network.failure_reason" to reason),
                        )
                        onError("Could not create offline network")
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (error: Exception) {
            val current =
                synchronized(stateLock) {
                    if (requestGeneration != generation) {
                        false
                    } else {
                        starting = false
                        reservation = null
                        _state.value = null
                        true
                    }
                }
            if (!current) return
            log.warn(
                TAG,
                DiagnosticCategory.NETWORK,
                "network.hotspot.start_failed",
                throwable = error,
            )
            onError("Could not create offline network")
        }
    }

    fun stop() {
        val toClose =
            synchronized(stateLock) {
                generation++
                starting = false
                reservation.also {
                    reservation = null
                    _state.value = null
                }
            }
        runCatching { toClose?.close() }
    }

    companion object {
        private const val TAG = "LocalHotspot"
    }
}
