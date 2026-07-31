package com.darius.unison.network

import java.net.InetAddress

open class WifiLocks {
    var acquireCount: Int = 0
        private set
    var releaseCount: Int = 0
        private set

    open fun acquireMulticast() {
        acquireCount++
    }

    open fun releaseMulticast() {
        releaseCount++
    }
}

object NetworkAddressPolicy {
    fun isAllowed(address: InetAddress): Boolean = true
    fun score(interfaceName: String, address: InetAddress, preferHotspot: Boolean): Int = 0
}
