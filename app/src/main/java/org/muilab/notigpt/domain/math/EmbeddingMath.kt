package org.muilab.notigpt.domain.math

import android.util.Base64
import android.util.Log
import org.muilab.notigpt.util.SharedPreferencesManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.min

fun doubleArrayToCompressedBase64(doubleArray: DoubleArray): String {
    val byteStream = ByteArrayOutputStream()
    GZIPOutputStream(byteStream).use { gzipStream ->
        DataOutputStream(gzipStream).use { dataStream ->
            doubleArray.forEach { dataStream.writeDouble(it) }
            dataStream.flush()
        }
        gzipStream.flush()
    }
    val compressedBytes = byteStream.toByteArray()
    return Base64.encodeToString(compressedBytes, Base64.DEFAULT)
}

fun compressedBase64ToDoubleArray(base64String: String): DoubleArray {
    return try {
        val compressedBytes = Base64.decode(base64String, Base64.DEFAULT)
        val gzipStream = GZIPInputStream(ByteArrayInputStream(compressedBytes))
        val dataStream = DataInputStream(gzipStream)

        val doubleList = mutableListOf<Double>()
        while (dataStream.available() > 0) {
            doubleList.add(dataStream.readDouble())
        }

        doubleList.toDoubleArray()
    } catch (e: EOFException) {
        Log.d("Query", e.stackTraceToString())
        emptyArray<Double>().toDoubleArray()
    } catch (e: Exception) {
        throw RuntimeException("Decompression failed: ${e.message}")
    }
}

/**
 * Current behavior matches the (actually executed) logic in the original util:
 * returns Query-Notification cosine similarity clamped to [0,1].
 *
 * Note: There is additional (currently-dead) contrastive logic in the old file; we preserve
 * runtime behavior and keep this function small and predictable.
 */
fun cosineSimilarity(embeddingBaseSixtyFourQuery: String, embeddingBaseSixtyFourNotification: String): Double {
    val queryEmbedding = compressedBase64ToDoubleArray(embeddingBaseSixtyFourQuery)
    val notificationEmbedding = compressedBase64ToDoubleArray(embeddingBaseSixtyFourNotification)

    val simQN = computeCosine(queryEmbedding, notificationEmbedding)
    return simQN.coerceIn(0.0, 1.0)
}

fun computeCosine(vec1: DoubleArray, vec2: DoubleArray): Double {
    if (vec1.isEmpty() || vec2.isEmpty()) return 0.0
    val dotProduct = vec1.zip(vec2).sumOf { it.first * it.second }
    val norm1 = kotlin.math.sqrt(vec1.sumOf { it * it })
    val norm2 = kotlin.math.sqrt(vec2.sumOf { it * it })
    return if (norm1 == 0.0 || norm2 == 0.0) 0.0 else (dotProduct / (norm1 * norm2))
}

