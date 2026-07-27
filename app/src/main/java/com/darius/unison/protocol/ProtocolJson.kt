package com.darius.unison.protocol

import kotlinx.serialization.json.Json

val ProtocolJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = false
}
