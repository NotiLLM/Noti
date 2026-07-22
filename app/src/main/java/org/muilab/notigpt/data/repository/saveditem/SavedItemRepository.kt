package org.muilab.notigpt.data.repository.saveditem

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.muilab.notigpt.R
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.local.room.dao.SavedItemDao
import org.muilab.notigpt.model.features.ExtractionJournalEntry
import org.muilab.notigpt.model.features.ExtractionJournalEventType
import org.muilab.notigpt.model.features.FirestoreOutboxKind
import org.muilab.notigpt.model.features.FirestoreOutboxOp
import org.muilab.notigpt.model.features.NotiSavedItemLink
import org.muilab.notigpt.model.features.NotiSavedItemLinkRole
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.SavedItemChangeType
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.data.remote.firestore.FirestoreSyncRepository
import org.muilab.notigpt.data.remote.n8n.N8nOpParsing
import org.muilab.notigpt.domain.saveditem.SavedItemRevertLogic
import org.muilab.notigpt.domain.saveditem.SavedItemNormalization
import org.muilab.notigpt.model.features.TodoStep
import org.muilab.notigpt.model.features.ReviewItemDraft
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.work.FirestoreOutboxWork
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository for item CRUD, filtering queries, soft deletes, and export-oriented item operations.
 *
 * Keep item persistence rules here rather than in Compose screens. Sub-task persistence and notification
 * context live in adjacent repositories to keep item rows focused.
 */
