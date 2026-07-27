package com.darius.unison.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.darius.unison.model.HotspotInfo
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class LocalHotspotController(
    context: Context,
    private val log: DiagnosticLog,
) {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private val starting = AtomicBoolean(false)
    private val generation = AtomicLong(0)
    private val _state = MutableStateFlow<HotspotInfo?>(null)
    val state: StateFlow<HotspotInfo?> = _state.asStateFlow()

    @Suppress("MissingPermission")
    fun start(onError: (String) -> Unit = {}) {
        if (reservation != null || !starting.compareAndSet(false, true)) return
        val requestGeneration = generation.incrementAndGet()

        try {
            wifi.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                        if (requestGeneration != generation.get()) {
                            value.close()
                            return
                        }
                        starting.set(false)
                        reservation = value
                        val config = value.softApConfiguration
                        _state.value = HotspotInfo(
                            ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                config.wifiSsid?.toString()
                            } else {
                                @Suppress("DEPRECATION")
                                config.ssid
                            } ?: "Unison",
                            passphrase = config.passphrase,
                            securityType = config.securityType,
                        )
                        log.i(TAG, "Local-only hotspot started")
                    }

                    override fun onStopped() {
                        if (requestGeneration == generation.get()) {
                            starting.set(false)
                            reservation = null
                            _state.value = null
                        }
                        log.i(TAG, "Local-only hotspot stopped")
                    }

                    override fun onFailed(reason: Int) {
                        if (requestGeneration != generation.get()) return
                        starting.set(false)
                        reservation = null
                        _state.value = null
                        log.w(TAG, "Local-only hotspot failed reason=$reason")
                        onError("Could not create offline network")
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (error: Throwable) {
            if (requestGeneration != generation.get()) return
            starting.set(false)
            reservation = null
            _state.value = null
            log.w(TAG, "Local-only hotspot failed before callback", error)
            onError("Could not create offline network")
        }
    }

    fun stop() {
        generation.incrementAndGet()
        starting.set(false)
        reservation?.close()
        reservation = null
        _state.value = null
    }

    companion object {
        private const val TAG = "LocalHotspot"
    }
}
