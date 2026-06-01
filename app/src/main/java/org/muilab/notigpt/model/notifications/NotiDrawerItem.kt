package org.muilab.notigpt.model.notifications

sealed interface NotiDrawerItem {
    val id: String
    val latestTime: Long
    // Add sorting attributes
    val isSetToTop: Boolean
    val setToTopTime: Long
}

/**
 * Sealed-ish drawer row models used after grouping notification display units.
 *
 * A row is either a loose notification item or a group item. Keep this model presentation-focused;
 * storage of group membership belongs to NotiUnit and NotiGroup.
 */
data class NotiItem(
    val displayUnit: NotiDisplayUnit
) : NotiDrawerItem {
    override val id: String = displayUnit.notiKey
    override val latestTime: Long = displayUnit.lastUpdateTime
    override val isSetToTop: Boolean = displayUnit.notiUnit.isSetToTop
    override val setToTopTime: Long = displayUnit.notiUnit.setToTopTime
}

data class NotiGroupItem(
    val group: NotiGroup,
    val children: List<NotiDisplayUnit>
) : NotiDrawerItem {
    override val id: String = group.groupId
    override val latestTime: Long = children.maxOfOrNull { it.lastUpdateTime } ?: group.createdAt

    // Group is "Topped" if any child is topped
    override val isSetToTop: Boolean = children.any { it.notiUnit.isSetToTop }

    // Group's top time is the max of its children
    override val setToTopTime: Long = children.maxOfOrNull { it.notiUnit.setToTopTime } ?: 0L
}