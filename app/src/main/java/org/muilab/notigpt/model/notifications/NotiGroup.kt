package org.muilab.notigpt.model.notifications

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a user-visible notification group.
 *
 * The group stores identity, title, expansion state, and ordering metadata. Child membership is owned by
 * each NotiUnit's groupId so groups can be renamed or removed without copying notification data.
 */
@Entity(tableName = "noti_group")
data class NotiGroup(
    @PrimaryKey val groupId: String,
    val title: String,
    val isExpanded: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)