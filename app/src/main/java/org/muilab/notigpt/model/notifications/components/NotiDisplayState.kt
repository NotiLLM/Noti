package org.muilab.notigpt.model.notifications.components

import org.muilab.notigpt.util.Constants
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_UNKNOWN
import org.muilab.notigpt.util.Constants.Companion.NOTI_TASK_STATE_NOT_STARTED

data class NotiDisplayState(
    // DETERMINED BY USER
    var isPinned: Boolean,
    var isArchived: Boolean,
    var isVisible: Boolean,
    var isCompletelyRead: Boolean,
    var sortPosition: Int,
    var appCategorySortPosition: Int,
    var taskState: Int,

    // DETERMINED BY LLM
    var explanation: String,
    var summary: String,
    var sortScore: Double,

    // DETERMINED BY BOTH
    var category: String,

    // APP CATEGORY (DETERMINED BY APP)
    var appCategory: String
) {

    // empty constructor
    constructor() : this(
        isPinned = false,
        isArchived = false,
        isVisible = true,
        isCompletelyRead = false,
        sortPosition = -1,
        appCategorySortPosition = -1,
        taskState = NOTI_TASK_STATE_NOT_STARTED,
        explanation = "",
        summary = "",
        sortScore = 100.0,
        category = NOTI_CATEGORY_GENERAL,
        appCategory = APP_CATEGORY_UNKNOWN
    )

    fun flipPin() {
        isPinned = !isPinned
    }

    fun resetUserState() {
        isArchived = false
        isVisible = true
        isCompletelyRead = false
        taskState = NOTI_TASK_STATE_NOT_STARTED
        category = NOTI_CATEGORY_GENERAL
        resetSortPositions()
    }
    fun resetLLMState() {
        explanation = ""
        summary = ""
        sortScore = 100.0
    }

    fun resetSortPositions() {
        sortPosition = -1
        appCategorySortPosition = -1
    }
}