package android.system

/** Minimal stand-in for android.system.ErrnoException used by pre-connect route failure classification. */
class ErrnoException : Exception {
    @JvmField val errno: Int

    constructor(functionName: String, errno: Int) : super("$functionName failed: errno $errno") {
        this.errno = errno
    }

    constructor(
        functionName: String,
        errno: Int,
        cause: Throwable?,
    ) : super("$functionName failed: errno $errno", cause) {
        this.errno = errno
    }
}

/** Minimal stand-in for android.system.OsConstants errno values referenced by the router. */
object OsConstants {
    const val EPERM: Int = 1
    const val EACCES: Int = 13
}
