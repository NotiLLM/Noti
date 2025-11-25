package org.muilab.notigpt.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SharedPreferencesManager {

    private val KEY_LOCAL_PREFS = "local"
    private val KEY_SERVER_PREFS = "server"
    private lateinit var localSharedPrefs: SharedPreferences
    private lateinit var serverSharedPrefs: SharedPreferences

    fun init(context: Context) {
        localSharedPrefs = context.getSharedPreferences("local", Context.MODE_PRIVATE)
        serverSharedPrefs = context.getSharedPreferences("server", Context.MODE_PRIVATE)
    }

    // Generic Setter
    fun <T> put(prefName: String, key: String, value: T) {
        val sharedPrefs = when (prefName) {
            "local" -> localSharedPrefs
            "server" -> serverSharedPrefs
            else -> localSharedPrefs
        }
        with(sharedPrefs.edit()) {
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                is Long -> putLong(key, value)
                else -> throw IllegalArgumentException("Unsupported Type")
            }
            apply()
        }
    }

    // Generic Getter
    fun <T> get(prefName: String, key: String, defaultValue: T): T {
        val sharedPrefs = when (prefName) {
            "local" -> localSharedPrefs
            "server" -> serverSharedPrefs
            else -> localSharedPrefs
        }
        return when (defaultValue) {
            is String -> sharedPrefs.getString(key, defaultValue) as T
            is Int -> sharedPrefs.getInt(key, defaultValue) as T
            is Boolean -> sharedPrefs.getBoolean(key, defaultValue) as T
            is Float -> sharedPrefs.getFloat(key, defaultValue) as T
            is Long -> sharedPrefs.getLong(key, defaultValue) as T
            else -> throw IllegalArgumentException("Unsupported Type")
        }
    }

    private const val KEY_USER_ID = "userId"
    var userId: String
        get() = get(KEY_SERVER_PREFS, KEY_USER_ID, "").toString()
        set(value) = put(KEY_SERVER_PREFS, KEY_USER_ID, value)

    const val KEY_SERVER_URL = "SERVER_URL"
    var serverIP: String
        get() = get(KEY_SERVER_PREFS, KEY_SERVER_URL, "dify.udchen.tw").toString()
        set(value) = put(KEY_SERVER_PREFS, KEY_SERVER_URL, value)

    const val KEY_AUTO_ARCHIVE = "autoArchive"
    var autoArchive: Boolean
        get() = get(KEY_LOCAL_PREFS, KEY_AUTO_ARCHIVE, false)
        set(value) = put(KEY_LOCAL_PREFS, KEY_AUTO_ARCHIVE, value)

    const val KEY_AUTO_DELETE = "autoDelete"
    var autoDelete: Boolean
        get() = get(KEY_LOCAL_PREFS, KEY_AUTO_DELETE, false)
        set(value) = put(KEY_LOCAL_PREFS, KEY_AUTO_DELETE, value)

    const val KEY_TRACK_PIN = "trackPin"
    var trackPin: Boolean
        get() = get(KEY_LOCAL_PREFS, KEY_TRACK_PIN, false)
        set(value) = put(KEY_LOCAL_PREFS, KEY_TRACK_PIN, value)

    const val KEY_WAIT_SECONDS_BEFORE_NOTI_UNIT_SYNC = "waitSecondsBeforeNotiUnitSync"
    var waitSecondsBeforeNotiUnitSync: Int
        get() = get(KEY_LOCAL_PREFS, KEY_WAIT_SECONDS_BEFORE_NOTI_UNIT_SYNC, 5 * 60)
        set(value) = put(KEY_LOCAL_PREFS, KEY_WAIT_SECONDS_BEFORE_NOTI_UNIT_SYNC, value)

    const val KEY_MAX_RECORDS_BEFORE_NOTI_SYNC = "maxRecordsBeforeNotiSync"
    var maxRecordsBeforeNotiSync: Int
        get() = get(KEY_LOCAL_PREFS, KEY_MAX_RECORDS_BEFORE_NOTI_SYNC, 10)
        set(value) = put(KEY_LOCAL_PREFS, KEY_MAX_RECORDS_BEFORE_NOTI_SYNC, value)

    const val KEY_WAIT_SECONDS_BEFORE_DRAWER_SYNC = "waitSecondsBeforeDrawerSync"
    var waitSecondsBeforeDrawerSync: Int
        get() = get(KEY_LOCAL_PREFS, KEY_WAIT_SECONDS_BEFORE_DRAWER_SYNC, 30 * 60)
        set(value) = put(KEY_LOCAL_PREFS, KEY_WAIT_SECONDS_BEFORE_DRAWER_SYNC, value)

    const val KEY_MAX_RECORDS_BEFORE_DRAWER_SYNC = "maxRecordsBeforeDrawerSync"
    var maxRecordsBeforeDrawerSync: Int
        get() = get(KEY_LOCAL_PREFS, KEY_MAX_RECORDS_BEFORE_DRAWER_SYNC, 5)
        set(value) = put(KEY_LOCAL_PREFS, KEY_MAX_RECORDS_BEFORE_DRAWER_SYNC, value)

    private val _hideComplexVisualsFlow = MutableStateFlow(true)
    val hideComplexVisualsFlow: StateFlow<Boolean> = _hideComplexVisualsFlow.asStateFlow()

    private const val KEY_BASELINE_EMBEDDING_EN = "baselineEmbeddingEn"
    var baselineEmbeddingEn: String
        get() = get(KEY_SERVER_PREFS, KEY_BASELINE_EMBEDDING_EN, "")
        set(value) = put(KEY_SERVER_PREFS, KEY_BASELINE_EMBEDDING_EN, value)

    private const val KEY_BASELINE_EMBEDDING_ZHTW = "baselineEmbeddingZhTW"
    var baselineEmbeddingZhTW: String
        get() = get(KEY_SERVER_PREFS, KEY_BASELINE_EMBEDDING_ZHTW, "")
        set(value) = put(KEY_SERVER_PREFS, KEY_BASELINE_EMBEDDING_ZHTW, value)

    const val KEY_HISTORY_NOTI_COUNT_THRESHOLD = "historyNotiCountThreshold"
    var historyNotiCountThreshold: Int
        get() = get(KEY_LOCAL_PREFS, KEY_HISTORY_NOTI_COUNT_THRESHOLD, -1)
        set(value) = put(KEY_LOCAL_PREFS, KEY_HISTORY_NOTI_COUNT_THRESHOLD, value)

    const val KEY_HISTORY_NOTI_HOURS_THRESHOLD = "historyNotiHoursThreshold"
    var historyNotiHoursThreshold: Int
        get() = get(KEY_LOCAL_PREFS, KEY_HISTORY_NOTI_HOURS_THRESHOLD, -1)
        set(value) = put(KEY_LOCAL_PREFS, KEY_HISTORY_NOTI_HOURS_THRESHOLD, value)

    const val KEY_LAST_APP_RESUME_TIME = "lastAppResumeTime"
    var lastAppResumeTime: Long
        get() = get(KEY_LOCAL_PREFS, KEY_LAST_APP_RESUME_TIME, 0L)
        set(value) = put(KEY_LOCAL_PREFS, KEY_LAST_APP_RESUME_TIME, value)

    const val KEY_MAX_PAST_CONTEXT = "maxPastContext"
    var maxPastContext: Int
        get() = get(KEY_LOCAL_PREFS, KEY_MAX_PAST_CONTEXT, 0)
        set(value) = put(KEY_LOCAL_PREFS, KEY_MAX_PAST_CONTEXT, value)

    fun clearAll() {
        localSharedPrefs.edit().clear().apply()
        serverSharedPrefs.edit().clear().apply()
    }
}
