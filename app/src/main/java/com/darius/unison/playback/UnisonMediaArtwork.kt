package com.darius.unison.playback

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream

/** Fixed application artwork exposed only to Android's system media controls. */
internal object UnisonMediaArtwork {
    private const val SIZE_PX = 256
    private const val BAR_RADIUS_PX = 8f

    fun createPng(): ByteArray {
        val bitmap = createBitmap(SIZE_PX, SIZE_PX)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(24, 26, 32))

            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
            BARS.forEach { bar ->
                canvas.drawRoundRect(
                    bar.left,
                    bar.top,
                    bar.right,
                    bar.bottom,
                    BAR_RADIUS_PX,
                    BAR_RADIUS_PX,
                    paint,
                )
            }

            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                    "Unable to encode system media artwork"
                }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private data class Bar(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    private val BARS =
        listOf(
            Bar(20f, 110f, 48f, 146f),
            Bar(67f, 80f, 95f, 176f),
            Bar(114f, 53f, 142f, 203f),
            Bar(161f, 87f, 189f, 169f),
            Bar(208f, 110f, 236f, 146f),
        )
}
