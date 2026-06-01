package org.muilab.notigpt.database.room

import androidx.room.TypeConverter
import org.json.JSONArray

/**
 * Room type converters for compact value types stored inside local tables.
 *
 * Keep converters deterministic and format-stable. If a value needs querying or partial updates,
 * prefer a table/column model over adding more JSON-like conversion here.
 */
object Converters {
    @TypeConverter
    @JvmStatic
    fun fromStringList(list: Set<String>?): String {
        if (list == null) return "[]"
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    @TypeConverter
    @JvmStatic
    fun toStringList(value: String?): Set<String> {
        val result = mutableSetOf<String>()
        if (value.isNullOrEmpty()) return result
        try {
            val arr = JSONArray(value)
            for (i in 0 until arr.length()) {
                result.add(arr.optString(i))
            }
        } catch (_: Exception) {
        }
        return result
    }
}

