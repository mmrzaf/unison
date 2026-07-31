package android.net.wifi

import android.os.Handler

class WifiSsid { override fun toString(): String = "ssid" }
open class SoftApConfiguration {
    open val wifiSsid: WifiSsid? = null
    open val ssid: String? = null
    open val passphrase: String? = null
    open val securityType: Int = 0
}
open class WifiManager {
    open class LocalOnlyHotspotReservation : AutoCloseable {
        open val softApConfiguration: SoftApConfiguration = SoftApConfiguration()
        override fun close() = Unit
    }
    open class LocalOnlyHotspotCallback {
        open fun onStarted(value: LocalOnlyHotspotReservation) = Unit
        open fun onStopped() = Unit
        open fun onFailed(reason: Int) = Unit
    }
    open fun startLocalOnlyHotspot(callback: LocalOnlyHotspotCallback, handler: Handler) = Unit
}
