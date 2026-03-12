package org.muilab.notigpt.model.notifications.components

/**
 * Small embedded class to group task-related attributes for a NotiUnit's display state.
 */
data class NotiReminderAttr(
    var shouldExtractReminder: Boolean = false,
    var hasTask: Boolean = false,
    var hasMemo: Boolean = false,
    var hasEvent: Boolean = false
)
