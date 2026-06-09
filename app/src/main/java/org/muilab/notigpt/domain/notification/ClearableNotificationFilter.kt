package org.muilab.notigpt.domain.notification

import org.muilab.notigpt.model.notifications.NotiDisplayUnit

fun countClearableActiveNotifications(units: List<NotiDisplayUnit>): Int {
    return units.count { displayUnit ->
        !displayUnit.notiUnit.isDismissed && !displayUnit.notiUnit.isPinned
    }
}
