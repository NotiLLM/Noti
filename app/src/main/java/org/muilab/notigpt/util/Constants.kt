package org.muilab.notigpt.util

class Constants {
    companion object {
        val HISTORY_NOTI_COUNT_THRESHOLD: Int = 10
        val HISTORY_NOTI_TIME_THRESHOLD: Long = 24 * 60 * 60 * 1000

        val API_SYNC_DRAWER = "sync_drawer"
        val API_SORT_DRAWER = "sort_drawer"
        val API_UPDATE_USER = "update_user"
        val API_EXPORT_DB = "export_db"
        val API_CLEAR_DB = "clear_db"
        val API_INSERT_PREFERENCE = "insert_preference"
    }
}