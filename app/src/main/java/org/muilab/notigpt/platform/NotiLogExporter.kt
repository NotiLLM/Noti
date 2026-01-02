package org.muilab.notigpt.platform

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore

/**
 * Persists exported NotiGPT logs.
 *
 * Kept as a separate interface so ViewModels stay testable and avoid MediaStore APIs.
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

