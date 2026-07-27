package com.darius.unison.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

class DiagnosticLog(context: Context) {
    private val file = File(context.filesDir, "diagnostics/unison.log").apply { parentFile?.mkdirs() }
    private val lock = Any()

    fun i(tag: String, message: String) = write("I", tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = write("W", tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = write("E", tag, message, throwable)

    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            "E" -> Log.e(tag, message, throwable)
            "W" -> Log.w(tag, message, throwable)
            else -> Log.i(tag, message)
        }
        val line = buildString {
            append(Instant.now()).append(' ').append(level).append('/').append(tag).append(' ')
            append(sanitize(message))
            throwable?.let {
                append(" :: ").append(it::class.simpleName)
                sanitize(it.message.orEmpty()).takeIf(String::isNotBlank)?.let { detail ->
                    append(": ").append(detail)
                }
            }
            append('\n')
        }
        synchronized(lock) {
            rotateIfNeeded()
            runCatching { file.appendText(line) }
        }
    }

    suspend fun read(): String = withContext(Dispatchers.IO) {
        synchronized(lock) { file.takeIf(File::exists)?.readText().orEmpty() }
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("(?i)(secret|token|passphrase|pin)=\\S+"), "$1=<redacted>")
        .replace(Regex("(?i)(password)(:|=)\\s*\\S+"), "$1$2 <redacted>")

    private fun rotateIfNeeded() {
        if (file.length() < 5 * 1024 * 1024) return
        val rotated = File(file.parentFile, "unison.log.1")
        rotated.delete()
        file.renameTo(rotated)
    }
}
