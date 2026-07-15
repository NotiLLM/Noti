package org.muilab.notigpt.data.repository.reminder

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.remote.firestore.FirestoreSyncRepository
import org.muilab.notigpt.data.remote.n8n.N8nOpParsing
import org.muilab.notigpt.domain.reminder.ReminderAssociationMerger
import org.muilab.notigpt.model.features.ExtractionJournalEntry
import org.muilab.notigpt.model.features.ExtractionJournalEventType
import org.muilab.notigpt.model.features.NotiSavedItemLinkSource
import org.muilab.notigpt.model.features.PendingOp
import org.muilab.notigpt.model.features.PendingOpType
import org.muilab.notigpt.model.features.RejectedMerge
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.SavedItemChangeType
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedSubItem
import java.util.UUID

/**
 * The staged-review core: pipeline ops land here as [PendingOp] rows, previews are computed
 * in-memory, and only user approval turns an op group into real saved-item writes.
 *
 * Grouping is item-level — users review "one eventual item" per card, not atomic instructions:
 * each create op is its own group; every update/merge op targeting the same existing item shares
 * one group. Accepting applies the whole group; rejecting discards it (recording merge cool-downs
 * so D-stages stop re-proposing the pair for a while).
 */
class PendingOpRepository(private val appContext: Context) {

    private val db = AppDatabase.getInstance(appContext.applicationContext)
    private val pendingOpDao = db.pendingOpDao()
    private val rejectedMergeDao = db.rejectedMergeDao()
    private val savedItemDao = db.reminderListDao()
    private val subTaskDao = db.subTaskDao()
    private val changeLogDao = db.savedItemChangeLogDao()
    private val journalDao = db.extractionJournalDao()
    private val itemRepo by lazy { SavedItemRepository(savedItemDao, appContext.applicationContext) }
    private val journalRepo by lazy { ExtractionJournalRepository(journalDao) }
    private val firestoreSync by lazy { FirestoreSyncRepository(appContext.applicationContext) }

    fun observePending(): Flow<List<PendingOp>> = pendingOpDao.observeAll()

    suspend fun getPending(): List<PendingOp> = withContext(Dispatchers.IO) { pendingOpDao.getAll() }

    /** Item ids with unreviewed staged ops against them — excluded from merge-stage inputs. */
    suspend fun getTargetedItemIds(): Set<String> = withContext(Dispatchers.IO) {
        pendingOpDao.getTargetedItemIds().toSet()
    }

    /** Pairs still inside the merge-rejection cool-down window. */
    suspend fun getActiveMergeCooldowns(
        now: Long = System.currentTimeMillis(),
        cooldownMs: Long = RejectedMerge.DEFAULT_COOLDOWN_MS,
    ): List<RejectedMerge> = withContext(Dispatchers.IO) {
        rejectedMergeDao.purgeOlderThan(now - cooldownMs)
        rejectedMergeDao.getActiveSince(now - cooldownMs)
    }

    // ========== Staging ==========

