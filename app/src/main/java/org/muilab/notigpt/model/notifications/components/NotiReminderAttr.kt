package org.muilab.notigpt.model.notifications.components

/**
 * Embedded reminder-detection attributes attached to the current notification row.
 *
 * These flags summarize scan/extraction results at the notification-key level. Record-level processing flags
 * stay on NotiRecord because each captured record can be scanned or extracted independently.
 */
data class NotiReminderAttr(
    var shouldExtractReminder: Boolean = false,
    var hasTask: Boolean = false,
    var hasMemo: Boolean = false,
    var hasEvent: Boolean = false
)
