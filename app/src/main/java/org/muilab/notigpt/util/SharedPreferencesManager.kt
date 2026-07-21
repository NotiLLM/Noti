package org.muilab.notigpt.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray

/**
 * SharedPreferences wrapper for app configuration and lightweight persisted flags.
 *
 * Initialize once with application context before use. Prefer Room or repositories for structured feature data;
 * this manager is for small key-value settings.
 */
object SharedPreferencesManager {

    private const val KEY_LOCAL_PREFS = "local"
    private const val KEY_SERVER_PREFS = "server"
    private lateinit var localSharedPrefs: SharedPreferences
    private lateinit var serverSharedPrefs: SharedPreferences

    /** Stores application context for subsequent typed preference reads and writes. */
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
        sharedPrefs.edit {
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                is Long -> putLong(key, value)
                else -> throw IllegalArgumentException("Unsupported Type")
            }
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
        get() = get(KEY_SERVER_PREFS, KEY_USER_ID, "")
        set(value) = put(KEY_SERVER_PREFS, KEY_USER_ID, value)

    const val KEY_WAIT_SECONDS_BEFORE_NOTI_UNIT_SYNC = "waitSecondsBeforeNotiUnitSync"
    var waitSecondsBeforeNotiUnitSync: Int
        get() = get(KEY_LOCAL_PREFS, KEY_WAIT_SECONDS_BEFORE_NOTI_UNIT_SYNC, 3 * 60)
        set(value) = put(KEY_LOCAL_PREFS, KEY_WAIT_SECONDS_BEFORE_NOTI_UNIT_SYNC, value)

    const val KEY_MAX_RECORDS_BEFORE_NOTI_SYNC = "maxRecordsBeforeNotiSync"
    var maxRecordsBeforeNotiSync: Int
        get() = get(KEY_LOCAL_PREFS, KEY_MAX_RECORDS_BEFORE_NOTI_SYNC, 10)
        set(value) = put(KEY_LOCAL_PREFS, KEY_MAX_RECORDS_BEFORE_NOTI_SYNC, value)

    // --- Swipe Delete Direction ---
    const val KEY_SWIPE_DELETE_LEFT = "swipeDeleteLeft"
    var swipeDeleteLeft: Boolean
        get() = get(KEY_LOCAL_PREFS, KEY_SWIPE_DELETE_LEFT, true)
        set(value) = put(KEY_LOCAL_PREFS, KEY_SWIPE_DELETE_LEFT, value)

    // --- Dynamic color (Material You) opt-in; retints neutral chrome only ---
    const val KEY_USE_DYNAMIC_COLOR = "useDynamicColor"
    var useDynamicColor: Boolean
        get() = get(KEY_LOCAL_PREFS, KEY_USE_DYNAMIC_COLOR, false)
        set(value) = put(KEY_LOCAL_PREFS, KEY_USE_DYNAMIC_COLOR, value)

    // --- Skip the "Clear all" confirmation dialog once the user opts out of seeing it ---
    const val KEY_SKIP_CLEAR_ALL_CONFIRM = "skipClearAllConfirm"
    var skipClearAllConfirm: Boolean
        get() = get(KEY_LOCAL_PREFS, KEY_SKIP_CLEAR_ALL_CONFIRM, false)
        set(value) = put(KEY_LOCAL_PREFS, KEY_SKIP_CLEAR_ALL_CONFIRM, value)

    // --- Target extraction language ---
    const val KEY_TARGET_EXTRACTION_LANGUAGE = "targetExtractionLanguage"
    var targetExtractionLanguage: String
        get() = get(KEY_LOCAL_PREFS, KEY_TARGET_EXTRACTION_LANGUAGE, "original")
        set(value) = put(KEY_LOCAL_PREFS, KEY_TARGET_EXTRACTION_LANGUAGE, value)

