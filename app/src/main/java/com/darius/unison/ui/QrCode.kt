package com.darius.unison.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object QrCode {
    private val generationMutex = Mutex()
    private val memory = object : LruCache<String, ImageBitmap>(CACHE_KIB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            ((value.width.toLong() * value.height.toLong() * 4L) / 1024L).coerceAtLeast(1L).toInt()
    }

    suspend fun createCached(text: String, size: Int = DEFAULT_SIZE): ImageBitmap = withContext(Dispatchers.Default) {
        val boundedSize = size.coerceIn(MIN_SIZE, MAX_SIZE)
        val key = "$boundedSize:$text"
        memory.get(key)?.let { return@withContext it }
        generationMutex.withLock {
            memory.get(key)?.let { return@withLock it }
            create(text, boundedSize).also { memory.put(key, it) }
        }
    }

    fun clearMemory() {
        memory.evictAll()
    }

    private fun create(text: String, size: Int): ImageBitmap {
        require(text.length <= MAX_TEXT_LENGTH) { "QR payload is too long" }
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) BLACK else WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private const val DEFAULT_SIZE = 640
    private const val MIN_SIZE = 192
    private const val MAX_SIZE = 768
    private const val MAX_TEXT_LENGTH = 2_048
    private const val CACHE_KIB = 2 * 1024
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}

@Composable
fun AsyncQrCode(
    text: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = text) {
        try {
            value = QrCode.createCached(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val current = bitmap
        if (current == null) {
            CircularProgressIndicator()
        } else {
            Image(current, "Room QR")
        }
    }
}
