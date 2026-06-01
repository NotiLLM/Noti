package org.muilab.notigpt.model.notifications.components

/**
 * Small value holder for notification status flags that may need to travel separately from NotiUnit.
 *
 * Keep this only if status is passed across boundaries independently of metadata/display state. If callers
 * always need the full notification row, merge the flags back into the relevant embedded component instead.
 */
data class NotiStatus (
    var isRead: Boolean = false,
    var isReplied: Boolean = false,
    var isPinned: Boolean = false,
    var isArchived: Boolean = false,
    var isDeleted: Boolean = false,
    var isSpam: Boolean = false,
    var isBlocked: Boolean = false
)