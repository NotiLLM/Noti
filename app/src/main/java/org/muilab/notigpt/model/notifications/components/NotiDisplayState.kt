package org.muilab.notigpt.model.notifications.components

data class NotiDisplayState(
    var isPinned: Boolean,
    var isArchived: Boolean,
    /**
     * New semantics: isDismissed = true means the notification is hidden from the active drawer.
     * This replaces the previous isVisible flag (flipped).
     */
    var isDismissed: Boolean,
    var isRead: Boolean,

    var isSetToTop: Boolean = false,
    var setToTopTime: Long = 0L,

    /**
     * Manual drawer ordering.
     * -1 means "unset" (auto-fill by time for remaining gaps).
     */
    var sortPosition: Int = -1,

    var explanation: String,
    var summary: String,
    var sortScore: Double,
) {
    constructor() : this(
        isPinned = false,
        isArchived = false,
        isDismissed = false,
        isRead = false,
        isSetToTop = false,
        setToTopTime = 0L,
        sortPosition = -1,
        explanation = "",
        summary = "",
        sortScore = 100.0,
    )

    fun resetUserState() {
        isArchived = false
        isDismissed = false

        // Reset To Top when state resets
        isSetToTop = false
        setToTopTime = 0L

        // Reset manual ordering
        sortPosition = -1
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