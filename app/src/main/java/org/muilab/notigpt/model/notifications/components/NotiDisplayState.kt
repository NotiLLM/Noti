package org.muilab.notigpt.model.notifications.components

import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_UNKNOWN
import org.muilab.notigpt.util.Constants.Companion.NOTI_TASK_STATE_NOT_STARTED

data class NotiDisplayState(
    var isPinned: Boolean,
    var isArchived: Boolean,
    var isVisible: Boolean,
    var isCompletelyRead: Boolean,
    // REMOVED: sortPosition, appCategorySortPosition
    var taskState: Int,

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
        resetReadState()
        // resetSortPositions() // Removed
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