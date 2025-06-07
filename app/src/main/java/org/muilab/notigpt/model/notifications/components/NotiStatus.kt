package org.muilab.notigpt.model.notifications.components

data class NotiStatus (
    var isRead: Boolean = false,
    var isReplied: Boolean = false,
    var isPinned: Boolean = false,
    var isArchived: Boolean = false,
    var isDeleted: Boolean = false,
    var isSpam: Boolean = false,
    var isBlocked: Boolean = false
)