package org.muilab.notigpt.platform

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages exporting large notification data by writing incrementally to one or more files.
 *
 * The previous implementation materialised the entire [JSONArray] in RAM before deciding how
 * to split it, which caused OOM with large datasets (full context + dismissed notifications).
 * This version consumes a lazy [Sequence] so each notification's JSON object is processed and
 * can be GC'd before the next one is loaded — at most one 5 MB chunk is held in memory at once.
 */
@Suppress("MemberVisibilityCanBePrivate")
class DataExportManager(context: Context) {

    private val logExporter = MediaStoreNotiLogExporter(context)

    companion object {
        // Maximum bytes per output file (5 MB)
        const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024
    }

    /**
     * Iterates [items] one [JSONObject] at a time.  Whenever the running chunk would exceed
     * [MAX_FILE_SIZE_BYTES] it is flushed to disk and a fresh chunk starts.
     *
     * Naming rules:
     * - Single file  → `<baseFilename>.txt`
     * - Multiple files → `<baseFilename>_part_1.txt`, `_part_2.txt`, …
     *
     * Returns the list of file names that were created, or an empty list if [items] was empty.
     */
    fun exportNotificationData(
        items: Sequence<JSONObject>,
        filename: String,
    ): List<String> {
        val baseFilename = filename.removeSuffix(".txt")

        val createdFiles = mutableListOf<String>()
        var flushedPartCount = 0   // number of part files already written to disk
        var currentChunk = JSONArray()
        var currentChunkSize = 0

        for (item in items) {
            val itemStr = item.toString()
            val itemSize = itemStr.toByteArray().size

            // If adding this item would overflow the current chunk (and the chunk is non-empty),
            // flush first.  A single oversized item is still written on its own to avoid losing data.
            if (currentChunkSize + itemSize > MAX_FILE_SIZE_BYTES && currentChunk.length() > 0) {
                flushedPartCount++
                logExporter.exportToDocuments(
                    "${baseFilename}_part_${flushedPartCount}.txt",
                    currentChunk.toString(2),
                )
                createdFiles.add("${baseFilename}_part_${flushedPartCount}.txt")
                currentChunk = JSONArray()
                currentChunkSize = 0
            }

            currentChunk.put(item)
            currentChunkSize += itemSize
        }

        // Flush the remaining (or only) chunk.
        if (currentChunk.length() > 0) {
            val finalFilename = if (flushedPartCount == 0) {
                // Only one chunk was ever produced → clean name, no part suffix.
                "$baseFilename.txt"
            } else {
                flushedPartCount++
                "${baseFilename}_part_${flushedPartCount}.txt"
            }
            logExporter.exportToDocuments(finalFilename, currentChunk.toString(2))
            createdFiles.add(finalFilename)
        }

        return createdFiles
    }
}


