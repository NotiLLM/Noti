package org.muilab.notigpt.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore

/**
 * Platform boundary for writing exported notification logs into Android Documents.
 *
 * Keep MediaStore details behind the NotiLogExporter interface so DataExportManager only decides filenames and
 * content chunks, not storage-provider mechanics.
 */
interface NotiLogExporter {
    /**
     * Writes [content] to a text file in Documents. Best-effort.
     */
    fun exportToDocuments(filename: String, content: String)
}

class MediaStoreNotiLogExporter(private val context: Context) : NotiLogExporter {
    override fun exportToDocuments(filename: String, content: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            }
        }
    }
}
