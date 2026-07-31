package com.darius.unison.util

import java.io.File

class DiagnosticLog(private val file: File? = null) : AutoCloseable {
    fun i(tag: String, message: String) = Unit
    fun w(tag: String, message: String, throwable: Throwable? = null) = Unit
    fun e(tag: String, message: String, throwable: Throwable? = null) = Unit
    override fun close() = Unit
}