    const val KEY_LAST_APP_RESUME_TIME = "lastAppResumeTime"
    var lastAppResumeTime: Long
        get() = get(KEY_LOCAL_PREFS, KEY_LAST_APP_RESUME_TIME, 0L)
        set(value) = put(KEY_LOCAL_PREFS, KEY_LAST_APP_RESUME_TIME, value)

    // --- Periodic extraction worker diagnostics ---
    const val KEY_LAST_EXTRACTION_PERIODIC_RUN_TIME = "lastExtractionPeriodicRunTime"
    private const val LEGACY_KEY_LAST_REMINDER_PERIODIC_RUN_TIME = "lastReminderPeriodicRunTime"
    var lastExtractionPeriodicRunTime: Long
        get() = get(KEY_LOCAL_PREFS, KEY_LAST_EXTRACTION_PERIODIC_RUN_TIME, 0L).takeIf { it > 0L }
            ?: get(KEY_LOCAL_PREFS, LEGACY_KEY_LAST_REMINDER_PERIODIC_RUN_TIME, 0L)
        set(value) = put(KEY_LOCAL_PREFS, KEY_LAST_EXTRACTION_PERIODIC_RUN_TIME, value)

    private const val LEGACY_KEY_LAST_REFLECTION_RUN_TIME = "lastReflectionRunTime"
    const val KEY_LAST_REFLECTION_ATTEMPT_TIME = "lastReflectionAttemptTime"
    const val KEY_LAST_REFLECTION_SUCCESS_TIME = "lastReflectionSuccessTime"
    private const val KEY_REFLECTION_DIRTY_ITEM_IDS = "reflectionDirtyItemIds"
    private const val KEY_REFLECTION_DIRTY_VERSION = "reflectionDirtyVersion"

    /** Last time D2 was enqueued. Used to debounce item-count-triggered runs. */
    var lastReflectionAttemptTime: Long
        get() = get(KEY_LOCAL_PREFS, KEY_LAST_REFLECTION_ATTEMPT_TIME, 0L).takeIf { it > 0L }
            ?: get(KEY_LOCAL_PREFS, LEGACY_KEY_LAST_REFLECTION_RUN_TIME, 0L)
        set(value) = put(KEY_LOCAL_PREFS, KEY_LAST_REFLECTION_ATTEMPT_TIME, value)

    /** Last time D2 completed without a retryable or terminal HTTP failure. */
    var lastReflectionSuccessTime: Long
        get() = get(KEY_LOCAL_PREFS, KEY_LAST_REFLECTION_SUCCESS_TIME, 0L).takeIf { it > 0L }
            ?: get(KEY_LOCAL_PREFS, LEGACY_KEY_LAST_REFLECTION_RUN_TIME, 0L)
        set(value) = put(KEY_LOCAL_PREFS, KEY_LAST_REFLECTION_SUCCESS_TIME, value)

    @Synchronized
    fun addReflectionDirtyItemIds(itemIds: Collection<String>): Int {
        val merged = reflectionDirtyItemIds().toMutableSet()
        val previousSize = merged.size
        merged += itemIds.filter(String::isNotBlank)
        put(KEY_LOCAL_PREFS, KEY_REFLECTION_DIRTY_ITEM_IDS, JSONArray(merged.toList()).toString())
        if (merged.size != previousSize) reflectionDirtyVersion = reflectionDirtyVersion + 1L
        return merged.size
    }

    @Synchronized
    fun reflectionDirtyItemCount(): Int = reflectionDirtyItemIds().size

    @Synchronized
    fun clearReflectionDirtyItemIdsIfVersion(expectedVersion: Long) {
        if (reflectionDirtyVersion == expectedVersion) {
            put(KEY_LOCAL_PREFS, KEY_REFLECTION_DIRTY_ITEM_IDS, "[]")
        }
    }

    var reflectionDirtyVersion: Long
        get() = get(KEY_LOCAL_PREFS, KEY_REFLECTION_DIRTY_VERSION, 0L)
        private set(value) = put(KEY_LOCAL_PREFS, KEY_REFLECTION_DIRTY_VERSION, value)

