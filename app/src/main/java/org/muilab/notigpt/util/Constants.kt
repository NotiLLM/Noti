package org.muilab.notigpt.util

/**
 * Central constants for notification categories, actions, preference keys, and timing values.
 *
 * Keep constants here only when they are shared across layers. Feature-local constants should stay near their
 * owning component or repository to avoid turning this class into a catch-all.
 */
class Constants {
    companion object {
        const val DIFY_UPDATE_NOTIFICATION = "update_notification"
        const val DIFY_POST_NOTIFICATION_ACTION = "post_notification_action"
        const val NOTI_REMOVE_DELAY = 10 * 1000L

        const val NOTI_CATEGORY_GENERAL = "General"
        const val NOTI_CATEGORY_TODO = "To-Do"
        const val NOTI_CATEGORY_ARCHIVE = "Archive"
        const val NOTI_CATEGORY_MAKETASK = "Task"
        const val NOTI_CATEGORY_SAVE = "Save"
        const val NOTI_CATEGORY_DELETED = "Deleted"

        const val NOTI_ACTION_CLICK_PINNED = "action_click_pinned"
        const val NOTI_ACTION_CLICK_NOT_PINNED = "action_click_not_pinned"
        const val NOTI_ACTION_SWIPE = "action_swipe"
        const val NOTI_ACTION_EXPAND = "action_expand"
        const val NOTI_ACTION_PIN = "action_pin"
        const val NOTI_ACTION_UNPIN = "action_unpin"
        const val NOTI_ACTION_ARCHIVE = "action_archive"
        const val NOTI_ACTION_UNARCHIVE = "action_unarchive"
        const val NOTI_ACTION_BIN = "action_bin"

        // App Category constants
        const val APP_CATEGORY_ALL = "All"
        const val APP_CATEGORY_IM = "IM"
        const val APP_CATEGORY_MEDIA = "Media"
        const val APP_CATEGORY_SOCIAL = "Social"
        const val APP_CATEGORY_SMS = "SMS"
        const val APP_CATEGORY_SHOPPING = "Shopping"
        const val APP_CATEGORY_FINANCE = "Finance"
        const val APP_CATEGORY_NEWS = "News"
        const val APP_CATEGORY_GAME = "Game"
        const val APP_CATEGORY_TOOL = "Tool"
        const val APP_CATEGORY_EMAIL = "Email"
        const val APP_CATEGORY_WEATHER = "Weather"
        const val APP_CATEGORY_SYSTEM = "System"
        const val APP_CATEGORY_TRANSPORTATION = "Transportation"
        const val APP_CATEGORY_READING = "Reading"
        const val APP_CATEGORY_HEALTH_FITNESS = "Health_Fitness"
        const val APP_CATEGORY_DATING = "Dating"
        const val APP_CATEGORY_CALENDAR_REMINDER = "Calendar_Reminder"
        const val APP_CATEGORY_PHONE = "Phone"
        const val APP_CATEGORY_UNKNOWN = "Unknown"

        // Available app categories list
        val APP_CATEGORIES = setOf(
            APP_CATEGORY_IM,
            APP_CATEGORY_MEDIA,
            APP_CATEGORY_SOCIAL,
            APP_CATEGORY_SMS,
            APP_CATEGORY_SHOPPING,
            APP_CATEGORY_FINANCE,
            APP_CATEGORY_NEWS,
            APP_CATEGORY_GAME,
            APP_CATEGORY_TOOL,
            APP_CATEGORY_EMAIL,
            APP_CATEGORY_WEATHER,
            APP_CATEGORY_SYSTEM,
            APP_CATEGORY_TRANSPORTATION,
            APP_CATEGORY_READING,
            APP_CATEGORY_HEALTH_FITNESS,
            APP_CATEGORY_DATING,
            APP_CATEGORY_CALENDAR_REMINDER,
            APP_CATEGORY_PHONE,
            APP_CATEGORY_UNKNOWN
        )

        // Time in milliseconds for one week
        const val NOTI_RECORD_EXPIRE_TIME_MS = 7 * 24 * 60 * 60 * 1000L
        // The maximum number of read, expired notification records to keep visible per key
        const val MAX_EXPIRED_RECORDS_PER_KEY = 5

        // n8n task API types
        const val N8N_TASK_SCAN = "task_scan"
        const val N8N_TASK_EXTRACTION = "task_extraction"
        const val N8N_REGENERATE_ONE = "regenerate_one"
        const val N8N_REGENERATE_ALL = "regenerate_all"
        const val N8N_RERANK = "rerank"

        // n8n preference API types
        const val N8N_PREFERENCE_QUICK_SYNC = "preference_quick_sync"
        const val N8N_PREFERENCE_CHAT_INTERACT = "preference_chat_interact"
    }
}