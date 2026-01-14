package org.muilab.notigpt.domain.action

/**
 * Typed replacement for stringly-typed notification/group actions.
 *
 * We keep [wireValue] stable to:
 * - preserve existing DB logging values
 * - preserve existing analytics / server payload formats
 */
sealed class NotiActionType(val wireValue: String) {

    data object DismissSwipe : NotiActionType("dismiss_swipe")
    data object AccessClick : NotiActionType("access_click")
    data object AccessClickDismiss : NotiActionType("access_click_dismiss")
    data object AccessClickSearch : NotiActionType("access_click_search")

    data object ToTop : NotiActionType("to_top")
    data object UndoToTop : NotiActionType("undo_to_top")

    data object Archive : NotiActionType("archive")
    data object Unarchive : NotiActionType("unarchive")

    data object MakeTask : NotiActionType("make_task")
    data object DismissTask : NotiActionType("dismiss_task")

    data object Save : NotiActionType("save")
    data object Unsave : NotiActionType("unsave")

    data object Pin : NotiActionType("pin")
    data object Unpin : NotiActionType("unpin")

    data object MarkRead : NotiActionType("mark_read")
    data object MarkAllRead : NotiActionType("mark_all_read")
    data object ScrollRead : NotiActionType("scroll_read")

    data object DeleteAll : NotiActionType("delete_all")

    data object ExtractReminder : NotiActionType("extract_reminder")

    companion object {
        /**
         * Best-effort mapper for legacy call sites.
         */
        fun fromWireValue(value: String): NotiActionType? = when (value) {
            "dismiss_swipe" -> DismissSwipe
            "access_click" -> AccessClick
            "access_click_dismiss" -> AccessClickDismiss
            "access_click_search" -> AccessClickSearch
            "to_top" -> ToTop
            "undo_to_top" -> UndoToTop
            "archive" -> Archive
            "unarchive" -> Unarchive
            "make_task" -> MakeTask
            "dismiss_task" -> DismissTask
            "save" -> Save
            "unsave" -> Unsave
            "pin" -> Pin
            "unpin" -> Unpin
            "mark_read" -> MarkRead
            "mark_all_read" -> MarkAllRead
            "scroll_read" -> ScrollRead
            "delete_all" -> DeleteAll
            "extract_reminder" -> ExtractReminder
            else -> null
        }
    }
}
