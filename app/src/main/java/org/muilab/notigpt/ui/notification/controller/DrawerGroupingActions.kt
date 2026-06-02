package org.muilab.notigpt.ui.notification.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.muilab.notigpt.domain.action.NotiActionType
import org.muilab.notigpt.data.repository.notification.NotiRepository

/**
 * Controller for group membership, expansion, naming, and drag-related grouping actions.
 *
 * Keep group mutations centralized here so cards and drag handles only emit user intent. Ordering/grouping
 * persistence should flow through repository methods rather than direct UI model edits.
 */
internal class DrawerGroupingActions(
    private val scope: CoroutineScope,
    private val notiRepository: NotiRepository,
    private val actionsController: DrawerActionsController,
) {

    fun onMerge(dragId: String, targetId: String) {
        scope.launch(Dispatchers.IO) { notiRepository.merge(dragId, targetId) }
    }

    fun onUngroup(groupId: String) {
        scope.launch(Dispatchers.IO) { notiRepository.ungroup(groupId) }
    }

    fun toggleGroupExpansion(groupId: String, currentExpanded: Boolean) {
        scope.launch(Dispatchers.IO) { notiRepository.updateGroupExpansion(groupId, !currentExpanded) }
    }

    fun renameGroup(groupId: String, newTitle: String) {
        scope.launch(Dispatchers.IO) { notiRepository.updateGroupTitle(groupId, newTitle) }
    }

    fun removeFromGroup(notiKey: String) {
        scope.launch(Dispatchers.IO) { notiRepository.removeFromGroup(notiKey) }
    }

    fun actOnGroup(groupId: String, action: String) {
        val typed = NotiActionType.fromWireValue(action)
        if (typed != null) {
            actOnGroup(groupId, typed)
            return
        }
        scope.launch { actionsController.actOnGroupLegacy(groupId, action) }
    }

    fun actOnGroup(groupId: String, action: NotiActionType) {
        scope.launch { actionsController.actOnGroup(groupId, action) }
    }
}

