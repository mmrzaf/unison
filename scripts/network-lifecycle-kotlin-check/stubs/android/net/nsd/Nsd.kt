package android.net.nsd

import java.net.InetAddress

open class NsdServiceInfo {
    var serviceName: String = ""
    var serviceType: String = ""
    var port: Int = 0
    var host: InetAddress? = null
    private val mutableAttributes = linkedMapOf<String, ByteArray>()
    val attributes: Map<String, ByteArray> get() = mutableAttributes
    fun setAttribute(key: String, value: String) {
        mutableAttributes[key] = value.encodeToByteArray()
    }
}

open class NsdManager {
    interface RegistrationListener {
        fun onServiceRegistered(serviceInfo: NsdServiceInfo)
        fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int)
        fun onServiceUnregistered(serviceInfo: NsdServiceInfo)
        fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int)
    }
    interface DiscoveryListener {
        fun onDiscoveryStarted(serviceType: String)
        fun onServiceFound(serviceInfo: NsdServiceInfo)
        fun onServiceLost(serviceInfo: NsdServiceInfo)
        fun onDiscoveryStopped(serviceType: String)
        fun onStartDiscoveryFailed(serviceType: String, errorCode: Int)
        fun onStopDiscoveryFailed(serviceType: String, errorCode: Int)
    }
    interface ResolveListener {
        fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int)
        fun onServiceResolved(resolved: NsdServiceInfo)
    }
    open fun registerService(info: NsdServiceInfo, protocol: Int, listener: RegistrationListener) = Unit
    open fun unregisterService(listener: RegistrationListener) = Unit
    open fun discoverServices(type: String, protocol: Int, listener: DiscoveryListener) = Unit
    open fun stopServiceDiscovery(listener: DiscoveryListener) = Unit
    open fun resolveService(info: NsdServiceInfo, listener: ResolveListener) = Unit
    companion object {
        const val PROTOCOL_DNS_SD = 1
    }
}
