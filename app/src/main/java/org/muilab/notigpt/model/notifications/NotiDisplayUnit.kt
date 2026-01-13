package org.muilab.notigpt.model.notifications

import org.muilab.notigpt.util.getRelativeTimeStr

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

    val latestUpdateRelTimeStr: String
        get() = getRelativeTimeStr(lastUpdateTime)
}