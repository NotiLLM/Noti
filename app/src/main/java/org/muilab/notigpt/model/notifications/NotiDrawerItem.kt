package org.muilab.notigpt.model.notifications

sealed interface NotiDrawerItem {
    val id: String
    val latestTime: Long
    // Add sorting attributes
    val isSetToTop: Boolean
    val setToTopTime: Long
}

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