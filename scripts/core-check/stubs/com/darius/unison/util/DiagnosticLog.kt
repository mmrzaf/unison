package com.darius.unison.util

class DiagnosticLog {
    fun i(tag: String, message: String) = Unit
    fun w(tag: String, message: String, throwable: Throwable? = null) = Unit
    fun e(tag: String, message: String, throwable: Throwable? = null) = Unit
}
