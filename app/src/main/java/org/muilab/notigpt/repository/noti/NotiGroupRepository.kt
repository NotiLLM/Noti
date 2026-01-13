package org.muilab.notigpt.repository.noti

import org.muilab.notigpt.database.room.NotiDrawerDao
import org.muilab.notigpt.database.room.NotiGroupDao
import org.muilab.notigpt.model.notifications.NotiGroup
import java.util.UUID

/**
 * Owns all group mutation logic: merge/ungroup/metadata updates and group-level actions.
 */
class NotiGroupRepository(
    private val notiDrawerDao: NotiDrawerDao,
    private val notiGroupDao: NotiGroupDao,
) {

    suspend fun actOnGroup(groupId: String, action: String) {
        when (action) {
            "to_top" -> notiDrawerDao.updateToTopStatusByGroupId(groupId, true, System.currentTimeMillis())
            "undo_to_top" -> notiDrawerDao.updateToTopStatusByGroupId(groupId, false, 0L)
            "dismiss_swipe" -> {
                notiDrawerDao.dismissGroup(groupId)
                val remainingActiveItems = notiDrawerDao.getActiveCountForGroup(groupId)
                if (remainingActiveItems <= 1) {
                    notiDrawerDao.ungroupItems(groupId)
                    notiGroupDao.deleteGroup(groupId)
                }
            }
        }
    }

    suspend fun merge(dragId: String, targetId: String) {
        val dragUnit = notiDrawerDao.getByNotiKey(dragId)
        val targetUnit = notiDrawerDao.getByNotiKey(targetId)
        val targetGroup = notiGroupDao.getGroupById(targetId)
        val dragGroup = notiGroupDao.getGroupById(dragId)

        if (targetUnit != null) {
            // TARGET IS AN ITEM
            if (dragUnit != null) {
                // Item -> Item : Create Group
                val newGroupId = "g_" + UUID.randomUUID().toString().take(8)
                val newTitle = targetUnit.appName
                val newGroup = NotiGroup(groupId = newGroupId, title = newTitle)

                notiGroupDao.insert(newGroup)
                setGroupId(targetUnit.notiKey, newGroupId)
                setGroupId(dragUnit.notiKey, newGroupId)
            } else if (dragGroup != null) {
                // Group -> Item : Create group containing item + group contents
                val newGroupId = "g_" + UUID.randomUUID().toString().take(8)
                val newGroup = NotiGroup(groupId = newGroupId, title = dragGroup.title)
                notiGroupDao.insert(newGroup)

                setGroupId(targetUnit.notiKey, newGroupId)
                moveGroupChildren(dragGroup.groupId, newGroupId)
                notiGroupDao.deleteGroup(dragGroup.groupId)
            }
        } else if (targetGroup != null) {
            // TARGET IS A GROUP
            if (dragUnit != null) {
                // Item -> Group : Add item
                setGroupId(dragUnit.notiKey, targetGroup.groupId)
            } else if (dragGroup != null) {
                // Group -> Group : Merge children
                moveGroupChildren(dragGroup.groupId, targetGroup.groupId)
                notiGroupDao.deleteGroup(dragGroup.groupId)
            }
        }
    }

    suspend fun ungroup(groupId: String) {
        moveGroupChildren(groupId, null)
        notiGroupDao.deleteGroup(groupId)
    }

    suspend fun updateGroupExpansion(groupId: String, expanded: Boolean) {
        notiGroupDao.updateExpansion(groupId, expanded)
    }

    suspend fun updateGroupTitle(groupId: String, title: String) {
        notiGroupDao.updateTitle(groupId, title)
    }

    suspend fun removeFromGroup(notiKey: String) {
        setGroupId(notiKey, null)
    }

    private suspend fun setGroupId(notiKey: String, groupId: String?) {
        notiDrawerDao.updateGroupId(notiKey, groupId)
    }

    private suspend fun moveGroupChildren(oldGroupId: String, newGroupId: String?) {
        notiDrawerDao.moveGroupChildren(oldGroupId, newGroupId)
    }
}
