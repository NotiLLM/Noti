package org.muilab.notigpt.ui.review.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.muilab.notigpt.R
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.repository.reminder.PendingProposedOpRepository
import org.muilab.notigpt.data.repository.reminder.ReminderRelatedNotificationsRepository
import org.muilab.notigpt.data.repository.reminder.SavedItemChangeLogRepository
import org.muilab.notigpt.data.repository.reminder.SavedItemRepository
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedSubItem

/**
 * Drives the swipe-to-review screen over the fully-staged model: pipeline proposals live in
 * pending_proposed_op and are grouped item-level ([ReviewEntry] per eventual item). Nothing touches
 * saved_item until approve; reject just discards the staged ops. Items that entered the legacy
 * new/updated states directly (single-item regeneration) still surface as entries with no group.
 */
class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application.applicationContext)
    private val repo = SavedItemRepository(db.reminderListDao(), application.applicationContext)
    private val changeLogRepo = SavedItemChangeLogRepository(db.savedItemChangeLogDao())
    private val relatedRepo = ReminderRelatedNotificationsRepository(application.applicationContext)
    private val pendingProposedOpRepo = PendingProposedOpRepository(application.applicationContext)

    enum class ReviewFilter { All, NewTasks, UpdatedTasks, NewKeeps, UpdatedKeeps }

    private val _filter = MutableStateFlow(ReviewFilter.All)
    val filter: StateFlow<ReviewFilter> = _filter
    fun setFilter(f: ReviewFilter) { _filter.value = f }

    /**
     * One review card: [preview] is what accepting would produce (a would-be item for creates, the
     * current item with staged changes applied in memory for updates/merges, or the item itself
     * for legacy entries). [group] is null for legacy new/updated items.
     */
    data class ReviewEntry(
        val key: String,
        val preview: SavedItem,
        val previewSubItems: List<SavedSubItem>,
        val group: PendingProposedOpRepository.OpGroup?,
        val reason: String,
    ) {
        val isNewLike: Boolean get() = preview.state == SavedItemState.New
    }

    /** All entries awaiting review. The screen applies the chip filter locally. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<ReviewEntry>> = combine(
        pendingProposedOpRepo.observePending(),
        repo.observeNewItems(),
    ) { ops, legacyItems -> ops to legacyItems }
        .mapLatest { (ops, legacyItems) ->
            val groups = pendingProposedOpRepo.groupOps(ops)
            val targeted = groups.mapNotNull { it.targetItemId }.toSet()
            val groupEntries = groups.mapNotNull { group ->
                val preview = pendingProposedOpRepo.buildPreview(group) ?: return@mapNotNull null
                ReviewEntry(
                    key = group.key,
                    preview = preview.item,
                    previewSubItems = preview.subItems,
                    group = group,
                    reason = group.reason,
                )
            }
            // Legacy items already targeted by a staged group show once, as the group entry.
            val legacyEntries = legacyItems
                .filter { it.savedItemId !in targeted }
                .map { item ->
                    ReviewEntry(
                        key = "legacy_${item.savedItemId}",
                        preview = item,
                        previewSubItems = emptyList(),
                        group = null,
                        reason = "",
                    )
                }
            groupEntries + legacyEntries
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun matchesFilter(entry: ReviewEntry, f: ReviewFilter): Boolean {
        val isTask = entry.preview.itemType == SavedItemType.Task
        val isNew = entry.isNewLike
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

    /** The one-line "what changed" for the minimal card: staged reason first, then the change log. */
    suspend fun latestChangeSummary(entry: ReviewEntry): String? {
        entry.reason.takeIf { it.isNotBlank() }?.let { return it }
        val item = entry.preview
        return changeLogRepo.getNewerThan(item.savedItemId, item.lastViewedChangeAt)
            .firstOrNull { it.changeSummary.isNotBlank() }
            ?.changeSummary
    }

    private val _related = MutableStateFlow(RelatedState())
    val related: StateFlow<RelatedState> = _related

    data class RelatedState(
        val entryKey: String? = null,
        val isLoading: Boolean = false,
        val value: ReminderRelatedNotificationsRepository.RelatedNotifications =
            ReminderRelatedNotificationsRepository.RelatedNotifications.Empty,
    )

    fun loadRelated(entry: ReviewEntry) {
        if (_related.value.entryKey == entry.key) return
        viewModelScope.launch {
            _related.value = RelatedState(entry.key, isLoading = true)
            val value = try {
                val group = entry.group
                if (group != null) {
                    // Staged ops carry their evidence in the payload — no link rows exist yet.
                    val evidence = group.ops.flatMap { op ->
                        val arr = JSONArray(op.evidenceRecordIds)
                        buildList { for (i in 0 until arr.length()) arr.optString(i).takeIf(String::isNotBlank)?.let(::add) }
                    }
                    relatedRepo.getByRecordIds(evidence)
                } else {
                    relatedRepo.getRelatedNotifications(entry.preview)
                }
            } catch (_: Throwable) {
                ReminderRelatedNotificationsRepository.RelatedNotifications.Empty
            }
            _related.value = RelatedState(entry.key, isLoading = false, value = value)
        }
    }

    // ── Actions with undo ──

    private sealed interface UndoableAction {
        /** A staged group was applied; undo restores the pre-apply state and re-stages the ops. */
        data class ApplyGroup(val outcome: PendingProposedOpRepository.ApplyOutcome) : UndoableAction

        /** A staged group was discarded; undo re-inserts the ops. */
        data class DiscardGroup(val group: PendingProposedOpRepository.OpGroup) : UndoableAction

        // Legacy (non-staged) items keep their old semantics.
        data class Approve(val item: SavedItem) : UndoableAction
        data class RejectNew(val item: SavedItem, val subTasks: List<SavedSubItem>) : UndoableAction
        data class RejectUpdated(val outcome: SavedItemRepository.RevertOutcome) : UndoableAction
    }

    private var pendingUndo: UndoableAction? = null

    /**
     * One review action's snackbar. [item] is what was acted on (for "Tell it why"); [canTeach] is
     * true for rejects — the moments where teaching a preference makes sense.
     */
    data class ReviewSnackbar(val messageRes: Int, val item: SavedItem?, val canTeach: Boolean)

    private val _snackbar = Channel<ReviewSnackbar>(Channel.CONFLATED)
    /** Emits when an action lands, so the screen can show a snackbar (Undo + optional Tell-it-why). */
    val snackbar: Flow<ReviewSnackbar> = _snackbar.receiveAsFlow()

    // ── This review pass: tasks approved that still have no planned (do) date ──
    // Drives the end-of-stack "set do-dates?" offer. Reset when the screen is (re)entered.
    private val approvedNeedingDoDate = linkedSetOf<String>()
    fun approvedNeedingDoDateCount(): Int = approvedNeedingDoDate.size
    fun resetReviewSession() = approvedNeedingDoDate.clear()

    private fun noteApprovedForDoDate(item: SavedItem) {
        if (item.isTask && !SavedItem.hasPlannedDate(item.doAtMs)) approvedNeedingDoDate.add(item.savedItemId)
    }

    fun approve(entry: ReviewEntry) {
        viewModelScope.launch {
            val group = entry.group
            if (group != null) {
                val outcome = pendingProposedOpRepo.applyGroup(group) ?: return@launch
                pendingUndo = UndoableAction.ApplyGroup(outcome)
                noteApprovedForDoDate(entry.preview.copy(savedItemId = outcome.appliedItemId))
            } else {
                repo.acknowledgeReview(entry.preview.savedItemId, System.currentTimeMillis())
                pendingUndo = UndoableAction.Approve(entry.preview)
                noteApprovedForDoDate(entry.preview)
            }
            _snackbar.trySend(ReviewSnackbar(R.string.review_approved_toast, entry.preview, canTeach = false))
        }
    }

    /** Edit-in-review "Save & Approve": apply the staged group, then persist the user's edits on
     *  top (which shields them from later LLM updates). Editing implies acceptance. */
    fun saveApprove(entry: ReviewEntry, edited: SavedItem) {
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            val group = entry.group
            if (group != null) {
                val outcome = pendingProposedOpRepo.applyGroup(group) ?: return@launch
                // The editor edited the preview; retarget its id to the real applied item.
                repo.upsert(
                    edited.copy(
                        savedItemId = outcome.appliedItemId,
                        state = SavedItemState.Saved,
                        origin = "manual",
                        userEdited = true,
                        lastUpdateTimestamp = ts,
                        lastViewedChangeAt = ts,
                    )
                )
                noteApprovedForDoDate(edited.copy(savedItemId = outcome.appliedItemId))
            } else {
                repo.upsert(edited.copy(origin = "manual", userEdited = true, lastUpdateTimestamp = ts))
                repo.acknowledgeReview(edited.savedItemId, ts)
                noteApprovedForDoDate(edited)
            }
            pendingUndo = null
            _snackbar.trySend(ReviewSnackbar(R.string.review_approved_toast, edited, canTeach = false))
        }
    }

    fun reject(entry: ReviewEntry) {
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            val group = entry.group
            if (group != null) {
                pendingProposedOpRepo.discardGroup(group, ts)
                pendingUndo = UndoableAction.DiscardGroup(group)
                _snackbar.trySend(ReviewSnackbar(R.string.review_rejected_toast, entry.preview, canTeach = true))
                return@launch
            }
            val item = entry.preview
            if (item.state == SavedItemState.Updated) {
                val outcome = repo.revertPendingLlmUpdates(item.savedItemId, ts)
                if (outcome != null) {
                    pendingUndo = UndoableAction.RejectUpdated(outcome)
                    _snackbar.trySend(ReviewSnackbar(R.string.review_reverted_toast, item, canTeach = true))
                }
            } else {
                // Legacy new item → hard delete; capture rows so undo can restore them.
                val subTasks = db.subTaskDao().getByReminderId(item.savedItemId)
                repo.deleteById(item.savedItemId, ts)
                pendingUndo = UndoableAction.RejectNew(item, subTasks)
                _snackbar.trySend(ReviewSnackbar(R.string.review_rejected_toast, item, canTeach = true))
            }
        }
    }

    fun approveAll(entries: List<ReviewEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            entries.forEach { entry ->
                val group = entry.group
                if (group != null) {
                    val outcome = pendingProposedOpRepo.applyGroup(group, ts) ?: return@forEach
                    noteApprovedForDoDate(entry.preview.copy(savedItemId = outcome.appliedItemId))
                } else {
                    repo.acknowledgeReview(entry.preview.savedItemId, ts)
                    noteApprovedForDoDate(entry.preview)
                }
            }
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
                is UndoableAction.ApplyGroup -> pendingProposedOpRepo.undoApply(action.outcome, ts)
                is UndoableAction.DiscardGroup -> pendingProposedOpRepo.restoreDiscarded(action.group)
                is UndoableAction.Approve -> {
                    repo.upsert(action.item)
                    approvedNeedingDoDate.remove(action.item.savedItemId)
                }
                is UndoableAction.RejectNew -> {
                    repo.upsert(action.item)
                    if (action.subTasks.isNotEmpty()) db.subTaskDao().upsertAll(action.subTasks)
                }
                is UndoableAction.RejectUpdated -> repo.undoRevert(action.outcome, ts)
            }
        }
    }
}
