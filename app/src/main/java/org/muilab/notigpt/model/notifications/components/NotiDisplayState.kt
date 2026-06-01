package org.muilab.notigpt.model.notifications.components

/**
 * Embedded mutable display state for a notification drawer row.
 *
 * This keeps read, pin, dismissal, and LLM display annotations separate from notification metadata. If a
 * flag is about captured content or extraction provenance, it probably belongs outside this component.
 */
data class NotiDisplayState(
    var isPinned: Boolean,
    var isArchived: Boolean,
    /** True when this notification should be hidden from the active drawer. */
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