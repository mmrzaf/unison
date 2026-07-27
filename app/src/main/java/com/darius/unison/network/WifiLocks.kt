package com.darius.unison.network

import android.content.Context
import android.net.wifi.WifiManager

class WifiLocks(context: Context) : AutoCloseable {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val multicastLock = wifi.createMulticastLock("unison-mdns").apply { setReferenceCounted(false) }
    private val wifiLock =
        wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "unison-room").apply { setReferenceCounted(false) }

    fun acquireMulticast() {
        if (!multicastLock.isHeld) multicastLock.acquire()
    }

    fun releaseMulticast() {
        if (multicastLock.isHeld) multicastLock.release()
    }

    fun acquireWifi() {
        if (!wifiLock.isHeld) wifiLock.acquire()
    }

    fun releaseWifi() {
        if (wifiLock.isHeld) wifiLock.release()
    }

    fun releaseAll() {
        releaseMulticast(); releaseWifi()
    }

    override fun close() = releaseAll()
}
