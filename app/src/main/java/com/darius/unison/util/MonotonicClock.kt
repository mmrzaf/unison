package com.darius.unison.util

import android.os.SystemClock

fun interface MonotonicClock {
    fun nowNs(): Long
}

object AndroidMonotonicClock : MonotonicClock {
    override fun nowNs(): Long = SystemClock.elapsedRealtimeNanos()
}
