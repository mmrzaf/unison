package android.net

import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

open class Network(
    val networkHandle: Long = 1L,
    open val socketFactory: SocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket = Socket()
        override fun createSocket(host: String?, port: Int): Socket = Socket(host, port)
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            Socket(host, port, localHost, localPort)
        override fun createSocket(host: InetAddress?, port: Int): Socket = Socket(host, port)
        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            Socket(address, port, localAddress, localPort)
    },
)

open class NetworkCapabilities(private val transports: Set<Int> = emptySet()) {
    open fun hasTransport(transportType: Int): Boolean = transportType in transports

    companion object {
        const val TRANSPORT_CELLULAR = 0
        const val TRANSPORT_WIFI = 1
        const val TRANSPORT_BLUETOOTH = 2
        const val TRANSPORT_ETHERNET = 3
        const val TRANSPORT_VPN = 4
    }
}

open class LinkAddress(open val address: InetAddress)

open class RouteInfo(private val matcher: (InetAddress) -> Boolean = { true }) {
    open fun matches(destination: InetAddress): Boolean = matcher(destination)
}

open class LinkProperties {
    open val interfaceName: String? = null
    open val linkAddresses: List<LinkAddress> = emptyList()
    open val routes: List<RouteInfo> = emptyList()
}

open class ConnectivityManager {
    open val activeNetwork: Network? = null
    open val allNetworks: Array<Network> = emptyArray()
    open fun getNetworkCapabilities(network: Network): NetworkCapabilities? = null
    open fun getLinkProperties(network: Network): LinkProperties? = null
}
