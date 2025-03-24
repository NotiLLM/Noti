package org.muilab.notigpt.model.notifications

import androidx.room.Entity

@Entity(tableName = "noti_category", primaryKeys = ["categoryName"])
data class NotiCategory(
    var categoryName: String,
    var explanation: String = ""
)
