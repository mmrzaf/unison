package com.darius.unison.util

import java.io.File

enum class DiagnosticCategory {
    APP,
    ROOM,
    NETWORK,
    DISCOVERY,
    PLAYBACK,
    SYNC,
    TRANSFER,
    STORAGE,
    SECURITY,
}

class DiagnosticLog(private val file: File? = null) : AutoCloseable {
    fun debug(
        component: String,
        category: DiagnosticCategory,
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = Unit

    fun info(
        component: String,
        category: DiagnosticCategory,
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = Unit

    fun warn(
        component: String,
        category: DiagnosticCategory,
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = Unit

    fun error(
        component: String,
        category: DiagnosticCategory,
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = Unit

    fun scoped(component: String, category: DiagnosticCategory): DiagnosticLogger = DiagnosticLogger()

    fun beginRoom(roomId: String, role: String): String = "test-room-session"
    fun updateRoomRole(role: String) = Unit
    fun currentRoomSessionId(): String? = "test-room-session"
    fun endRoom(sessionId: String? = null) = Unit

    val pendingEventCount: Int get() = 0
    val droppedEventCount: Long get() = 0L
    override fun close() = Unit
}

class DiagnosticLogger {
    fun debug(eventName: String, body: String? = null, attributes: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) = Unit
    fun info(eventName: String, body: String? = null, attributes: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) = Unit
    fun warn(eventName: String, body: String? = null, attributes: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) = Unit
    fun error(eventName: String, body: String? = null, attributes: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) = Unit
}
