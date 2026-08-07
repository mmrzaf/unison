package com.darius.unison.util

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

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
    private val events = CopyOnWriteArrayList<Unit>()

    private fun record() {
        events += Unit
    }

    fun debug(
        component: String,
        category: DiagnosticCategory,
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = record()

    fun info(
        component: String,
        category: DiagnosticCategory,
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = record()

    fun warn(
        component: String,
        category: DiagnosticCategory,
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = record()

    fun error(
        component: String,
        category: DiagnosticCategory,
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = record()

    fun scoped(component: String, category: DiagnosticCategory): DiagnosticLogger =
        DiagnosticLogger(::record)

    fun beginRoom(roomId: String, role: String): String = "test-room-session"
    fun updateRoomRole(role: String) = Unit
    fun currentRoomSessionId(): String? = "test-room-session"
    fun endRoom(sessionId: String? = null) = Unit

    fun readRaw(): String = ""
    fun snapshot(sessionId: String? = null): List<Unit> = events.toList()

    val pendingEventCount: Int get() = 0
    val droppedEventCount: Long get() = 0L
    override fun close() = Unit
}

class DiagnosticLogger(private val record: () -> Unit = {}) {
    fun debug(
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = record()

    fun info(
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = record()

    fun warn(
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = record()

    fun error(
        eventName: String,
        body: String? = null,
        attributes: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) = record()
}
