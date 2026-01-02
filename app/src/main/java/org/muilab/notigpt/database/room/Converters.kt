package org.muilab.notigpt.database.room

import androidx.room.TypeConverter
import org.json.JSONArray

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

