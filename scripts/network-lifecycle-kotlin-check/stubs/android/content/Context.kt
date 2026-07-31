package android.content

import java.util.concurrent.Executor

open class Context {
    open val applicationContext: Context get() = this
    open val mainExecutor: Executor get() = Executor { it.run() }
    open fun <T> getSystemService(clazz: Class<T>): T = error("compile-only")
}
