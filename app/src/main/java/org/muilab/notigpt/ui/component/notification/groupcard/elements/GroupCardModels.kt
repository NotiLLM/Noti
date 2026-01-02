package org.muilab.notigpt.ui.component.notification.groupcard.elements

import org.muilab.notigpt.model.notifications.NotiDisplayUnit

internal fun computeItemsToShow(children: List<NotiDisplayUnit>, expanded: Boolean): List<NotiDisplayUnit> {
    if (children.isEmpty()) return emptyList()
    val unreadChildren = children.filter { !it.notiUnit.isRead }
    return if (expanded) children else unreadChildren.ifEmpty { children.take(1) }
}

