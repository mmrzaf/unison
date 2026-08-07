package android.os

open class Looper { companion object { fun getMainLooper(): Looper = Looper() } }
open class Handler(looper: Looper)
object Build {
    object VERSION { var SDK_INT: Int = 33 }
    object VERSION_CODES { const val TIRAMISU = 33 }
}
