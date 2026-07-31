package com.darius.unison.util

class DiagnosticLog {
    fun i(tag: String, message: String) = Unit
    fun e(tag: String, message: String, throwable: Throwable? = null) = Unit
}
