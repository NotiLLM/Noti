package org.muilab.notigpt.model.notifications.components

/**
 * Small embedded class to group task-related attributes for a NotiUnit's display state.
 */
data class NotiTaskAttr(
    var shouldExtractTask: Boolean = false,
    var hasGenuineTask: Boolean = false
)
