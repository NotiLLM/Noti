package org.muilab.notigpt.model.notifications.components

import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_UNKNOWN

data class NotiDisplayState(
    var isPinned: Boolean,
    var isArchived: Boolean,
    var isVisible: Boolean,
    var isRead: Boolean,

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
        isRead = false,
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
        category = NOTI_CATEGORY_GENERAL

        // Reset To Top when state resets
        isSetToTop = false
        setToTopTime = 0L
    }

    fun resetReadState() {
        isRead = false
    }

    fun resetLLMState() {
        explanation = ""
        summary = ""
        sortScore = 100.0
    }
}