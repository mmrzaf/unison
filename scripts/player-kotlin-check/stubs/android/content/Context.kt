package android.content

open class Context {
    open val applicationContext: Context get() = this
    open fun <T> getSystemService(serviceClass: Class<T>): T? = null
}
