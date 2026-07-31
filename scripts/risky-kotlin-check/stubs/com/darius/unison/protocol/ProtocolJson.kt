package com.darius.unison.protocol

/** Compile-only substitute for kotlinx.serialization Json in the SDK-free Kotlin gate. */
object ProtocolJson {
    fun <T> encodeToString(value: T): String = value.toString()
    inline fun <reified T> decodeFromString(value: String): T = error("compile-only")
}
