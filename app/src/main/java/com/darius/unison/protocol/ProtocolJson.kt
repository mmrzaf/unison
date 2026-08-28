package com.darius.unison.protocol

import kotlinx.serialization.json.Json

/** Strict protocol codec. Protocol 2 has no fallback fields or unknown-key compatibility path. */
val ProtocolJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = false
    explicitNulls = true
    isLenient = false
    coerceInputValues = false
    allowStructuredMapKeys = false
}
