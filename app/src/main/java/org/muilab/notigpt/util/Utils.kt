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
import org.muilab.notigpt.database.room.NotiDrawerDatabase
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_UNKNOWN
import org.muilab.notigpt.R
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
import kotlin.math.min

fun getViewedNotifications(context: Context): ArrayList<NotiUnit> = with(Dispatchers.IO) {
    val notiDrawerDatabase = NotiDrawerDatabase.getInstance(context)
    val drawerDao = notiDrawerDatabase.drawerDao()
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

fun cosineSimilarity(embeddingBaseSixtyFourQuery: String, embeddingBaseSixtyFourNotification: String): Double {
    val queryEmbedding = compressedBase64ToDoubleArray(embeddingBaseSixtyFourQuery)
    val notificationEmbedding = compressedBase64ToDoubleArray(embeddingBaseSixtyFourNotification)
    val baselineEnglishEmbedding = compressedBase64ToDoubleArray(SharedPreferencesManager.baselineEmbeddingEn)
    val baselineChineseEmbedding = compressedBase64ToDoubleArray(SharedPreferencesManager.baselineEmbeddingZhTW)

    // Compute Cosine Similarities
    val simQN = computeCosine(queryEmbedding, notificationEmbedding) // Query-Notification

    return simQN.coerceIn(0.0, 1.0)

    val simQBaseEn = computeCosine(queryEmbedding, baselineEnglishEmbedding) // Query-Baseline (EN)
    val simQBaseZh = computeCosine(queryEmbedding, baselineChineseEmbedding) // Query-Baseline (ZH)

    // Prevent trivial cases
    if (queryEmbedding.isEmpty() || notificationEmbedding.isEmpty()) return 0.0

    // Adjust similarity with relative scaling instead of direct subtraction
    val diffFromEnglish = (simQN - simQBaseEn) / (1 - simQBaseEn)
    val diffFromChinese = (simQN - simQBaseZh) / (1 - simQBaseZh)
    val contrastiveSimilarity = maxOf(diffFromEnglish, diffFromChinese)

    // Apply a diversity boost to push down notifications that are too generic
    val diversityFactor = 1 - min(simQBaseEn, simQBaseZh)
    val finalSimilarity = contrastiveSimilarity * diversityFactor

    // Weighted combination: Balance between direct similarity and contrastive adjustment
    val weightedSimilarity = (0.7 * simQN) + (0.3 * finalSimilarity)

    // Ensure scores remain within range (0 to 1) and prevent negatives
    return weightedSimilarity.coerceIn(0.0, 1.0)
}

// Helper function for cosine similarity calculation
fun computeCosine(vec1: DoubleArray, vec2: DoubleArray): Double {
    if (vec1.isEmpty() || vec2.isEmpty()) return 0.0
    val dotProduct = vec1.zip(vec2).sumOf { it.first * it.second }
    val norm1 = kotlin.math.sqrt(vec1.sumOf { it * it })
    val norm2 = kotlin.math.sqrt(vec2.sumOf { it * it })
    return if (norm1 == 0.0 || norm2 == 0.0) 0.0 else (dotProduct / (norm1 * norm2))
}

// App category mapping cache
private var appCategoryMap: Map<String, String>? = null

// Function to load app category mapping from XML
fun loadAppCategoryMapping(context: Context): Map<String, String> {
    if (appCategoryMap == null) {
        appCategoryMap = try {
            val resources = context.resources
            val xmlResourceParser = resources.getXml(R.xml.app_category_map)
            val mapping = mutableMapOf<String, String>()
            
            var eventType = xmlResourceParser.eventType
            while (eventType != android.content.res.XmlResourceParser.END_DOCUMENT) {
                if (eventType == android.content.res.XmlResourceParser.START_TAG && 
                    xmlResourceParser.name == "string") {
                    val appName = xmlResourceParser.getAttributeValue(null, "name")
                    xmlResourceParser.next()
                    if (xmlResourceParser.eventType == android.content.res.XmlResourceParser.TEXT) {
                        val category = xmlResourceParser.text
                        if (appName != null && category != null) {
                            mapping[appName] = category
                        }
                    }
                }
                eventType = xmlResourceParser.next()
            }
            xmlResourceParser.close()
            mapping.toMap()
        } catch (e: Exception) {
            Log.e("AppCategoryMapping", "Failed to load app category mapping", e)
            emptyMap()
        }
    }
    return appCategoryMap!!
}

// Function to get app category by app name
fun getAppCategoryByAppName(context: Context, appName: String): String {
    val mapping = loadAppCategoryMapping(context)
    return mapping[appName] ?: APP_CATEGORY_UNKNOWN
}