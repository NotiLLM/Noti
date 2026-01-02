package org.muilab.notigpt.ui.component.notification.search.elements

import org.muilab.notigpt.model.notifications.NotiRecord

internal fun String?.cleanNullish(): String = when {
    this == null -> ""
    this.equals("null", ignoreCase = true) -> ""
    else -> this
}

internal fun computeOverallTitle(records: List<NotiRecord>, isPeople: Boolean): String {
    val lastRecord = records.lastOrNull() ?: return ""
    val lastRecordTitle = lastRecord.getDisplayedTitle(isPeople).cleanNullish()

    return when {
        lastRecord.extraConversationTitle.cleanNullish().isNotBlank() -> lastRecord.extraConversationTitle.cleanNullish()
        lastRecordTitle.isNotBlank() -> lastRecordTitle
        lastRecord.extraSubText.cleanNullish().isNotBlank() -> lastRecord.extraSubText.cleanNullish()
        else -> ""
    }
}
