package android.os

open class Looper {
    companion object {
        private val main = Looper()
        fun getMainLooper(): Looper = main
        fun myLooper(): Looper? = main
    }
}

class Handler(val looper: Looper)

object SystemClock {
    fun elapsedRealtimeNanos(): Long = 0L
}

object Build {
    object VERSION { const val SDK_INT: Int = 35 }
    object VERSION_CODES {
        const val S: Int = 31
        const val TIRAMISU: Int = 33
    }
}
