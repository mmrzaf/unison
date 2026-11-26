package com.darius.unison.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.darius.unison.app.AndroidMonotonicClock
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.util.DiagnosticLog
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Real pinned-Media3 fixture shared by the instrumented playback tests. */
internal suspend fun withMedia3Adapter(block: suspend (Media3Fixture) -> Unit) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val log =
        DiagnosticLog(
            File(context.cacheDir, "media3-boundary-diagnostics.ndjson"),
            AndroidMonotonicClock,
        )
    val adapter =
        withContext(Dispatchers.Main.immediate) {
            Media3PlayerAdapter(context, scope, log).also { it.exoPlayer.volume = 0f }
        }
    val fixture = Media3Fixture(context, adapter)
    try {
        block(fixture)
    } finally {
        withContext(Dispatchers.Main.immediate) { adapter.close() }
        scope.cancel()
        log.close()
        fixture.files.forEach(File::delete)
    }
}

internal class Media3Fixture(
    private val context: Context,
    val adapter: Media3PlayerAdapter,
) {
    val files = mutableListOf<File>()
    private var trackOrdinal = 1

    fun item(name: String, durationMs: Long): LocalPlayableItem {
        val file = File(context.cacheDir, "unison-boundary-$name-${System.nanoTime()}.wav")
        writeSilentPcmWav(file, durationMs)
        files += file
        val digit = Integer.toHexString(trackOrdinal++).last()
        return LocalPlayableItem(
            queueItemId = QueueItemId("boundary-$name"),
            track =
                TrackDescriptor(
                    trackId = TrackId(digit.toString().repeat(64)),
                    sizeBytes = file.length(),
                    mimeType = "audio/wav",
                    durationMs = durationMs,
                    title = name,
                    originalFileName = file.name,
                ),
            file = file,
        )
    }

    suspend fun awaitBoundaryAfter(revision: Long): PlayerState =
        withTimeout(BOUNDARY_TIMEOUT_MS) {
            adapter.state.first { it.itemBoundaryRevision > revision }
        }
}

private const val SAMPLE_RATE_HZ = 8_000
private const val BITS_PER_SAMPLE = 16
private const val CHANNELS = 1
internal const val BOUNDARY_TIMEOUT_MS = 12_000L

internal fun writeSilentPcmWav(file: File, durationMs: Long) {
    val sampleCount = ((SAMPLE_RATE_HZ.toLong() * durationMs) / 1_000L).toInt()
    val bytesPerSample = BITS_PER_SAMPLE / 8
    val dataSize = sampleCount * CHANNELS * bytesPerSample
    val byteRate = SAMPLE_RATE_HZ * CHANNELS * bytesPerSample
    val blockAlign = CHANNELS * bytesPerSample

    FileOutputStream(file).use { out ->
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.writeLittleEndianInt(36 + dataSize)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.writeLittleEndianInt(16)
        out.writeLittleEndianShort(1) // PCM
        out.writeLittleEndianShort(CHANNELS)
        out.writeLittleEndianInt(SAMPLE_RATE_HZ)
        out.writeLittleEndianInt(byteRate)
        out.writeLittleEndianShort(blockAlign)
        out.writeLittleEndianShort(BITS_PER_SAMPLE)
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.writeLittleEndianInt(dataSize)
        repeat(dataSize) { out.write(0) }
    }
}

private fun FileOutputStream.writeLittleEndianInt(value: Int) {
    write(value and 0xff)
    write((value ushr 8) and 0xff)
    write((value ushr 16) and 0xff)
    write((value ushr 24) and 0xff)
}

private fun FileOutputStream.writeLittleEndianShort(value: Int) {
    write(value and 0xff)
    write((value ushr 8) and 0xff)
}
