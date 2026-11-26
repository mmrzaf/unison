package android.os

open class Looper { companion object { fun getMainLooper(): Looper = Looper() } }
open class Handler(looper: Looper)
object SystemClock { fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000L }
object Build {
    object VERSION { var SDK_INT: Int = 33 }
    object VERSION_CODES { const val TIRAMISU = 33 }
}
