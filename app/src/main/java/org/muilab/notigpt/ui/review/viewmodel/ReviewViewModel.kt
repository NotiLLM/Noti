package org.muilab.notigpt.ui.review.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.repository.reminder.ReminderRelatedNotificationsRepository
import org.muilab.notigpt.data.repository.reminder.SavedItemChangeLogRepository
import org.muilab.notigpt.data.repository.reminder.SavedItemRepository
import org.muilab.notigpt.data.repository.reminder.SavedSubItemRepository
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedItemType

/**
 * Drives the swipe-to-review screen: the list of new/updated items awaiting review, the chip filter
 * over that list, the approve/reject actions with their revert semantics, and single-step undo.
 */
class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application.applicationContext)
    private val repo = SavedItemRepository(db.reminderListDao(), application.applicationContext)
    private val subTaskRepo = SavedSubItemRepository(db.subTaskDao())
    private val changeLogRepo = SavedItemChangeLogRepository(db.savedItemChangeLogDao())
    private val relatedRepo = ReminderRelatedNotificationsRepository(application.applicationContext)

    enum class ReviewFilter { All, NewTasks, UpdatedTasks, NewKeeps, UpdatedKeeps }

    private val _filter = MutableStateFlow(ReviewFilter.All)
    val filter: StateFlow<ReviewFilter> = _filter
    fun setFilter(f: ReviewFilter) { _filter.value = f }

    /** All items awaiting review, newest first. The screen applies the chip filter locally. */
    val newItems: StateFlow<List<SavedItem>> = repo.observeNewItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun matchesFilter(item: SavedItem, f: ReviewFilter): Boolean {
        val isTask = item.itemType == SavedItemType.Task
        val isNew = item.state == SavedItemState.New
        return when (f) {
            ReviewFilter.All -> true
            ReviewFilter.NewTasks -> isTask && isNew
            ReviewFilter.UpdatedTasks -> isTask && !isNew
            ReviewFilter.NewKeeps -> !isTask && isNew
            ReviewFilter.UpdatedKeeps -> !isTask && !isNew
        }
    }

    // ── Change history / related notifications for card expansion ──

    fun changeLogFlow(savedItemId: String): Flow<List<SavedItemChangeLog>> =
        changeLogRepo.observeByItem(savedItemId)

    /** The newest unacknowledged change's one-line summary, for the minimal card of an updated item. */
    suspend fun latestChangeSummary(item: SavedItem): String? =
        changeLogRepo.getNewerThan(item.savedItemId, item.lastViewedChangeAt)
            .firstOrNull { it.changeSummary.isNotBlank() }
            ?.changeSummary

    private val _related = MutableStateFlow(RelatedState())
    val related: StateFlow<RelatedState> = _related

    data class RelatedState(
        val savedItemId: String? = null,
        val isLoading: Boolean = false,
        val value: ReminderRelatedNotificationsRepository.RelatedNotifications =
            ReminderRelatedNotificationsRepository.RelatedNotifications.Empty,
    )

    fun loadRelated(item: SavedItem) {
        if (_related.value.savedItemId == item.savedItemId) return
        viewModelScope.launch {
            _related.value = RelatedState(item.savedItemId, isLoading = true)
            val value = try {
                relatedRepo.getRelatedNotifications(item)
            } catch (_: Throwable) {
                ReminderRelatedNotificationsRepository.RelatedNotifications.Empty
            }
            _related.value = RelatedState(item.savedItemId, isLoading = false, value = value)
        }
    }

    // ── Actions with undo ──

    private sealed interface UndoableAction {
        data class Approve(val item: SavedItem) : UndoableAction
        data class RejectNew(val item: SavedItem, val subTaskIds: List<String>) : UndoableAction
        data class RejectUpdated(val outcome: SavedItemRepository.RevertOutcome) : UndoableAction
    }

    private var pendingUndo: UndoableAction? = null

    private val _snackbar = Channel<Int>(Channel.CONFLATED)
    /** Emits a string res id when an action lands, so the screen can show a snackbar with Undo. */
    val snackbar: Flow<Int> = _snackbar.receiveAsFlow()

    fun approve(item: SavedItem) {
        viewModelScope.launch {
            repo.acknowledgeReview(item.savedItemId, System.currentTimeMillis())
            pendingUndo = UndoableAction.Approve(item)
            _snackbar.trySend(R.string.review_approved_toast)
        }
    }

    fun reject(item: SavedItem) {
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            if (item.state == SavedItemState.Updated) {
                val outcome = repo.revertPendingLlmUpdates(item.savedItemId, ts)
                if (outcome != null) {
                    pendingUndo = UndoableAction.RejectUpdated(outcome)
                    _snackbar.trySend(R.string.review_reverted_toast)
                }
            } else {
                // New item → soft delete, capturing sub-task ids so undo can restore them.
                val subTaskIds = db.subTaskDao().getByReminderId(item.savedItemId).map { st -> st.savedSubItemId }
                subTaskRepo.softDeleteByParentId(item.savedItemId, ts)
                repo.deleteById(item.savedItemId, ts)
                pendingUndo = UndoableAction.RejectNew(item, subTaskIds)
                _snackbar.trySend(R.string.review_rejected_toast)
            }
        }
    }

    fun approveAll(items: List<SavedItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            repo.acknowledgeReviewByIds(items.map { it.savedItemId }, System.currentTimeMillis())
            // Bulk approve isn't individually undoable; clear any stale single-action undo.
            pendingUndo = null
        }
    }

    fun undoLast() {
        val action = pendingUndo ?: return
        pendingUndo = null
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            when (action) {
                is UndoableAction.Approve -> repo.upsert(action.item)
                is UndoableAction.RejectNew -> {
                    repo.upsert(action.item)
                    if (action.subTaskIds.isNotEmpty()) db.subTaskDao().restoreByIds(action.subTaskIds, ts)
                }
                is UndoableAction.RejectUpdated -> repo.undoRevert(action.outcome, ts)
            }
        }
    }
}
