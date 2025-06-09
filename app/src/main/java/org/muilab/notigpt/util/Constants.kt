package org.muilab.notigpt.util

class Constants {
    companion object {
        const val DIFY_UPDATE_NOTIFICATION = "update_notification"
        const val DIFY_POST_NOTIFICATION_ACTION = "post_notification_action"
        const val NOTI_REMOVE_DELAY = 10 * 1000L

        const val NOTI_CATEGORY_GENERAL = "General"
        const val NOTI_CATEGORY_TODO = "To-Do"
        const val NOTI_CATEGORY_ARCHIVE = "Archive"
        const val NOTI_CATEGORY_MAKETASK = "Task"
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
    }
}