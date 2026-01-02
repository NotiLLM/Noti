package org.muilab.notigpt.model.notifications

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "noti_group")
data class NotiGroup(
    @PrimaryKey val groupId: String,
    val title: String,
    val isExpanded: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)