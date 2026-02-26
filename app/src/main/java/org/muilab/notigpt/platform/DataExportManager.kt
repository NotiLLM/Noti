package org.muilab.notigpt.platform

import android.content.Context
import org.json.JSONArray

/**
 * Manages exporting large notification data by splitting into multiple files if needed.
 * Handles splitting based on file size limits to ensure files are saveable.
 */
@Suppress("MemberVisibilityCanBePrivate")
class DataExportManager(context: Context) {

    private val logExporter = MediaStoreNotiLogExporter(context)

    companion object {
        // Maximum file size in bytes (5MB to be safe for various devices)
        private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024

        // Average bytes per notification (rough estimate for splitting logic)
        private const val ESTIMATED_BYTES_PER_NOTI = 2048
    }

    /**
     * Export notification data, splitting into multiple files if necessary.
     * Returns the list of file names that were created.
     */
    fun exportNotificationData(
        allData: JSONArray,
        filename: String,
    ): List<String> {
        if (allData.length() == 0) {
            return emptyList()
        }

        val baseFilename = if (filename.endsWith(".txt")) {
            filename.removeSuffix(".txt")
        } else {
            filename
        }

        val jsonString = allData.toString(2)
        val jsonBytes = jsonString.toByteArray()

        return if (jsonBytes.size <= MAX_FILE_SIZE_BYTES) {
            // Single file is fine
            val finalFilename = "$baseFilename.txt"
            logExporter.exportToDocuments(finalFilename, jsonString)
            listOf(finalFilename)
        } else {
            // Need to split - divide by approximate item size
            splitAndExport(allData, baseFilename)
        }
    }

    /**
     * Splits large notification array into multiple files and exports them.
     * Strategy: split JSON array into chunks, each exported separately.
     */
    private fun splitAndExport(
        allData: JSONArray,
        baseFilename: String,
    ): List<String> {
        val totalItems = allData.length()

        val chunks = mutableListOf<List<Int>>()
        var currentChunk = mutableListOf<Int>()
        var currentChunkSize = 0

        for (i in 0 until totalItems) {
            val itemJsonStr = allData.getJSONObject(i).toString()
            val itemSize = itemJsonStr.toByteArray().size

            // If adding this item would exceed limit, start a new chunk
            // (unless chunk is empty - single item might be large but we still need to include it)
            if (currentChunkSize + itemSize > MAX_FILE_SIZE_BYTES && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toList())
                currentChunk = mutableListOf()
                currentChunkSize = 0
            }

            currentChunk.add(i)
            currentChunkSize += itemSize
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toList())
        }

        // Export each chunk to a separate file
        val createdFiles = mutableListOf<String>()
        chunks.forEachIndexed { chunkIndex, indices ->
            val chunkArray = JSONArray()
            indices.forEach { idx ->
                chunkArray.put(allData.getJSONObject(idx))
            }

            val filename = if (chunks.size > 1) {
                "${baseFilename}_part_${chunkIndex + 1}_of_${chunks.size}.txt"
            } else {
                "$baseFilename.txt"
            }

            logExporter.exportToDocuments(filename, chunkArray.toString(2))
            createdFiles.add(filename)
        }

        return createdFiles
    }
}