    private fun reflectionDirtyItemIds(): Set<String> = runCatching {
        val values = JSONArray(get(KEY_LOCAL_PREFS, KEY_REFLECTION_DIRTY_ITEM_IDS, "[]"))
        buildSet {
            for (index in 0 until values.length()) values.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }.getOrDefault(emptySet())

    const val KEY_MAX_PAST_CONTEXT = "maxPastContext"
    var maxPastContext: Int
        get() = get(KEY_LOCAL_PREFS, KEY_MAX_PAST_CONTEXT, 0)
        set(value) = put(KEY_LOCAL_PREFS, KEY_MAX_PAST_CONTEXT, value)

    /** Days without new records before a thread's journal is folded/truncated as idle. */
    const val KEY_JOURNAL_IDLE_FOLD_DAYS = "journalIdleFoldDays"
    var journalIdleFoldDays: Int
        get() = get(KEY_LOCAL_PREFS, KEY_JOURNAL_IDLE_FOLD_DAYS, 7)
        set(value) = put(KEY_LOCAL_PREFS, KEY_JOURNAL_IDLE_FOLD_DAYS, value)

    // --- Extraction compaction guards ---
    // Chatty threads that never yield items still accumulate unfolded records; either guard
    // tripping forces a summary-fold (C) run to cap the raw-record payload. Negative disables.

    /** Minutes without a new record before unfolded records get compacted. */
    const val KEY_EXTRACTION_QUIET_WINDOW_MINUTES = "extractionQuietWindowMinutes"
    var extractionQuietWindowMinutes: Int
        get() = get(KEY_LOCAL_PREFS, KEY_EXTRACTION_QUIET_WINDOW_MINUTES, 720)
        set(value) = put(KEY_LOCAL_PREFS, KEY_EXTRACTION_QUIET_WINDOW_MINUTES, value)

    /** Unfolded-record count that forces compaction regardless of quiet time. */
    const val KEY_EXTRACTION_MAX_UNPROCESSED_RECORDS = "extractionMaxUnprocessedRecords"
    var extractionMaxUnprocessedRecords: Int
        get() = get(KEY_LOCAL_PREFS, KEY_EXTRACTION_MAX_UNPROCESSED_RECORDS, 500)
        set(value) = put(KEY_LOCAL_PREFS, KEY_EXTRACTION_MAX_UNPROCESSED_RECORDS, value)

    /** Home "Past X hours" recency window for notification previews/counts. Positive hours only. */
    const val KEY_HOME_NOTI_WINDOW_HOURS = "homeNotiWindowHours"
    var homeNotiWindowHours: Int
        get() = get(KEY_LOCAL_PREFS, KEY_HOME_NOTI_WINDOW_HOURS, 24).coerceAtLeast(1)
        set(value) = put(KEY_LOCAL_PREFS, KEY_HOME_NOTI_WINDOW_HOURS, value.coerceAtLeast(1))

    private const val KEY_INSTALL_TIMESTAMP_MS = "installTimestampMs"

    /**
     * Installation time (first-run) in millis since epoch, persisted locally.
     *
     * This is used for analytics baselining in Firestore.
     */
    var installTimestampMs: Long
        get() {
            val existing = get(KEY_LOCAL_PREFS, KEY_INSTALL_TIMESTAMP_MS, 0L)
            if (existing != 0L) return existing
            val now = System.currentTimeMillis()
            put(KEY_LOCAL_PREFS, KEY_INSTALL_TIMESTAMP_MS, now)
            return now
        }
        set(value) = put(KEY_LOCAL_PREFS, KEY_INSTALL_TIMESTAMP_MS, value)

    fun clearAll() {
        localSharedPrefs.edit { clear() }
        serverSharedPrefs.edit { clear() }
    }
}
