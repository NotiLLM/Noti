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
import org.muilab.notigpt.data.repository.saveditem.PendingProposedOpRepository
import org.muilab.notigpt.data.repository.saveditem.SavedItemRelatedNotificationsRepository
import org.muilab.notigpt.data.repository.saveditem.SavedItemChangeLogRepository
import org.muilab.notigpt.data.repository.saveditem.SavedItemRepository
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.TodoStep
import org.muilab.notigpt.model.features.ReviewItemDraft
import org.muilab.notigpt.model.features.PendingReviewDraft
import org.muilab.notigpt.model.features.PendingProposedOpType
import org.muilab.notigpt.model.features.ReviewTranslationState
import org.muilab.notigpt.data.remote.n8n.enqueueReviewTranslation
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Drives the swipe-to-review screen over the fully-staged model: pipeline proposals live in
 * pending_proposed_op and are grouped item-level ([ReviewEntry] per eventual item). Nothing touches
 * saved_item until approve; reject just discards the staged ops. Items that entered the legacy
 * new/updated states directly (single-item regeneration) still surface as entries with no group.
 */
class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application.applicationContext)
    private val repo = SavedItemRepository(db.savedItemDao(), application.applicationContext)
    private val changeLogRepo = SavedItemChangeLogRepository(db.savedItemChangeLogDao())
    private val relatedRepo = SavedItemRelatedNotificationsRepository(application.applicationContext)
    private val pendingProposedOpRepo = PendingProposedOpRepository(application.applicationContext)

    enum class ReviewFilter { All, NewTodos, UpdatedTodos, NewKeeps, UpdatedKeeps }
    enum class ReviewOperationKind { Create, Update, Merge }

    private val _filter = MutableStateFlow(ReviewFilter.All)
    val filter: StateFlow<ReviewFilter> = _filter
    fun setFilter(f: ReviewFilter) { _filter.value = f }

    private val _deferredKeys = MutableStateFlow<Set<String>>(emptySet())
    val deferredKeys: StateFlow<Set<String>> = _deferredKeys
    fun reviewLater(entry: ReviewEntry) { _deferredKeys.value += entry.key }
    fun restoreDeferred() { _deferredKeys.value = emptySet() }

    /**
     * One review card: [preview] is what accepting would produce (a would-be item for creates, the
     * current item with staged changes applied in memory for updates/merges, or the item itself
     * for legacy entries). [group] is null for legacy new/updated items.
     */
    data class ReviewEntry(
        val key: String,
        val preview: SavedItem,
        val previewSteps: List<TodoStep>,
        val survivor: PendingProposedOpRepository.MergeSourceSnapshot?,
        val mergeSources: List<PendingProposedOpRepository.MergeSourceSnapshot>,
        val group: PendingProposedOpRepository.OpGroup?,
        val reason: String,
        val operationKind: ReviewOperationKind,
        val translatedDraft: ReviewItemDraft? = null,
    ) {
        val isNewLike: Boolean get() = group?.isCreate == true || operationKind == ReviewOperationKind.Create
    }

    /** All entries awaiting review. The screen applies the chip filter locally. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<ReviewEntry>> = combine(
        pendingProposedOpRepo.observePending(),
        repo.observeNewItems(),
        pendingProposedOpRepo.observeReviewDrafts(),
    ) { ops, legacyItems, drafts -> Triple(ops, legacyItems, drafts) }
        .mapLatest { (ops, legacyItems, drafts) ->
            val draftsByKey = drafts.associateBy { it.reviewKey }
            val groups = pendingProposedOpRepo.groupOps(ops)
            val targeted = groups.mapNotNull { it.targetItemId }.toSet()
            val groupEntries = groups.mapNotNull { group ->
                val translation = ReviewTranslationState.fromJson(draftsByKey[group.key]?.translationStateJson)
                val translationMatches = translation?.sourceOpIds == group.ops.map { it.opId }
                if (translation?.isPending == true && translationMatches) return@mapNotNull null
                val preview = pendingProposedOpRepo.buildPreview(group) ?: return@mapNotNull null
                val kind = when {
                    group.isCreate && group.ops.any { pending ->
                        runCatching { org.json.JSONObject(pending.payload).optString("reviewOperationKind") == "merge" }
                            .getOrDefault(false)
                    } -> ReviewOperationKind.Merge
                    group.isCreate -> ReviewOperationKind.Create
                    group.ops.any { it.opType == PendingProposedOpType.Merge } -> ReviewOperationKind.Merge
                    else -> ReviewOperationKind.Update
                }
                val translatedDraft = translation?.takeIf { translationMatches }?.translatedDraft?.let { translated ->
                    overlayTranslatedText(ReviewItemDraft(preview.item, preview.steps), translated)
                }
                ReviewEntry(
                    key = group.key,
                    preview = translatedDraft?.item ?: preview.item,
                    previewSteps = translatedDraft?.steps ?: preview.steps,
                    survivor = preview.survivor,
                    mergeSources = preview.mergeSources,
                    group = group,
                    reason = group.reason,
                    operationKind = kind,
                    translatedDraft = translatedDraft,
                )
            }
            // Legacy items already targeted by a staged group show once, as the group entry.
            val legacyEntries = legacyItems
                .filter { it.savedItemId !in targeted }
                .mapNotNull { item ->
                    val key = "legacy_${item.savedItemId}"
                    val translation = ReviewTranslationState.fromJson(draftsByKey[key]?.translationStateJson)
                    val translationMatches = translation != null && translation.sourceOpIds.isEmpty() &&
                        translation.sourceItem.lastUpdateTimestamp == item.lastUpdateTimestamp
                    if (translation?.isPending == true && translationMatches) return@mapNotNull null
                    val baseItem = item
                    val baseSteps = db.todoStepDao().getBySavedItemId(item.savedItemId)
                    val translatedDraft = translation?.takeIf { translationMatches }?.translatedDraft?.let { translated ->
                        overlayTranslatedText(ReviewItemDraft(baseItem, baseSteps), translated)
                    }
                    ReviewEntry(
                        key = key,
                        preview = translatedDraft?.item ?: baseItem,
                        previewSteps = translatedDraft?.steps ?: baseSteps,
                        survivor = null,
                        mergeSources = emptyList(),
                        group = null,
                        reason = "",
                        operationKind = if (item.state == SavedItemState.New) ReviewOperationKind.Create else ReviewOperationKind.Update,
                        translatedDraft = translatedDraft,
                    )
                }
            groupEntries + legacyEntries
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun matchesFilter(entry: ReviewEntry, f: ReviewFilter): Boolean {
        val isTodo = entry.preview.itemType == SavedItemType.Todo
        val isNew = entry.isNewLike
        return when (f) {
            ReviewFilter.All -> true
            ReviewFilter.NewTodos -> isTodo && isNew
            ReviewFilter.UpdatedTodos -> isTodo && !isNew
            ReviewFilter.NewKeeps -> !isTodo && isNew
            ReviewFilter.UpdatedKeeps -> !isTodo && !isNew
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

    /** Snapshots this exact preview, hides it through Room state, and starts translation-only F. */
    fun translate(entry: ReviewEntry, targetLanguage: String, applyToFutureItems: Boolean) {
        viewModelScope.launch {
            if (applyToFutureItems) SharedPreferencesManager.targetExtractionLanguage = targetLanguage
            val evidence = if (entry.group != null) {
                val staged = entry.group.ops.flatMap { op ->
                    val ids = JSONArray(op.evidenceRecordIds)
                    buildList {
                        for (index in 0 until ids.length()) {
                            ids.optString(index).takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                }
                val target = entry.group.targetItemId?.let { repo.getLinkedRecordIds(it) }.orEmpty()
                val sources = entry.mergeSources.flatMap { source -> source.evidenceLinks.map { it.notiRecordId } }
                target + sources + staged
            } else {
                repo.getLinkedRecordIds(entry.preview.savedItemId)
            }
            val state = ReviewTranslationState.pending(
                targetLanguage = targetLanguage,
                source = ReviewItemDraft(entry.preview, entry.previewSteps),
                evidenceRecordIds = evidence,
                sourceOpIds = entry.group?.ops?.map { it.opId }.orEmpty(),
            )
            pendingProposedOpRepo.setReviewTranslation(entry.key, ReviewTranslationState.toJson(state))
            enqueueReviewTranslation(getApplication<Application>(), entry.key)
        }
    }

    private val _related = MutableStateFlow(RelatedState())
    val related: StateFlow<RelatedState> = _related

    data class RelatedState(
        val entryKey: String? = null,
        val isLoading: Boolean = false,
        val value: SavedItemRelatedNotificationsRepository.RelatedNotifications =
            SavedItemRelatedNotificationsRepository.RelatedNotifications.Empty,
    )

    fun loadRelated(entry: ReviewEntry) {
        if (_related.value.entryKey == entry.key) return
        viewModelScope.launch {
            _related.value = RelatedState(entry.key, isLoading = true)
            val value = try {
                val group = entry.group
                if (group != null) {
                    // Review shows the full eventual provenance: target links, merge-source links,
                    // and the exact new records cited by staged ops.
                    val stagedEvidence = group.ops.flatMap { op ->
                        val arr = JSONArray(op.evidenceRecordIds)
                        buildList { for (i in 0 until arr.length()) arr.optString(i).takeIf(String::isNotBlank)?.let(::add) }
                    }
                    val targetEvidence = group.targetItemId?.let { repo.getLinkedRecordIds(it) }.orEmpty()
                    val sourceEvidence = entry.mergeSources.flatMap { source ->
                        source.evidenceLinks.map { it.notiRecordId }
                    }
                    relatedRepo.getByRecordIds(targetEvidence + sourceEvidence + stagedEvidence)
                } else {
                    relatedRepo.getRelatedNotifications(entry.preview)
                }
            } catch (_: Throwable) {
                SavedItemRelatedNotificationsRepository.RelatedNotifications.Empty
            }
            _related.value = RelatedState(entry.key, isLoading = false, value = value)
        }
    }

    // ── Actions with undo ──

    private sealed interface UndoableAction {
        /** A staged group was applied; undo restores the pre-apply state and re-stages the ops. */
        data class ApplyGroup(val outcome: PendingProposedOpRepository.ApplyOutcome) : UndoableAction

        /** A staged group was discarded; undo re-inserts the ops. */
        data class DiscardGroup(
            val group: PendingProposedOpRepository.OpGroup,
            val reviewDraft: PendingReviewDraft?,
        ) : UndoableAction

        // Legacy (non-staged) items keep their old semantics.
        data class Approve(val item: SavedItem, val reviewDraft: PendingReviewDraft?) : UndoableAction
        data class SaveLegacy(
            val item: SavedItem,
            val steps: List<TodoStep>,
            val history: List<SavedItemChangeLog>,
            val reviewDraft: PendingReviewDraft?,
        ) : UndoableAction
        data class RejectNew(
            val item: SavedItem,
            val steps: List<TodoStep>,
            val reviewDraft: PendingReviewDraft?,
        ) : UndoableAction
        data class RejectUpdated(
            val outcome: SavedItemRepository.RevertOutcome,
            val reviewDraft: PendingReviewDraft?,
        ) : UndoableAction
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

    fun resetReviewSession() {
        _deferredKeys.value = emptySet()
    }

    fun approve(entry: ReviewEntry) {
        viewModelScope.launch {
            val group = entry.group
            if (group != null) {
                val outcome = pendingProposedOpRepo.applyGroup(
                    group = group,
                    editedDraft = entry.translatedDraft,
                    editedDraftIsUserEdit = false,
                ) ?: return@launch
                pendingUndo = UndoableAction.ApplyGroup(outcome)
            } else if (entry.translatedDraft != null) {
                val before = db.savedItemDao().getById(entry.preview.savedItemId) ?: return@launch
                val beforeSteps = db.todoStepDao().getBySavedItemId(before.savedItemId)
                val beforeHistory = db.savedItemChangeLogDao().getByItem(before.savedItemId)
                val reviewDraft = db.pendingReviewDraftDao().getByKey(entry.key)
                repo.saveReviewedDraft(
                    draft = entry.translatedDraft,
                    ts = System.currentTimeMillis(),
                    reviewKey = entry.key,
                    markAsUserEdit = false,
                )
                pendingUndo = UndoableAction.SaveLegacy(before, beforeSteps, beforeHistory, reviewDraft)
            } else {
                val before = db.savedItemDao().getById(entry.preview.savedItemId) ?: return@launch
                repo.acknowledgeReview(
                    entry.preview.savedItemId,
                    System.currentTimeMillis(),
                    entry.key,
                )
                pendingUndo = UndoableAction.Approve(before, db.pendingReviewDraftDao().getByKey(entry.key))
            }
            _snackbar.trySend(ReviewSnackbar(R.string.review_approved_toast, entry.preview, canTeach = false))
        }
    }

    /** Edit-in-review "Save & Approve": apply the staged group, then persist the user's edits on
     *  top (which shields them from later LLM updates). Editing implies acceptance. */
    fun saveApprove(entry: ReviewEntry, edited: ReviewItemDraft) {
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            val group = entry.group
            if (group != null) {
                val outcome = pendingProposedOpRepo.applyGroup(group, editedDraft = edited, now = ts) ?: return@launch
                pendingUndo = UndoableAction.ApplyGroup(outcome)
            } else {
                val before = db.savedItemDao().getById(entry.preview.savedItemId) ?: return@launch
                val beforeSteps = db.todoStepDao().getBySavedItemId(before.savedItemId)
                val beforeHistory = db.savedItemChangeLogDao().getByItem(before.savedItemId)
                val reviewDraft = db.pendingReviewDraftDao().getByKey(entry.key)
                repo.saveReviewedDraft(edited, ts, entry.key)
                pendingUndo = UndoableAction.SaveLegacy(before, beforeSteps, beforeHistory, reviewDraft)
            }
            _snackbar.trySend(ReviewSnackbar(R.string.review_approved_toast, edited.item, canTeach = false))
        }
    }

    fun reject(entry: ReviewEntry) {
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            val group = entry.group
            if (group != null) {
                val reviewDraft = pendingProposedOpRepo.discardGroup(group, ts)
                pendingUndo = UndoableAction.DiscardGroup(group, reviewDraft)
                _snackbar.trySend(ReviewSnackbar(R.string.review_rejected_toast, entry.preview, canTeach = true))
                return@launch
            }
            val item = db.savedItemDao().getById(entry.preview.savedItemId) ?: return@launch
            val reviewDraft = db.pendingReviewDraftDao().getByKey(entry.key)
            db.pendingReviewDraftDao().deleteByKey(entry.key)
            if (item.state == SavedItemState.Updated) {
                val outcome = repo.revertPendingLlmUpdates(item.savedItemId, ts)
                if (outcome != null) {
                    pendingUndo = UndoableAction.RejectUpdated(outcome, reviewDraft)
                    _snackbar.trySend(ReviewSnackbar(R.string.review_reverted_toast, item, canTeach = true))
                }
            } else {
                // Legacy new item → hard delete; capture rows so undo can restore them.
                val steps = db.todoStepDao().getBySavedItemId(item.savedItemId)
                repo.deleteById(item.savedItemId, ts)
                pendingUndo = UndoableAction.RejectNew(item, steps, reviewDraft)
                _snackbar.trySend(ReviewSnackbar(R.string.review_rejected_toast, item, canTeach = true))
            }
        }
    }

    fun undoLast() {
        val action = pendingUndo ?: return
        pendingUndo = null
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            when (action) {
                is UndoableAction.ApplyGroup -> pendingProposedOpRepo.undoApply(action.outcome, ts)
                is UndoableAction.DiscardGroup -> pendingProposedOpRepo.restoreDiscarded(action.group, action.reviewDraft)
                is UndoableAction.Approve -> {
                    repo.upsert(action.item)
                    action.reviewDraft?.let { db.pendingReviewDraftDao().upsert(it) }
                }
                is UndoableAction.SaveLegacy -> {
                    repo.upsert(action.item)
                    db.todoStepDao().hardDeleteByParentId(action.item.savedItemId)
                    if (action.steps.isNotEmpty()) db.todoStepDao().upsertAll(action.steps)
                    db.savedItemChangeLogDao().deleteByItem(action.item.savedItemId)
                    if (action.history.isNotEmpty()) db.savedItemChangeLogDao().upsertAll(action.history)
                    action.reviewDraft?.let { db.pendingReviewDraftDao().upsert(it) }
                }
                is UndoableAction.RejectNew -> {
                    repo.upsert(action.item)
                    if (action.item.isTodo && action.steps.isNotEmpty()) {
                        db.todoStepDao().upsertAll(action.steps)
                    }
                    action.reviewDraft?.let { db.pendingReviewDraftDao().upsert(it) }
                }
                is UndoableAction.RejectUpdated -> {
                    repo.undoRevert(action.outcome, ts)
                    action.reviewDraft?.let { db.pendingReviewDraftDao().upsert(it) }
                }
            }
        }
    }

    companion object {
        /** Applies translated strings to the latest local preview while retaining all structure/state. */
        internal fun overlayTranslatedText(base: ReviewItemDraft, translated: ReviewItemDraft): ReviewItemDraft {
            val translatedSteps = translated.steps.associateBy { it.todoStepId }
            return ReviewItemDraft(
                item = base.item.copy(
                    title = translated.item.title,
                    content = translated.item.content,
                    buttons = translated.item.buttons,
                ),
                steps = base.steps.mapIndexed { index, sub ->
                    val translatedText = translatedSteps[sub.todoStepId]?.text
                        ?: translated.steps.getOrNull(index)?.text
                        ?: sub.text
                    sub.copy(text = translatedText)
                },
            )
        }
    }
}