    /**
     * Stages extraction (B) create/update ops behind the evidence gate: ops citing no record id
     * we actually sent are dropped. Returns the staged rows (with generated opIds).
     */
    suspend fun stageExtractionOps(
        notiKey: String,
        batchId: String,
        ops: JSONArray,
        validRecordIds: Set<String>,
        now: Long = System.currentTimeMillis(),
    ): List<PendingOp> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<PendingOp>()
        for (i in 0 until ops.length()) {
            val op = ops.optJSONObject(i) ?: continue
            val cited = ReminderAssociationMerger.evidenceIdsFrom(op)
            val evidence = ReminderAssociationMerger.filterValidEvidence(cited, validRecordIds)
            if (evidence.isEmpty()) continue // uncited claims never reach the database
            when (op.optString("op", "create")) {
                "update" -> {
                    val targetId = N8nOpParsing.savedItemIdFrom(op)
                    val target = savedItemDao.getById(targetId) ?: continue
                    rows += PendingOp(
                        notiKey = notiKey,
                        opType = PendingOpType.Update,
                        payload = op.toString(),
                        targetItemId = targetId,
                        evidenceRecordIds = JSONArray(evidence.toList()).toString(),
                        reason = reasonFrom(op),
                        itemType = target.itemType,
                        batchId = batchId,
                        createdAt = now,
                    )
                }
                else -> rows += PendingOp(
                    notiKey = notiKey,
                    opType = PendingOpType.Create,
                    payload = op.toString(),
                    evidenceRecordIds = JSONArray(evidence.toList()).toString(),
                    reason = reasonFrom(op),
                    itemType = if (op.optBoolean("isTask", true)) SavedItemType.Task else SavedItemType.Keep,
                    batchId = batchId,
                    createdAt = now,
                )
            }
        }
        if (rows.isEmpty()) return@withContext emptyList()
        val ids = pendingOpDao.insertAll(rows)
        rows.mapIndexed { idx, row -> row.copy(opId = ids[idx]) }
    }

    /**
     * Stages merge ops from E1/E2. An E1 op carrying `newOpRef` consumes a staged create: the
     * create row is deleted and its evidence rides the merge op instead ("this new content folds
     * into an existing item" is one reviewable decision, not two).
     *
     * [createsByRef] maps the opRef labels sent to E1 to the staged create rows.
     */
    suspend fun stageMergeOps(
        batchId: String,
        ops: JSONArray,
        createsByRef: Map<String, PendingOp> = emptyMap(),
        now: Long = System.currentTimeMillis(),
    ): List<PendingOp> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<PendingOp>()
        val consumedCreateIds = mutableListOf<Long>()
        for (i in 0 until ops.length()) {
            val op = ops.optJSONObject(i) ?: continue
            val targetId = op.optString("targetItemId", N8nOpParsing.savedItemIdFrom(op))
            if (targetId.isBlank()) continue
            val target = savedItemDao.getById(targetId) ?: continue
            val consumed = createsByRef[op.optString("newOpRef")]
            val sourceIds = op.optJSONArray("sourceItemIds") ?: JSONArray()
            // Sources must be real, distinct items; the survivor can't merge into itself.
            val validSources = buildList {
                for (j in 0 until sourceIds.length()) {
                    val id = sourceIds.optString(j)
                    if (id.isNotBlank() && id != targetId && savedItemDao.getById(id) != null) add(id)
                }
            }.distinct()
            if (consumed == null && validSources.isEmpty() && !op.has("changes")) continue

            rows += PendingOp(
                notiKey = consumed?.notiKey ?: "",
                opType = PendingOpType.Merge,
                payload = op.toString(),
                targetItemId = targetId,
                mergeSourceItemIds = JSONArray(validSources).toString(),
                evidenceRecordIds = consumed?.evidenceRecordIds ?: "[]",
                reason = reasonFrom(op),
                itemType = target.itemType,
                batchId = batchId,
                createdAt = now,
            )
            consumed?.let { consumedCreateIds += it.opId }
        }
        if (consumedCreateIds.isNotEmpty()) pendingOpDao.deleteByIds(consumedCreateIds)
        if (rows.isEmpty()) return@withContext emptyList()
        val ids = pendingOpDao.insertAll(rows)
        rows.mapIndexed { idx, row -> row.copy(opId = ids[idx]) }
    }

    // ========== Item-level grouping & preview ==========

    /** One reviewable unit: a would-be item (create) or an existing item with staged changes. */
    data class OpGroup(
        val key: String,
        val ops: List<PendingOp>,
        /** Null for creates — the item doesn't exist yet. */
        val targetItemId: String?,
    ) {
        val isCreate: Boolean get() = targetItemId == null
        val itemType: String get() = ops.first().itemType
        val reason: String get() = ops.mapNotNull { it.reason.takeIf(String::isNotBlank) }.joinToString("\n")
    }

    /** Groups pending ops item-level: one group per create op, one per targeted existing item. */
    fun groupOps(ops: List<PendingOp>): List<OpGroup> {
        val groups = mutableListOf<OpGroup>()
        ops.filter { it.opType == PendingOpType.Create }.forEach { op ->
            groups += OpGroup(key = "create_${op.opId}", ops = listOf(op), targetItemId = null)
        }
        ops.filter { it.opType != PendingOpType.Create }
            .groupBy { it.targetItemId }
            .forEach { (targetId, targetOps) ->
                groups += OpGroup(key = "item_$targetId", ops = targetOps.sortedBy { it.opId }, targetItemId = targetId)
            }
        return groups.sortedBy { it.ops.first().createdAt }
    }

    data class Preview(val item: SavedItem, val subItems: List<SavedSubItem>)

    /**
     * Computes what the group would produce, without touching the database: creates render the
     * would-be item; updates/merges render the current item with the staged changes applied in
     * memory. Returns null when an update group's target vanished (the ops should be purged).
     */
    suspend fun buildPreview(group: OpGroup, now: Long = System.currentTimeMillis()): Preview? = withContext(Dispatchers.IO) {
        if (group.isCreate) {
            val op = JSONObject(group.ops.first().payload)
            val previewId = "pending_${group.ops.first().opId}"
            val item = itemFromCreateOp(op, previewId, now).copy(state = SavedItemState.New)
            val subs = N8nOpParsing.parseSubTasks(op.optJSONArray("subTasks"), previewId, now, baseSortOrder = 0)
            return@withContext Preview(item, subs)
        }
        val current = savedItemDao.getById(group.targetItemId!!) ?: return@withContext null
        var item = current
        val existingSubs = subTaskDao.getByReminderId(current.savedItemId)
        val subs = existingSubs.toMutableList()
        group.ops.forEach { pending ->
            val op = JSONObject(pending.payload)
            val changes = op.optJSONObject("changes") ?: JSONObject()
            item = applyChangesInMemory(item, changes, now)
            subs += N8nOpParsing.parseSubTasks(changes.optJSONArray("addedSubTasks"), current.savedItemId, now, baseSortOrder = subs.size)
            removedSubTaskIds(changes).forEach { removedId -> subs.removeAll { it.savedSubItemId == removedId } }
        }
        Preview(item.copy(state = SavedItemState.Updated, lastUpdateTimestamp = now), subs)
    }

    // ========== Apply (accept) ==========

    /** Everything needed to undo an accepted group. Held in memory by the review screen's snackbar. */
    data class ApplyOutcome(
        val ops: List<PendingOp>,
        val createdItemId: String?,
        val beforeTarget: SavedItem?,
        val deletedSourceItems: List<SavedItem>,
        val deletedSourceSubItems: List<SavedSubItem>,
        val addedSubItemIds: List<String>,
        val hiddenSubItemIds: List<String>,
        val changeLogIds: List<Long>,
        val appliedItemId: String,
    )

    /**
     * Applies an accepted group to the database and deletes its op rows. Accepted items land in
     * the `saved` state directly — review acceptance *is* the acknowledgment.
     */
    suspend fun applyGroup(group: OpGroup, now: Long = System.currentTimeMillis()): ApplyOutcome? = withContext(Dispatchers.IO) {
        val outcome = if (group.isCreate) applyCreate(group, now) else applyOnTarget(group, now)
        if (outcome != null) pendingOpDao.deleteByIds(group.ops.map { it.opId })
        outcome
    }

    private suspend fun applyCreate(group: OpGroup, now: Long): ApplyOutcome {
        val pending = group.ops.first()
        val op = JSONObject(pending.payload)
        val itemId = "r_" + UUID.randomUUID().toString().take(8)
        val item = itemFromCreateOp(op, itemId, now)
        savedItemDao.upsert(item)

        val subs = N8nOpParsing.parseSubTasks(op.optJSONArray("subTasks"), itemId, now, baseSortOrder = 0)
        if (subs.isNotEmpty()) subTaskDao.upsertAll(subs)

        val evidence = evidenceOf(pending)
        itemRepo.addEvidenceLinks(itemId, evidence, item.itemType, NotiSavedItemLinkSource.LlmAutoExtraction)

        val changeLogId = changeLogDao.insert(
            SavedItemChangeLog(
                savedItemId = itemId,
                createdAt = now,
                changeType = SavedItemChangeType.Created,
                changeSummary = reasonFrom(op),
                evidenceRecordIdsJson = pending.evidenceRecordIds,
                origin = "llm",
            )
        )
        journalAccepted(evidence, pending.notiKey, ExtractionJournalEventType.ItemCreated, itemId, item.title, reasonFrom(op), now)
        firestoreSync.syncReminder(item)

        return ApplyOutcome(
            ops = group.ops,
            createdItemId = itemId,
            beforeTarget = null,
            deletedSourceItems = emptyList(),
            deletedSourceSubItems = emptyList(),
            addedSubItemIds = subs.map { it.savedSubItemId },
            hiddenSubItemIds = emptyList(),
            changeLogIds = listOf(changeLogId),
            appliedItemId = itemId,
        )
    }

    private suspend fun applyOnTarget(group: OpGroup, now: Long): ApplyOutcome? {
        val targetId = group.targetItemId!!
        val before = savedItemDao.getById(targetId) ?: return null

        var item = before
        val addedSubItemIds = mutableListOf<String>()
        val hiddenSubItemIds = mutableListOf<String>()
        val changeLogIds = mutableListOf<Long>()
        val deletedItems = mutableListOf<SavedItem>()
        val deletedSubs = mutableListOf<SavedSubItem>()

        group.ops.forEach { pending ->
            val op = JSONObject(pending.payload)
            val changes = op.optJSONObject("changes") ?: JSONObject()
            item = applyChangesInMemory(item, changes, now)
            op.optJSONArray("buttons")?.let { item = item.copy(buttons = it.toString()) }

            val added = N8nOpParsing.parseSubTasks(
                changes.optJSONArray("addedSubTasks"), targetId, now,
                baseSortOrder = subTaskDao.getByReminderId(targetId).size,
            )
            if (added.isNotEmpty()) {
                subTaskDao.upsertAll(added)
                addedSubItemIds += added.map { it.savedSubItemId }
            }
            val removedIds = removedSubTaskIds(changes)
            if (removedIds.isNotEmpty()) {
                removedIds.forEach { subTaskDao.softDeleteById(it, now) }
                hiddenSubItemIds += removedIds
            }

            // Merge: fold the sources away for good (links + change logs cascade; sub-items explicit).
            mergeSourceIdsOf(pending).forEach { sourceId ->
                val source = savedItemDao.getById(sourceId) ?: return@forEach
                deletedItems += source
                deletedSubs += subTaskDao.getByReminderId(sourceId)
                subTaskDao.hardDeleteByParentId(sourceId)
                savedItemDao.hardDeleteById(sourceId)
                rejectedMergeDao.deleteForItem(sourceId)
                firestoreSync.markReminderDeleted(sourceId, now)
            }

            val evidence = evidenceOf(pending)
            if (evidence.isNotEmpty()) {
                itemRepo.addEvidenceLinks(targetId, evidence, before.itemType, NotiSavedItemLinkSource.LlmAutoExtraction)
            }
            changeLogIds += changeLogDao.insert(
                SavedItemChangeLog(
                    savedItemId = targetId,
                    createdAt = now,
                    changeType = SavedItemChangeType.LlmUpdate,
                    changeSummary = changes.optString("changeSummary", pending.reason),
                    appendedContent = changes.optString("appendedContent", "").trim(),
                    changedFieldsJson = (changes.optJSONObject("changedFields") ?: JSONObject()).toString(),
                    evidenceRecordIdsJson = pending.evidenceRecordIds,
                    origin = "llm",
                )
            )
            journalAccepted(evidence, pending.notiKey, ExtractionJournalEventType.ItemUpdated, targetId, item.title, pending.reason, now)
        }

        // Acceptance is the acknowledgment: land in `saved` with the change cursor moved.
        val applied = item.copy(
            state = if (before.isCompleted || before.isArchived) before.state else SavedItemState.Saved,
            isViewed = true,
            lastViewedChangeAt = now,
            lastUpdateTimestamp = now,
        )
        savedItemDao.upsert(applied)
        firestoreSync.syncReminder(applied)

        return ApplyOutcome(
            ops = group.ops,
            createdItemId = null,
            beforeTarget = before,
            deletedSourceItems = deletedItems,
            deletedSourceSubItems = deletedSubs,
            addedSubItemIds = addedSubItemIds,
            hiddenSubItemIds = hiddenSubItemIds,
            changeLogIds = changeLogIds,
            appliedItemId = targetId,
        )
    }

    /** Reverses an [applyGroup]: restores the pre-apply state and re-stages the ops. */
    suspend fun undoApply(outcome: ApplyOutcome, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        outcome.createdItemId?.let { id ->
            subTaskDao.hardDeleteByParentId(id)
            savedItemDao.hardDeleteById(id)
            firestoreSync.markReminderDeleted(id, now)
        }
        outcome.beforeTarget?.let { before ->
            savedItemDao.upsert(before)
            // Update-added sub-items go away; hidden ones come back.
            if (outcome.addedSubItemIds.isNotEmpty()) subTaskDao.hardDeleteByIds(outcome.addedSubItemIds)
            if (outcome.hiddenSubItemIds.isNotEmpty()) subTaskDao.restoreByIds(outcome.hiddenSubItemIds, now)
            firestoreSync.syncReminder(before)
        }
        outcome.deletedSourceItems.forEach { savedItemDao.upsert(it) }
        if (outcome.deletedSourceSubItems.isNotEmpty()) subTaskDao.upsertAll(outcome.deletedSourceSubItems)
        outcome.deletedSourceItems.forEach { firestoreSync.syncReminder(it) }
        outcome.changeLogIds.forEach { changeLogDao.deleteById(it) }
        pendingOpDao.insertAll(outcome.ops.map { it.copy(opId = 0L) })
    }

    // ========== Discard (reject) ==========

    /**
     * Rejects a group: op rows are deleted (nothing was ever applied), the verdict is journaled so
     * later extraction runs know, and rejected merges enter the cool-down table.
     */
    suspend fun discardGroup(group: OpGroup, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        group.ops.forEach { pending ->
            val keys = journalKeysOf(pending)
            val title = when {
                group.isCreate -> N8nOpParsing.titleFrom(JSONObject(pending.payload))
                else -> savedItemDao.getById(pending.targetItemId)?.title ?: ""
            }
            keys.forEach { key ->
                journalRepo.append(
                    ExtractionJournalEntry(
                        notiKey = key,
                        createdAt = now,
                        eventType = ExtractionJournalEventType.UserRejectedProposal,
                        savedItemId = pending.targetItemId,
                        itemTitle = title,
                        detail = pending.reason.take(200),
                    )
                )
            }
            if (pending.opType == PendingOpType.Merge) {
                val ids = (mergeSourceIdsOf(pending) + pending.targetItemId).filter { it.isNotBlank() }.distinct()
                val pairs = buildList {
                    for (a in ids.indices) for (b in a + 1 until ids.size) add(RejectedMerge.of(ids[a], ids[b], now))
                }
                if (pairs.isNotEmpty()) rejectedMergeDao.upsertAll(pairs)
            }
        }
        pendingOpDao.deleteByIds(group.ops.map { it.opId })
    }

    /** Undo of a reject: the ops come back exactly as they were (fresh row ids). */
    suspend fun restoreDiscarded(group: OpGroup) = withContext(Dispatchers.IO) {
        pendingOpDao.insertAll(group.ops.map { it.copy(opId = 0L) })
        // The cool-down entries a rejected merge wrote are left in place; re-rejecting would
        // recreate them anyway and an accepted merge deletes them via deleteForItem.
    }

    /** Drops staged ops whose target item no longer exists (deleted out from under review). */
    suspend fun purgeOrphanedOps() = withContext(Dispatchers.IO) {
        pendingOpDao.getAll()
            .filter { it.targetItemId.isNotBlank() && savedItemDao.getById(it.targetItemId) == null }
            .map { it.opId }
            .takeIf { it.isNotEmpty() }
            ?.let { pendingOpDao.deleteByIds(it) }
    }

    // ========== Helpers ==========

    private fun itemFromCreateOp(op: JSONObject, itemId: String, now: Long): SavedItem {
        val isTask = op.optBoolean("isTask", op.optString("itemType", SavedItemType.Task) == SavedItemType.Task)
        return SavedItem(
            savedItemId = itemId,
            title = N8nOpParsing.titleFrom(op),
            content = N8nOpParsing.contentFrom(op),
            itemType = if (isTask) SavedItemType.Task else SavedItemType.Keep,
            state = SavedItemState.Saved,
            lastUpdateTimestamp = now,
            deadlineAtMs = N8nOpParsing.isoToUnixMillis(op.optString("deadlineTimeString", "-1")),
            startAtMs = N8nOpParsing.startAtMsFrom(op),
            endAtMs = N8nOpParsing.endAtMsFrom(op),
            origin = op.optString("origin", "llm_auto_extraction"),
            humanEditCount = 0,
            userEdited = false,
            buttons = op.optJSONArray("buttons")?.toString() ?: "[]",
            isViewed = true,
            lastViewedChangeAt = now,
        )
    }

    /**
     * Field-level change application, shared by preview and apply. Content is append-only (the
     * user has already read what's there); title and the short time fields may be replaced.
     * doAtMs/isStarred are user-owned and never touched.
     */
    private fun applyChangesInMemory(item: SavedItem, changes: JSONObject, now: Long): SavedItem {
        val fragment = changes.optString("appendedContent", "").trim()
        val content = if (fragment.isNotBlank()) item.content + itemRepo.buildUpdateSection(fragment, now) else item.content
        val changedFields = changes.optJSONObject("changedFields") ?: JSONObject()
        val title = changedFields.optJSONObject("title")?.optString("new")?.takeIf { it.isNotBlank() } ?: item.title
        val deadline = changedFields.optJSONObject("deadlineTimeString")
            ?.let { N8nOpParsing.isoToUnixMillis(it.optString("new", "-1")) } ?: item.deadlineAtMs
        val start = changedFields.optJSONObject("startTimeString")
            ?.let { N8nOpParsing.isoToUnixMillis(it.optString("new", "-1")).let { v -> if (v == -1L) 0L else v } } ?: item.startAtMs
        val end = changedFields.optJSONObject("endTimeString")
            ?.let { N8nOpParsing.isoToUnixMillis(it.optString("new", "-1")).let { v -> if (v == -1L) 0L else v } } ?: item.endAtMs
        return item.copy(title = title, content = content, deadlineAtMs = deadline, startAtMs = start, endAtMs = end)
    }

    private fun removedSubTaskIds(changes: JSONObject): List<String> {
        val arr = changes.optJSONArray("removedSubTasks") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val id = r.optString("subTaskId", r.optString("savedSubItemId"))
                if (id.isNotBlank()) add(id)
            }
        }
    }

    private fun mergeSourceIdsOf(pending: PendingOp): List<String> = try {
        val arr = JSONArray(pending.mergeSourceItemIds)
        buildList { for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(::add) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun evidenceOf(pending: PendingOp): Set<String> = try {
        val arr = JSONArray(pending.evidenceRecordIds)
        buildSet { for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(::add) }
    } catch (_: Exception) {
        emptySet()
    }

    /** Threads to journal a verdict to: the evidence keys, falling back to the op's own notiKey. */
    private fun journalKeysOf(pending: PendingOp): List<String> {
        val fromEvidence = evidenceOf(pending).map { it.substringBeforeLast("_") }.distinct()
        return fromEvidence.ifEmpty { listOfNotNull(pending.notiKey.takeIf { it.isNotBlank() }) }
    }

    private suspend fun journalAccepted(
        evidence: Set<String>,
        fallbackKey: String,
        eventType: String,
        savedItemId: String,
        itemTitle: String,
        detail: String,
        now: Long,
    ) {
        val keys = evidence.map { it.substringBeforeLast("_") }.distinct()
            .ifEmpty { listOfNotNull(fallbackKey.takeIf { it.isNotBlank() }) }
        keys.forEach { key ->
            try {
                journalRepo.append(
                    ExtractionJournalEntry(
                        notiKey = key,
                        createdAt = now,
                        eventType = eventType,
                        savedItemId = savedItemId,
                        itemTitle = itemTitle,
                        detail = detail.take(200),
                    )
                )
            } catch (_: Exception) {
                // Journaling is best-effort; never fail an accept over it.
            }
        }
    }

    private fun reasonFrom(op: JSONObject): String =
        op.optString("reason", op.optString("changeSummary", op.optJSONObject("changes")?.optString("changeSummary") ?: ""))
}
