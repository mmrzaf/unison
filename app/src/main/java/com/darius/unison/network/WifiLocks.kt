package com.darius.unison.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.SystemClock
import com.darius.unison.room.port.PowerDemand
import com.darius.unison.room.port.PowerPort

/** Owns the radio/CPU locks required by an explicit active room. */
class WifiLocks(context: Context) : PowerPort {
    private val appContext = context.applicationContext
    private val wifi = appContext.getSystemService(WifiManager::class.java)
    private val power = appContext.getSystemService(PowerManager::class.java)
    private val multicastLock =
        wifi.createMulticastLock("unison-mdns").apply {
            setReferenceCounted(false)
        }
    private val lowLatencyLock =
        wifi
            .createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                "unison-room-low-latency",
            )
            .apply { setReferenceCounted(false) }

    @Suppress("DEPRECATION")
    private val screenOffWifiLock =
        wifi
            .createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "unison-room-screen-off",
            )
            .apply { setReferenceCounted(false) }

    private val cpuWakeLock =
        power
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "com.darius.unison:room-network",
            )
            .apply { setReferenceCounted(false) }

    private var wifiRequired = false
    private var cpuRequired = false
    private var cpuAcquiredAtMs = 0L

    @Synchronized
    fun acquireMulticast() {
        if (!multicastLock.isHeld) multicastLock.acquire()
    }

    @Synchronized
    fun releaseMulticast() {
        if (multicastLock.isHeld) multicastLock.release()
    }

    /**
     * Applies the session's radio/CPU demand. Wi-Fi keeps both screen-on low-latency and screen-off
     * high-performance modes; the CPU lock carries a timeout so a leaked lock cannot survive a
     * broken lifecycle, and active sessions refresh it through repeated application.
     */
    @Synchronized
    override fun apply(demand: PowerDemand) {
        if (demand.wifi) acquireWifi() else releaseWifi()
        setCpuRequired(demand.cpu)
        refresh()
    }

    private fun acquireWifi() {
        wifiRequired = true
        if (!lowLatencyLock.isHeld) lowLatencyLock.acquire()
        if (!screenOffWifiLock.isHeld) screenOffWifiLock.acquire()
    }

    private fun releaseWifi() {
        wifiRequired = false
        if (lowLatencyLock.isHeld) lowLatencyLock.release()
        if (screenOffWifiLock.isHeld) screenOffWifiLock.release()
    }

    private fun setCpuRequired(required: Boolean) {
        cpuRequired = required
        if (!required) {
            releaseCpuLocked()
            return
        }
        refreshCpuLocked(SystemClock.elapsedRealtime())
    }

    private fun refresh() {
        if (wifiRequired) {
            if (!lowLatencyLock.isHeld) lowLatencyLock.acquire()
            if (!screenOffWifiLock.isHeld) screenOffWifiLock.acquire()
        }
        if (cpuRequired) refreshCpuLocked(SystemClock.elapsedRealtime())
    }

    @Synchronized
    fun releaseAll() {
        cpuRequired = false
        releaseCpuLocked()
        releaseMulticast()
        releaseWifi()
    }

    private fun refreshCpuLocked(nowMs: Long) {
        val needsRefresh = !cpuWakeLock.isHeld || nowMs - cpuAcquiredAtMs >= CPU_REFRESH_INTERVAL_MS
        if (!needsRefresh) return
        if (cpuWakeLock.isHeld) cpuWakeLock.release()
        cpuWakeLock.acquire(CPU_WAKE_LOCK_TIMEOUT_MS)
        cpuAcquiredAtMs = nowMs
    }

    private fun releaseCpuLocked() {
        if (cpuWakeLock.isHeld) cpuWakeLock.release()
        cpuAcquiredAtMs = 0L
    }

    override fun close() = releaseAll()

    private companion object {
        const val CPU_WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1_000L
        const val CPU_REFRESH_INTERVAL_MS = 8 * 60 * 1_000L
    }
}
