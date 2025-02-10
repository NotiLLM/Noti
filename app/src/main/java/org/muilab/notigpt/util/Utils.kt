package org.muilab.notigpt.util

import android.content.Context
import android.graphics.Bitmap
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.muilab.notigpt.database.room.DrawerDatabase
import org.muilab.notigpt.model.notifications.NotiUnit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.abs

fun getNotifications(context: Context): ArrayList<NotiUnit> = with(Dispatchers.IO) {
    val drawerDatabase = DrawerDatabase.getInstance(context)
    val drawerDao = drawerDatabase.drawerDao()
    return drawerDao.getAllVisible().toCollection(ArrayList())
}

fun getViewedNotifications(context: Context): ArrayList<NotiUnit> = with(Dispatchers.IO) {
    val drawerDatabase = DrawerDatabase.getInstance(context)
    val drawerDao = drawerDatabase.drawerDao()
    return drawerDao.getAllVisible().toCollection(ArrayList())
}

fun replaceChars(str: String): String {
    return str.replace("\n", " ").replace(",", " ")
}

fun hasTransparentPixels(bitmap: Bitmap, threshold: Float): Boolean {
    var bitCount: Int = 0
    for (x in 0 until bitmap.width) {
        for (y in 0 until bitmap.height) {
            val pixel = bitmap.getPixel(x, y)
            bitCount += if (pixel shr 24 == 0) 1 else 0
        }
    }
    val ratio = bitCount / bitmap.width / bitmap.height
    return minOf(ratio, 1 - ratio) > threshold
}


fun getRelativeTimeStr(unixTime: Long, locale: Locale = Locale("zh", "TW")): String {
    val now = System.currentTimeMillis()
    val diffInMillis = now - unixTime
    val formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))

    // Calculate differences in various units
    val diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(abs(diffInMillis))
    val diffInHours = TimeUnit.MILLISECONDS.toHours(abs(diffInMillis))
    val diffInDays = TimeUnit.MILLISECONDS.toDays(abs(diffInMillis))

    return when {
        diffInMillis < TimeUnit.MINUTES.toMillis(1) -> "現在"
        diffInMinutes < 60 -> formatter.format(diffInMinutes.toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.MINUTES).toString()
        diffInHours < 3 -> formatter.format(diffInHours.toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.HOURS).toString()
        diffInHours < 24 -> {
            val calNow = Calendar.getInstance()
            val calInput = Calendar.getInstance().apply { timeInMillis = unixTime}
            val dateFormat = if (calNow.get(Calendar.DATE) - calInput.get(Calendar.DATE) == 1) {
                SimpleDateFormat("'昨天' HH:mm", locale)
            } else {
                SimpleDateFormat("HH:mm", locale)
            }
            dateFormat.format(Date(unixTime))
        }
        diffInDays == 1L -> "昨天"
        diffInDays < 7 -> {
            val dayFormat = SimpleDateFormat("EEEE", locale)
            dayFormat.format(Date(unixTime))
        }
        else -> {
            val dateFormat = SimpleDateFormat("M'月' d'日'", Locale.getDefault())
            dateFormat.format(Date(unixTime))
        }
    }
}

fun getAbsoluteTimeStr(unixTime: Long, locale: Locale = Locale("zh", "TW")): String {
    val dateFormat = SimpleDateFormat("M'月' d'日' HH:mm", Locale.getDefault())
    return dateFormat.format(Date(unixTime))
}

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
        val byteStream = ByteArrayInputStream(compressedBytes)
        val gzipStream = GZIPInputStream(byteStream)
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

fun cosineSimilarity(embeddingBaseSixtyFour1: String, embeddingBaseSixtyFour2: String): Double {
    val vec1 = compressedBase64ToDoubleArray(embeddingBaseSixtyFour1)
    val vec2 = compressedBase64ToDoubleArray(embeddingBaseSixtyFour2)
    if (vec1.isEmpty() || vec2.isEmpty())
        return 0.0
    val dotProduct = vec1.zip(vec2).sumOf { it.first * it.second }
    val norm1 = kotlin.math.sqrt(vec1.sumOf { it * it })
    val norm2 = kotlin.math.sqrt(vec2.sumOf { it * it })
    return if (norm1 == 0.0 || norm2 == 0.0) 0.0 else (dotProduct / (norm1 * norm2))
}

fun resetSimilarity(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
        val drawerDatabase = DrawerDatabase.getInstance(context)
        val drawerDao = drawerDatabase.drawerDao()
        drawerDao.resetSimilarity()
    }
}