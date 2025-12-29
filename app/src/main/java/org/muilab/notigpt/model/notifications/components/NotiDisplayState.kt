package org.muilab.notigpt.model.notifications.components

import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_UNKNOWN
import org.muilab.notigpt.util.Constants.Companion.NOTI_TASK_STATE_NOT_STARTED

data class NotiDisplayState(
    var isPinned: Boolean,
    var isArchived: Boolean,
    var isVisible: Boolean,
    var isCompletelyRead: Boolean,
    var taskState: Int,

    // New Fields
    var isSetToTop: Boolean = false,
    var setToTopTime: Long = 0L,

    var explanation: String,
    var summary: String,
    var sortScore: Double,

    var category: String,
    var appCategory: String
) {
    constructor() : this(
        isPinned = false,
        isArchived = false,
        isVisible = true,
        isCompletelyRead = false,
        taskState = NOTI_TASK_STATE_NOT_STARTED,
        isSetToTop = false,
        setToTopTime = 0L,
        explanation = "",
        summary = "",
        sortScore = 100.0,
        category = NOTI_CATEGORY_GENERAL,
        appCategory = APP_CATEGORY_UNKNOWN
    )

    fun resetUserState() {
        isArchived = false
        isVisible = true
        taskState = NOTI_TASK_STATE_NOT_STARTED
        category = NOTI_CATEGORY_GENERAL

        // Reset To Top when state resets
        isSetToTop = false
        setToTopTime = 0L

        resetReadState()
    }

    fun resetReadState() {
        isCompletelyRead = false
    }

    fun resetLLMState() {
        explanation = ""
        summary = ""
        sortScore = 100.0
    }
}