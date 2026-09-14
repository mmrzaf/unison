package com.darius.unison.library

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.darius.unison.BuildConfig
import java.io.File

/** Test-only provider that exercises the same ContentResolver paths used by real picker imports. */
class TestAudioContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "audio/mpeg"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns =
            projection?.let { it.toList() }
                ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns.toTypedArray())
        val row = cursor.newRow()
        val scenario = scenario(uri)
        columns.forEach { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> row.add("$scenario.mp3")
                OpenableColumns.SIZE -> row.add(declaredSize(scenario))
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r") { "Test provider is read-only" }
        val scenario = scenario(uri)
        val file = File(requireNotNull(context).cacheDir, "unison-test-provider-$scenario.mp3")
        file.writeBytes(payload(scenario))
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Test provider is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Test provider is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Test provider is read-only")

    private fun scenario(uri: Uri): String =
        requireNotNull(uri.lastPathSegment) { "Missing import test scenario" }

    private fun declaredSize(scenario: String): Long? =
        when (scenario) {
            "unknown-size" -> null
            "underreported" -> 1L
            else -> payload(scenario).size.toLong()
        }

    companion object {
        val AUTHORITY = "${BuildConfig.APPLICATION_ID}.test.audio"
        const val PAYLOAD_SIZE = 8 * 1024

        fun uri(scenario: String): Uri = Uri.parse("content://$AUTHORITY/$scenario")

        fun payload(scenario: String): ByteArray =
            ByteArray(PAYLOAD_SIZE) { index -> ((index * 31 + scenario.length) and 0xff).toByte() }
    }
}
