package org.muilab.notigpt.model.notifications

class NotiDisplayUnit (
    val notiUnit: NotiUnit,
    val notiRecords: List<NotiRecord>,
) {
    val notiKey: String
        get() = notiUnit.notiKey

    val title: String
        get() = notiRecords.lastOrNull()?.getDisplayedTitle(notiUnit.isPeople) ?: ""

    val sortScore: Double
        get() = notiUnit.sortScore

    val lastUpdateTime: Long
        get() = notiRecords.lastOrNull()?.time ?: 0L

    // NOTE: UI should format this with Context to ensure correct localization.
}