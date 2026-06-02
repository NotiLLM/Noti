package org.muilab.notigpt.ui.notification.component.card.groupcard.elements

import org.muilab.notigpt.model.notifications.NotiDisplayUnit

/**
 * Small presentation helpers for deciding which group children are visible.
 *
 * Keep these helpers pure and UI-focused. If visibility affects persisted state or grouping semantics, move that
 * rule into DrawerGrouper or the drawer ViewModel instead.
 */
internal fun computeItemsToShow(children: List<NotiDisplayUnit>, expanded: Boolean): List<NotiDisplayUnit> {
    if (children.isEmpty()) return emptyList()
    val unreadChildren = children.filter { !it.notiUnit.isRead }
    return if (expanded) children else unreadChildren.ifEmpty { children.take(1) }
}

