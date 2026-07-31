package com.darius.unison.model

data class DiscoveredRoom(
    val serviceName: String,
    val roomId: String,
    val roomName: String,
    val hostAddress: String,
    val port: Int,
    val protocolVersion: Int,
    val term: Long,
)
data class HotspotInfo(val ssid: String, val passphrase: String?, val securityType: Int)
