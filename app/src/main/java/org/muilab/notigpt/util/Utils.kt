package org.muilab.notigpt.util

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.domain.math.compressedBase64ToDoubleArray as decodeEmbedding
import org.muilab.notigpt.domain.math.computeCosine as computeCosineDomain
import org.muilab.notigpt.domain.math.cosineSimilarity as cosineSimilarityDomain
import org.muilab.notigpt.domain.math.doubleArrayToCompressedBase64 as encodeEmbedding
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.util.app.getAppCategoryByAppName as getAppCategoryByAppNameImpl
import org.muilab.notigpt.util.app.loadAppCategoryMapping as loadAppCategoryMappingImpl
import org.muilab.notigpt.util.time.getAbsoluteTimeStr as getAbsoluteTimeStrImpl
import org.muilab.notigpt.util.time.getRelativeTimeStr as getRelativeTimeStrImpl

fun getViewedNotifications(context: Context): ArrayList<NotiUnit> = with(Dispatchers.IO) {
    val appDatabase = AppDatabase.getInstance(context)
    val drawerDao = appDatabase.drawerDao()
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

fun getRelativeTimeStr(unixTime: Long, locale: java.util.Locale = java.util.Locale("zh", "TW")): String =
    getRelativeTimeStrImpl(unixTime, locale)

fun getAbsoluteTimeStr(unixTime: Long, locale: java.util.Locale = java.util.Locale("zh", "TW")): String =
    getAbsoluteTimeStrImpl(unixTime, locale)

fun doubleArrayToCompressedBase64(doubleArray: DoubleArray): String = encodeEmbedding(doubleArray)

fun compressedBase64ToDoubleArray(base64String: String): DoubleArray = decodeEmbedding(base64String)

fun cosineSimilarity(embeddingBaseSixtyFourQuery: String, embeddingBaseSixtyFourNotification: String): Double =
    cosineSimilarityDomain(embeddingBaseSixtyFourQuery, embeddingBaseSixtyFourNotification)

// Helper function for cosine similarity calculation
fun computeCosine(vec1: DoubleArray, vec2: DoubleArray): Double = computeCosineDomain(vec1, vec2)

fun loadAppCategoryMapping(context: Context): Map<String, String> = loadAppCategoryMappingImpl(context)

fun getAppCategoryByAppName(context: Context, appName: String): String = getAppCategoryByAppNameImpl(context, appName)