class SavedItemRepository(
    private val savedItemDao: SavedItemDao,
    private val appContext: Context,
) {
    private val db = AppDatabase.getInstance(appContext.applicationContext)
    private val firestoreSync by lazy { FirestoreSyncRepository(appContext.applicationContext) }
    private val linkDao by lazy { db.notiSavedItemLinkDao() }
    private val journalRepo by lazy {
        ExtractionJournalRepository(db.extractionJournalDao())
    }
    private val changeLogDao by lazy { db.savedItemChangeLogDao() }
    private val todoStepDao by lazy { db.todoStepDao() }
    private val pendingProposedOpDao by lazy { db.pendingProposedOpDao() }
    private val rejectedMergeDao by lazy { db.rejectedMergeDao() }

    /** Returns visible savedItems in the canonical list order used by the savedItems screen. */
    fun observeAll(): Flow<List<SavedItem>> = savedItemDao.observeAll()
    fun observeTodos(): Flow<List<SavedItem>> = savedItemDao.observeTodos()
    fun observeKeeps(): Flow<List<SavedItem>> = savedItemDao.observeKeeps()
    fun observeActiveKeeps(): Flow<List<SavedItem>> = savedItemDao.observeActiveKeeps()
    fun observeArchivedKeeps(): Flow<List<SavedItem>> = savedItemDao.observeArchivedKeeps()
    fun observeCompletedTodos(): Flow<List<SavedItem>> = savedItemDao.observeCompletedTodos()
    fun observeNewItems(): Flow<List<SavedItem>> = savedItemDao.observeNewItems()

    suspend fun getAll(): List<SavedItem> = withContext(Dispatchers.IO) { savedItemDao.getAll() }

    /** Active (non-completed/archived) items — the merge stages' candidate pool. */
    suspend fun getAllActive(): List<SavedItem> = withContext(Dispatchers.IO) { savedItemDao.getAllActive() }

    /**
     * Upserts a SavedItem locally and mirrors the resulting row to Firestore.
     *
     * Callers should set edit timestamps and user-edit flags before calling this method so local and remote
     * copies share the same item semantics.
     */
    suspend fun upsert(item: SavedItem) = withContext(Dispatchers.IO) {
        // Editor saves (vs LLM handler upserts) carry origin="manual" + userEdited; record them in
        // the change history and the extraction journal so the pipeline knows the user took over.
        val old = savedItemDao.getById(item.savedItemId)
        val isEditorSave = item.userEdited && item.origin == "manual" &&
            old != null && (old.title != item.title || old.content != item.content ||
                old.deadlineAtMs != item.deadlineAtMs || old.itemType != item.itemType)

        val existingChildren = todoStepDao.getBySavedItemId(item.savedItemId)
        val normalized = when {
            old?.isTodo == true && !item.isTodo -> SavedItemNormalization.convertTodoToKeep(
                item.copy(
                    deadlineAtMs = item.deadlineAtMs.takeIf { it > 0L } ?: old.deadlineAtMs,
                ),
                existingChildren,
            )
            !item.isTodo -> SavedItemNormalization.normalize(item, existingChildren)
            else -> SavedItemNormalization.normalize(item, existingChildren)
        }
        db.withTransaction {
            savedItemDao.upsert(normalized.item)
            todoStepDao.hardDeleteByParentId(item.savedItemId)
            if (normalized.steps.isNotEmpty()) todoStepDao.upsertAll(normalized.steps)
        }

        if (isEditorSave) {
            val stored = normalized.item
            val changedFields = org.json.JSONObject().apply {
                if (old.title != stored.title) {
                    put("title", org.json.JSONObject().put("old", old.title).put("new", stored.title))
                }
                if (old.deadlineAtMs != stored.deadlineAtMs) {
                    put("deadlineAtMs", org.json.JSONObject().put("old", old.deadlineAtMs).put("new", stored.deadlineAtMs))
                }
            }
            changeLogDao.insert(
                SavedItemChangeLog(
                    savedItemId = stored.savedItemId,
                    createdAt = stored.lastUpdateTimestamp,
                    changeType = SavedItemChangeType.UserEdit,
                    changedFieldsJson = changedFields.toString(),
                    origin = "user",
                )
            )
            journalUserEvent(stored.savedItemId, ExtractionJournalEventType.UserEdited, stored.title)
        }

        queueAndSync(normalized.item, normalized.item.syncModifiedAt)
    }

    suspend fun upsertSubItem(subItem: TodoStep, ts: Long) = withContext(Dispatchers.IO) {
        val parent = savedItemDao.getById(subItem.parentSavedItemId) ?: return@withContext
        if (!parent.isTodo) return@withContext
        val normalizedText = TodoStep.normalizeText(subItem.text)
        val existing = todoStepDao.getById(subItem.todoStepId)
        val position = existing?.position ?: todoStepDao.nextPosition(parent.savedItemId)
        todoStepDao.upsert(subItem.copy(text = normalizedText, position = position))
        val updated = parent.copy(lastUpdateTimestamp = ts, syncModifiedAt = ts)
        savedItemDao.upsert(updated)
        queueAndSync(updated, ts)
    }

    suspend fun deleteSubItem(todoStepId: String, ts: Long) = withContext(Dispatchers.IO) {
        val child = todoStepDao.getById(todoStepId) ?: return@withContext
        val parent = savedItemDao.getById(child.parentSavedItemId) ?: return@withContext
        todoStepDao.hardDeleteById(todoStepId)
        val updated = parent.copy(lastUpdateTimestamp = ts, syncModifiedAt = ts)
        savedItemDao.upsert(updated)
        queueAndSync(updated, ts)
    }

    suspend fun setSubItemCompleted(todoStepId: String, completed: Boolean, ts: Long) = withContext(Dispatchers.IO) {
        val child = todoStepDao.getById(todoStepId) ?: return@withContext
        val parent = savedItemDao.getById(child.parentSavedItemId) ?: return@withContext
        todoStepDao.setCompleted(todoStepId, completed)
        val updated = parent.copy(lastUpdateTimestamp = ts, syncModifiedAt = ts)
        savedItemDao.upsert(updated)
        queueAndSync(updated, ts)
    }

    /**
     * User deletion is a hard delete: the row and its sub-items go away for good (links and
     * change logs cascade via FK). The journal entry is written first — it's the only trace the
     * pipeline keeps, so future extractions know the user discarded this item. The Firestore
     * mirror is marked deleted rather than removed, preserving the research record.
     */
    suspend fun deleteById(savedItemId: String, ts: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            journalUserEvent(savedItemId, ExtractionJournalEventType.UserDeleted)
            queueSavedItem(savedItemId, FirestoreOutboxKind.DeleteSavedItem, ts)
            todoStepDao.hardDeleteByParentId(savedItemId)
            savedItemDao.hardDeleteById(savedItemId)
            // Staged ops against a gone item are unreviewable; merge cool-downs are moot too.
            pendingProposedOpDao.deleteByTargetItemId(savedItemId)
            db.pendingReviewDraftDao().deleteByKey("item_$savedItemId")
            db.pendingReviewDraftDao().deleteByKey("legacy_$savedItemId")
            rejectedMergeDao.deleteForItem(savedItemId)
        }
        FirestoreOutboxWork.enqueue(appContext)
        firestoreSync.markSavedItemDeleted(savedItemId, ts)
    }

    suspend fun setCompleted(savedItemId: String, completed: Boolean, ts: Long) = withContext(Dispatchers.IO) {
        savedItemDao.setCompleted(savedItemId, completed, ts)
        if (completed) journalUserEvent(savedItemId, ExtractionJournalEventType.UserCompleted)

        val updated = savedItemDao.getById(savedItemId) ?: return@withContext
        queueAndSync(updated, ts)
    }

    suspend fun setState(savedItemId: String, state: String, ts: Long) = withContext(Dispatchers.IO) {
        val old = savedItemDao.getById(savedItemId)
        savedItemDao.setState(savedItemId, state, ts)
        when {
            state == SavedItemState.Archived -> journalUserEvent(savedItemId, ExtractionJournalEventType.UserArchived)
            // Un-archiving a keep is a "this matters again" signal for future extractions.
            old?.state == SavedItemState.Archived && state == SavedItemState.Saved ->
                journalUserEvent(savedItemId, ExtractionJournalEventType.ItemRestored)
        }
        val updated = savedItemDao.getById(savedItemId) ?: return@withContext
        queueAndSync(updated, ts)
    }

    suspend fun markSavedByIds(savedItemIds: List<String>, ts: Long) = withContext(Dispatchers.IO) {
        if (savedItemIds.isEmpty()) return@withContext
        savedItemDao.markSavedByIds(savedItemIds, ts)
        savedItemIds.mapNotNull { savedItemDao.getById(it) }.forEach { queueAndSync(it, ts) }
    }

    suspend fun deleteByIds(savedItemIds: List<String>, ts: Long) = withContext(Dispatchers.IO) {
        if (savedItemIds.isEmpty()) return@withContext
        db.withTransaction {
            savedItemIds.forEach {
                journalUserEvent(it, ExtractionJournalEventType.UserDeleted)
                queueSavedItem(it, FirestoreOutboxKind.DeleteSavedItem, ts)
            }
            todoStepDao.hardDeleteByParentIds(savedItemIds)
            savedItemDao.hardDeleteByIds(savedItemIds)
            savedItemIds.forEach {
                pendingProposedOpDao.deleteByTargetItemId(it)
                db.pendingReviewDraftDao().deleteByKey("item_$it")
                db.pendingReviewDraftDao().deleteByKey("legacy_$it")
                rejectedMergeDao.deleteForItem(it)
            }
        }
        FirestoreOutboxWork.enqueue(appContext)
        savedItemIds.forEach { firestoreSync.markSavedItemDeleted(it, ts) }
    }

    private suspend fun queueSavedItem(savedItemId: String, kind: String, ts: Long) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        db.firestoreOutboxDao().upsert(FirestoreOutboxOp.savedItem(uid, kind, savedItemId, ts))
    }

    private suspend fun queueAndSync(item: SavedItem, ts: Long = item.syncModifiedAt) {
        queueSavedItem(item.savedItemId, FirestoreOutboxKind.UpsertSavedItem, ts)
        FirestoreOutboxWork.enqueue(appContext)
        // Fast path; the outbox row remains durable until the worker independently confirms it.
        firestoreSync.syncSavedItem(item)
    }

    /**
     * Journals a user action on [savedItemId] into every notiUnit the item is linked to, so future
     * extractions from those threads know the user already handled it. No-op for unlinked items.
     */
    private suspend fun journalUserEvent(savedItemId: String, eventType: String, titleOverride: String? = null) {
        try {
            val item = savedItemDao.getById(savedItemId)
            val title = titleOverride ?: item?.title ?: return
            val keys = linkDao.getBySavedItemId(savedItemId).map { it.notiKey }.distinct()
            val now = System.currentTimeMillis()
            keys.forEach { key ->
                journalRepo.append(
                    ExtractionJournalEntry(
                        notiKey = key,
                        createdAt = now,
                        eventType = eventType,
                        savedItemId = savedItemId,
                        itemTitle = title,
                    )
                )
            }
        } catch (_: Exception) {
            // Journaling is best-effort; never block or fail a user action over it.
        }
    }

    suspend fun getById(savedItemId: String): SavedItem? = withContext(Dispatchers.IO) { savedItemDao.getById(savedItemId) }

    suspend fun setViewed(savedItemId: String) = withContext(Dispatchers.IO) {
        savedItemDao.setViewed(savedItemId)
        val updated = savedItemDao.getById(savedItemId) ?: return@withContext
        queueAndSync(updated)
    }

    suspend fun setStarred(savedItemId: String, starred: Boolean, ts: Long) = withContext(Dispatchers.IO) {
        savedItemDao.setStarred(savedItemId, starred, ts)
        val updated = savedItemDao.getById(savedItemId) ?: return@withContext
        queueAndSync(updated, ts)
    }

    /** [deadlineAtMs] = 0 clears the deadline. */
    suspend fun setDeadline(savedItemId: String, deadlineAtMs: Long, ts: Long) = withContext(Dispatchers.IO) {
        savedItemDao.setDeadline(savedItemId, deadlineAtMs, ts)
        val updated = savedItemDao.getById(savedItemId) ?: return@withContext
        queueAndSync(updated, ts)
    }

    /** Explicit user acknowledgment of a New/Updated item (the review "got it" action). */
    suspend fun acknowledgeReview(
        savedItemId: String,
        ts: Long,
        reviewKey: String? = null,
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            savedItemDao.acknowledgeReview(savedItemId, ts)
            if (reviewKey != null) db.pendingReviewDraftDao().deleteByKey(reviewKey)
        }
        val updated = savedItemDao.getById(savedItemId) ?: return@withContext
        queueAndSync(updated, ts)
    }

    /** Batch acknowledgment for "approve all" in the review screen. */
    suspend fun acknowledgeReviewByIds(savedItemIds: List<String>, ts: Long) = withContext(Dispatchers.IO) {
        if (savedItemIds.isEmpty()) return@withContext
        savedItemDao.acknowledgeReviewByIds(savedItemIds, ts)
        savedItemIds.mapNotNull { savedItemDao.getById(it) }.forEach { queueAndSync(it, ts) }
    }

    /** Persists the complete editor draft for a legacy review row in one local transaction. */
    suspend fun saveReviewedDraft(
        draft: ReviewItemDraft,
        ts: Long,
        reviewKey: String? = null,
        markAsUserEdit: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        val existing = savedItemDao.getById(draft.item.savedItemId) ?: return@withContext
        val existingChildren = todoStepDao.getBySavedItemId(existing.savedItemId)
        val contentEdited = existing.title != draft.item.title || existing.content != draft.item.content ||
            existing.itemType != draft.item.itemType ||
            existing.deadlineAtMs != draft.item.deadlineAtMs ||
            existingChildren.map { TodoStep.normalizeText(it.text) to it.isCompleted } !=
                draft.steps.map { TodoStep.normalizeText(it.text) to it.isCompleted }
        val normalized = SavedItemNormalization.normalize(
            draft.item.copy(
                state = if (draft.item.isTodo && draft.item.isCompleted) SavedItemState.Completed else SavedItemState.Saved,
                origin = if (markAsUserEdit && contentEdited) "manual" else existing.origin,
                humanEditCount = existing.humanEditCount + if (markAsUserEdit && contentEdited) 1 else 0,
                userEdited = existing.userEdited || (markAsUserEdit && contentEdited),
                lastUpdateTimestamp = if (contentEdited) ts else existing.lastUpdateTimestamp,
                syncModifiedAt = ts,
                lastViewedChangeAt = ts,
                isViewed = true,
            ),
            draft.steps.mapIndexed { index, child ->
                child.copy(parentSavedItemId = existing.savedItemId, position = index)
            },
        )
        db.withTransaction {
            savedItemDao.upsert(normalized.item)
            todoStepDao.hardDeleteByParentId(existing.savedItemId)
            if (normalized.steps.isNotEmpty()) todoStepDao.upsertAll(normalized.steps)
            if (markAsUserEdit && contentEdited) {
                changeLogDao.insert(
                    SavedItemChangeLog(
                        savedItemId = existing.savedItemId,
                        createdAt = ts,
                        changeType = SavedItemChangeType.UserEdit,
                        origin = "user",
                    )
                )
            }
            if (reviewKey != null) db.pendingReviewDraftDao().deleteByKey(reviewKey)
        }
        queueAndSync(normalized.item, ts)
    }

    // ========== Reject-an-update (revert) ==========

    /**
     * What a [revertPendingLlmUpdates] call changed, carried back so the caller can offer Undo.
     *
     * [previousItem] is the item exactly as it was before the revert (re-upsert to undo the field
     * and content rollback). Child rows are captured directly because steps now use hard deletes.
     */
    data class RevertOutcome(
        val previousItem: SavedItem,
        val deletedAddedSteps: List<TodoStep>,
        val restoredRemovedSteps: List<TodoStep>,
        val revertChangeId: Long,
    )

    /**
     * Rejects the pending LLM edits on an "updated" item, rolling it back to how it was before the
     * model touched it, and returns to the `saved` state.
     *
     * Only unacknowledged `llm_update` rows (createdAt > lastViewedChangeAt) are reverted, newest
     * first so stacked appended sections strip off the end in order. User edits and regenerations in
     * the window are left alone; each field is only rolled back if its current value still equals
     * what that LLM row set (so a later user edit — or a hallucinated `old` echo — is never clobbered).
     *
     * Returns null if the item no longer exists. If there is nothing pending to revert, the item is
     * simply acknowledged (equivalent to approve) and an outcome with no step changes is returned.
     */
    suspend fun revertPendingLlmUpdates(savedItemId: String, ts: Long): RevertOutcome? = withContext(Dispatchers.IO) {
        val item = savedItemDao.getById(savedItemId) ?: return@withContext null
        val pending = changeLogDao.getNewerThan(savedItemId, item.lastViewedChangeAt)
            .filter { it.origin == "llm" && it.changeType == SavedItemChangeType.LlmUpdate }

        var content = item.content
        var title = item.title
        var deadline = item.deadlineAtMs
        val deletedAddedSteps = mutableListOf<TodoStep>()
        val restoredRemovedSteps = mutableListOf<TodoStep>()

        // getNewerThan returns newest-first (ORDER BY createdAt DESC), which is the order we need.
        for (row in pending) {
            if (row.appendedContent.isNotBlank()) {
                stripUpdateSection(content, row.appendedContent, row.createdAt)?.let { content = it }
            }

            val changed = row.changedFieldsJson.toJsonObjectOrEmpty()
            changed.optJSONObject("title")?.let { obj ->
                title = SavedItemRevertLogic.revertField(title, obj.optString("new"), obj.optString("old", title))
            }
            changed.optJSONObject("deadlineTimeString")?.let { obj ->
                deadline = SavedItemRevertLogic.revertField(
                    deadline, deadlineMsFromIso(obj.optString("new", "-1")), deadlineMsFromIso(obj.optString("old", "-1")),
                )
            }
            parseStepIds(row.addedStepsJson).forEach { id ->
                todoStepDao.getById(id)?.let(deletedAddedSteps::add)
                todoStepDao.hardDeleteById(id)
            }
            val removed = parseSteps(row.removedStepsJson, savedItemId)
            if (removed.isNotEmpty() && item.isTodo) {
                todoStepDao.upsertAll(removed)
                restoredRemovedSteps += removed
            }
        }

        val restored = item.copy(
            title = title,
            content = content,
            state = SavedItemState.Saved,
            deadlineAtMs = deadline,
            lastViewedChangeAt = ts,
            lastUpdateTimestamp = ts,
            syncModifiedAt = ts,
        )
        savedItemDao.upsert(restored)

        val revertChangeId = changeLogDao.insert(
            SavedItemChangeLog(
                savedItemId = savedItemId,
                createdAt = ts,
                changeType = SavedItemChangeType.Reverted,
                changeSummary = "",
                origin = "user",
            )
        )
        // Tell future extraction passes the user rejected these edits so they aren't re-applied.
        journalUserEvent(savedItemId, ExtractionJournalEventType.UserRevertedUpdate, restored.title)
        queueAndSync(restored, ts)

        RevertOutcome(
            previousItem = item,
            deletedAddedSteps = deletedAddedSteps,
            restoredRemovedSteps = restoredRemovedSteps,
            revertChangeId = revertChangeId,
        )
    }

    /** Undoes a [revertPendingLlmUpdates], restoring the captured child rows. */
    suspend fun undoRevert(outcome: RevertOutcome, ts: Long) = withContext(Dispatchers.IO) {
        val restored = outcome.previousItem.copy(syncModifiedAt = ts)
        savedItemDao.upsert(restored)
        if (outcome.deletedAddedSteps.isNotEmpty()) todoStepDao.upsertAll(outcome.deletedAddedSteps)
        if (outcome.restoredRemovedSteps.isNotEmpty()) {
            todoStepDao.hardDeleteByIds(outcome.restoredRemovedSteps.map { it.todoStepId })
        }
        changeLogDao.deleteById(outcome.revertChangeId)
        queueAndSync(restored, ts)
    }

    private fun deadlineMsFromIso(iso: String): Long = N8nOpParsing.isoToUnixMillis(iso)

    private fun String.toJsonObjectOrEmpty(): JSONObject =
        try { JSONObject(this) } catch (_: Exception) { JSONObject() }

    private fun parseStepIds(json: String): List<String> = try {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("todoStepId", obj.optString("stepId"))
                if (id.isNotBlank()) add(id)
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun parseSteps(json: String, parentId: String): List<TodoStep> = try {
        val arr = JSONArray(json)
        buildList {
            for (index in 0 until arr.length()) {
                val obj = arr.optJSONObject(index) ?: continue
                val id = obj.optString("todoStepId", obj.optString("stepId"))
                val text = TodoStep.normalizeText(obj.optString("text", obj.optString("title")))
                if (id.isBlank() || text.isBlank()) continue
                add(
                    TodoStep(
                        todoStepId = id,
                        parentSavedItemId = parentId,
                        text = text,
                        isCompleted = obj.optBoolean("isCompleted", false),
                        position = obj.optInt("position", index),
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    // ========== Noti <-> saved-item links ==========

    /**
     * Adds evidence links from [savedItemId] to each record in [recordIds] without touching existing rows.
     *
     * Links are add-only provenance: a record already linked (same role) is silently skipped via the
     * unique index, and links from earlier extractions are never displaced by later responses.
     * [type] mirrors the owning saved item's itemType ("todo"/"keep"); [source] labels the flow that
     * produced the citation ([NotiSavedItemLinkSource]).
     */
    suspend fun addEvidenceLinks(
        savedItemId: String,
        recordIds: Collection<String>,
        type: String,
        source: String,
        role: String = NotiSavedItemLinkRole.Evidence,
    ): List<Long> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val links = recordIds
            .filter { it.isNotBlank() }
            .distinct()
            .map { recordId ->
                NotiSavedItemLink(
                    notiKey = recordId.substringBeforeLast("_"),
                    notiRecordId = recordId,
                    type = type,
                    savedItemId = savedItemId,
                    role = role,
                    source = source,
                    createdAt = now,
                )
            }
        if (links.isNotEmpty()) linkDao.insertAll(links) else emptyList()
    }

    /** Record ids linked to [savedItemId], in no particular order. */
    suspend fun getLinkedRecordIds(savedItemId: String): List<String> = withContext(Dispatchers.IO) {
        linkDao.getBySavedItemId(savedItemId).map { it.notiRecordId }.distinct()
    }

    /** Distinct notification group keys linked to [savedItemId]. */
    suspend fun getLinkedKeys(savedItemId: String): List<String> = withContext(Dispatchers.IO) {
        linkDao.getBySavedItemId(savedItemId).map { it.notiKey }.distinct()
    }

    /** Map of savedItemId -> its linked record ids, for batch payload building. */
    suspend fun getLinkedRecordIdsFor(savedItemIds: List<String>): Map<String, List<String>> = withContext(Dispatchers.IO) {
        if (savedItemIds.isEmpty()) return@withContext emptyMap()
        linkDao.getBySavedItemIds(savedItemIds)
            .groupBy { it.savedItemId }
            .mapValues { (_, links) -> links.map { it.notiRecordId }.distinct() }
    }

    suspend fun updateButtons(savedItemId: String, buttons: String, ts: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        savedItemDao.updateButtons(savedItemId, buttons, ts)
        val updated = savedItemDao.getById(savedItemId) ?: return@withContext
        queueAndSync(updated)
    }

    /**
     * Formats an LLM append-fragment as a timestamped section to concatenate below existing content.
     *
     * The header label follows the user's target extraction language when set (so it matches the
     * content language the LLM writes in), falling back to the app locale's string resource.
     * Content is append-only by design: users have already read what's there, so updates arrive as
     * dated sections instead of rewrites.
     */
    fun buildUpdateSection(fragment: String, ts: Long): String {
        val label = when (SharedPreferencesManager.targetExtractionLanguage) {
            "en" -> "Update"
            "zh-TW" -> "更新"
            else -> appContext.getString(R.string.saved_item_update_section_label)
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
        return "\n\n[$label — $time]\n$fragment"
    }

    /**
     * Inverse of [buildUpdateSection]: removes the dated section a given LLM [fragment] appended, so a
     * rejected update leaves [content] as it was before.
     *
     * Prefers stripping the exact section [buildUpdateSection] would rebuild for ([fragment], [ts]).
     * That can miss when the language preference or timezone changed since the append, so it falls
     * back to locating the fragment and stripping back to its `"\n\n["` header. Returns null when the
     * fragment can't be found at all (the user edited it away) — the caller then leaves content alone.
     */
    fun stripUpdateSection(content: String, fragment: String, ts: Long): String? =
        SavedItemRevertLogic.stripUpdateSection(content, fragment, buildUpdateSection(fragment, ts))
}
