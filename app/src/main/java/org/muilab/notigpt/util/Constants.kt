package org.muilab.notigpt.util

class Constants {
    companion object {
        const val DIFY_UPDATE_NOTIFICATION = "update_notification"
        const val DIFY_POST_NOTIFICATION_PREFERENCE = "post_notification_preference"
        const val DIFY_POST_NOTIFICATION_ACTION = "post_notification_action"
        const val NOTI_REMOVE_DELAY = 10 * 1000L

        const val NOTI_CATEGORY_GENERAL = "General"
        const val NOTI_CATEGORY_TODO = "To-Do"
        const val NOTI_CATEGORY_ARCHIVE = "Archive"
        const val NOTI_CATEGORY_DELETED = "Deleted"

        const val NOTI_ACTION_CLICK = "action_click"
        const val NOTI_ACTION_DISMISS = "action_dismiss"
        const val NOTI_ACTION_EXPAND = "action_expand"
        const val NOTI_ACTION_PIN = "action_pin"
        const val NOTI_ACTION_ARCHIVE = "action_archive"
        const val NOTI_ACTION_BIN = "action_bin"
    }
}