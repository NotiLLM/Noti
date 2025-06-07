package org.muilab.notigpt.model.notifications.components

import org.muilab.notigpt.util.Constants
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL

data class NotiDisplayState(
    // DETERMINED BY USER
    var isPinned: Boolean,
    var isArchived: Boolean,
    var isVisible: Boolean,
    var isCompletelyRead: Boolean,

    // DETERMINED BY LLM
    var explanation: String,
    var summary: String,
    var sortScore: Double,

    // DETERMINED BY BOTH
    var category: String
) {

    // empty constructor
    constructor() : this(
        isPinned = false,
        isArchived = false,
        isVisible = true,
        isCompletelyRead = false,
        explanation = "",
        summary = "",
        sortScore = 100.0,
        category = NOTI_CATEGORY_GENERAL
    )

    fun flipPin() {
        isPinned = !isPinned
    }

    fun resetUserState() {
        isArchived = false
        isVisible = true
        isCompletelyRead = false
        category = NOTI_CATEGORY_GENERAL
    }
    fun resetLLMState() {
        explanation = ""
        summary = ""
        sortScore = 100.0
    }
}