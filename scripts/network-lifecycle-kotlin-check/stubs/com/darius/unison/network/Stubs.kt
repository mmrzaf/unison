package com.darius.unison.network

open class WifiLocks {
    var acquireCount: Int = 0
        private set
    var releaseCount: Int = 0
        private set

    open fun acquireMulticast() { acquireCount++ }
    open fun releaseMulticast() { releaseCount++ }
}
