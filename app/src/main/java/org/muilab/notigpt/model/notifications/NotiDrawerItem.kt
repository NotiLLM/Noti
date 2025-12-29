package org.muilab.notigpt.model.notifications

// Sealed interface for polymorphic list items
sealed interface NotiDrawerItem {
    val id: String
    val latestTime: Long
}

// Wrapper for a NotiDisplayUnit (Leaf)
data class NotiItem(
    val displayUnit: NotiDisplayUnit
) : NotiDrawerItem {
    override val id: String = displayUnit.notiKey
    override val latestTime: Long = displayUnit.lastUpdateTime
}

// Wrapper for a Group (Node)
data class NotiGroupItem(
    val group: NotiGroup,
    val children: List<NotiDisplayUnit>
) : NotiDrawerItem {
    override val id: String = group.groupId
    // Group time is the max time of its children, or creation time if empty
    override val latestTime: Long = children.maxOfOrNull { it.lastUpdateTime } ?: group.createdAt
}