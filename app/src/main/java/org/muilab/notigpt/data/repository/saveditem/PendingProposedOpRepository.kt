package org.muilab.notigpt.data.repository.saveditem

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.remote.firestore.FirestoreSyncRepository
import org.muilab.notigpt.data.remote.n8n.N8nOpParsing
import org.muilab.notigpt.domain.saveditem.SavedItemAssociationMerger
import org.muilab.notigpt.domain.saveditem.SavedItemMergePolicy
import org.muilab.notigpt.domain.saveditem.SavedItemNormalization
import org.muilab.notigpt.domain.saveditem.ReviewMergeSemantics
import org.muilab.notigpt.model.features.ExtractionJournalEntry
import org.muilab.notigpt.model.features.ExtractionJournalEventType
import org.muilab.notigpt.model.features.FirestoreOutboxKind
import org.muilab.notigpt.model.features.FirestoreOutboxOp
import org.muilab.notigpt.model.features.ProposedOpRecord
import org.muilab.notigpt.model.features.ProposedOpRecordDecision
import org.muilab.notigpt.model.features.NotiSavedItemLinkSource
import org.muilab.notigpt.model.features.NotiSavedItemLink
import org.muilab.notigpt.model.features.PendingProposedOp
import org.muilab.notigpt.model.features.PendingProposedOpType
import org.muilab.notigpt.model.features.RejectedMerge
import org.muilab.notigpt.model.features.Reminder
import org.muilab.notigpt.model.features.ReminderSavedItemRef
import org.muilab.notigpt.model.features.ReminderStatus
import org.muilab.notigpt.data.repository.reminder.ReminderScheduler
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.SavedItemChangeType
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.TodoStep
import org.muilab.notigpt.model.features.ReviewItemDraft
import org.muilab.notigpt.model.features.PendingReviewDraft
import java.util.UUID
import org.muilab.notigpt.work.FirestoreOutboxWork
import org.muilab.notigpt.work.ReflectionTrigger

/**
 * The staged-review core: pipeline ops land here as [PendingProposedOp] rows, previews are computed
 * in-memory, and only user approval turns an op group into real saved-item writes.
 *
 * Grouping is item-level — users review "one eventual item" per card, not atomic instructions:
 * each create op is its own group; every update/merge op targeting the same existing item shares
 * one group. Accepting applies the whole group; rejecting discards it (recording merge cool-downs
 * so D-stages stop re-proposing the pair for a while).
 */
class PendingProposedOpRepository(private val appContext: Context) {

    private val db = AppDatabase.getInstance(appContext.applicationContext)
    private val pendingProposedOpDao = db.pendingProposedOpDao()
    private val rejectedMergeDao = db.rejectedMergeDao()
    private val savedItemDao = db.savedItemDao()
    private val todoStepDao = db.todoStepDao()
    private val changeLogDao = db.savedItemChangeLogDao()
    private val journalDao = db.extractionJournalDao()
    private val itemRepo by lazy { SavedItemRepository(savedItemDao, appContext.applicationContext) }
    private val journalRepo by lazy { ExtractionJournalRepository(journalDao) }
    private val firestoreSync by lazy { FirestoreSyncRepository(appContext.applicationContext) }

    fun observePending(): Flow<List<PendingProposedOp>> = pendingProposedOpDao.observeAll()

    fun observeReviewDrafts(): Flow<List<PendingReviewDraft>> = db.pendingReviewDraftDao().observeAll()

    suspend fun setReviewTranslation(
        reviewKey: String,
        translationStateJson: String?,
        now: Long = System.currentTimeMillis(),
    ) = withContext(Dispatchers.IO) {
        val current = db.pendingReviewDraftDao().getByKey(reviewKey)
        db.pendingReviewDraftDao().upsert(
            current?.copy(translationStateJson = translationStateJson, updatedAt = now)
                ?: PendingReviewDraft(
                    reviewKey = reviewKey,
                    translationStateJson = translationStateJson,
                    updatedAt = now,
                ),
        )
    }

    suspend fun setSplitBatchDraft(
        reviewKey: String,
        children: List<ReviewItemDraft>,
        now: Long = System.currentTimeMillis(),
    ) = withContext(Dispatchers.IO) {
        val current = db.pendingReviewDraftDao().getByKey(reviewKey)
        db.pendingReviewDraftDao().upsert(
            current?.copy(batchDraftJson = splitDraftToJson(children), updatedAt = now)
                ?: PendingReviewDraft(
                    reviewKey = reviewKey,
                    batchDraftJson = splitDraftToJson(children),
                    updatedAt = now,
                )
        )
    }

    suspend fun getPending(): List<PendingProposedOp> = withContext(Dispatchers.IO) { pendingProposedOpDao.getAll() }

    suspend fun getPendingForTarget(savedItemId: String): List<PendingProposedOp> = withContext(Dispatchers.IO) {
        pendingProposedOpDao.getByTargetItemId(savedItemId)
    }

    /** Claims the source before network work begins, enforcing one transform per item. */
    suspend fun beginTransform(
        sourceItemId: String,
        type: String,
        now: Long = System.currentTimeMillis(),
    ): PendingProposedOp? = withContext(Dispatchers.IO) {
        require(type == PendingProposedOpType.Split || type == PendingProposedOpType.Regenerate)
        val source = savedItemDao.getById(sourceItemId) ?: return@withContext null
        if (source.isCompleted || source.isArchived) return@withContext null
        val row = PendingProposedOp(
            opType = type,
            payload = JSONObject().put("status", "processing")
                .put("sourceVersion", source.lastUpdateTimestamp)
                .put("sourceSavedItemId", sourceItemId)
                .toString(),
            targetItemId = sourceItemId,
            itemType = source.itemType,
            batchId = "transform_${UUID.randomUUID()}",
            createdAt = now,
        )
        db.withTransaction {
            if (pendingProposedOpDao.getByTargetItemId(sourceItemId).isNotEmpty()) return@withTransaction null
            val id = pendingProposedOpDao.insertAll(listOf(row)).single()
            savedItemDao.upsert(source.copy(
                pendingTransformType = type,
                pendingTransformStatus = "processing",
                syncModifiedAt = now,
            ))
            queueSavedItem(sourceItemId, FirestoreOutboxKind.UpsertSavedItem, now)
            row.copy(opId = id).also { persistProposedOpRecords(listOf(it)) }
        }.also { if (it != null) FirestoreOutboxWork.enqueue(appContext) }
    }

    suspend fun clearProcessingTransform(sourceItemId: String, type: String) = withContext(Dispatchers.IO) {
        val processing = pendingProposedOpDao.getByTargetItemId(sourceItemId).filter {
            it.opType == type && runCatching {
                JSONObject(it.payload).optString("status") == "processing"
            }.getOrDefault(false)
        }
        if (processing.isNotEmpty()) pendingProposedOpDao.deleteByIds(processing.map { it.opId })
        savedItemDao.getById(sourceItemId)?.takeIf { it.pendingTransformType == type }?.let { item ->
            val now = System.currentTimeMillis()
            savedItemDao.upsert(item.copy(
                pendingTransformType = "",
                pendingTransformStatus = "",
                syncModifiedAt = now,
            ))
            queueSavedItem(sourceItemId, FirestoreOutboxKind.UpsertSavedItem, now)
            FirestoreOutboxWork.enqueue(appContext)
        }
    }

    /** Item ids with unreviewed staged ops against them — excluded from merge-stage inputs. */
    suspend fun getTargetedItemIds(): Set<String> = withContext(Dispatchers.IO) {
        pendingProposedOpDao.getTargetedItemIds().toSet()
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
    ): List<PendingProposedOp> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<PendingProposedOp>()
        for (i in 0 until ops.length()) {
            val op = ops.optJSONObject(i) ?: continue
            val cited = SavedItemAssociationMerger.evidenceIdsFrom(op)
            val evidence = SavedItemAssociationMerger.filterValidEvidence(cited, validRecordIds)
            if (evidence.isEmpty()) continue // uncited claims never reach the database
            when (op.optString("op", "create")) {
                "update" -> {
                    val targetId = N8nOpParsing.savedItemIdFrom(op)
                    val target = savedItemDao.getById(targetId) ?: continue
                    rows += PendingProposedOp(
                        notiKey = notiKey,
                        opType = PendingProposedOpType.Update,
                        payload = op.toString(),
                        targetItemId = targetId,
                        evidenceRecordIds = JSONArray(evidence.toList()).toString(),
                        reason = reasonFrom(op),
                        itemType = target.itemType,
                        batchId = batchId,
                        createdAt = now,
                    )
                }
                else -> rows += PendingProposedOp(
                    notiKey = notiKey,
                    opType = PendingProposedOpType.Create,
                    payload = op.toString(),
                    evidenceRecordIds = JSONArray(evidence.toList()).toString(),
                    reason = reasonFrom(op),
                    itemType = if (op.optString("itemType") == SavedItemType.Keep) SavedItemType.Keep else SavedItemType.Todo,
                    batchId = batchId,
                    createdAt = now,
                )
            }
        }
        if (rows.isEmpty()) return@withContext emptyList()
        val staged = db.withTransaction {
            val ids = pendingProposedOpDao.insertAll(rows)
            rows.mapIndexed { idx, row -> row.copy(opId = ids[idx]) }
                .also { persistProposedOpRecords(it) }
        }
        FirestoreOutboxWork.enqueue(appContext)
        staged
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
        createsByRef: Map<String, PendingProposedOp> = emptyMap(),
        now: Long = System.currentTimeMillis(),
    ): List<PendingProposedOp> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<PendingProposedOp>()
        val consumedCreateIds = mutableListOf<Long>()
        for (i in 0 until ops.length()) {
            val op = ops.optJSONObject(i) ?: continue
            val targetId = op.optString("targetItemId", N8nOpParsing.savedItemIdFrom(op))
            if (targetId.isBlank()) continue
            val target = savedItemDao.getById(targetId) ?: continue
            val consumed = createsByRef[op.optString("newOpRef")]
            if (consumed != null && consumed.itemType != target.itemType) continue
            val sourceIds = op.optJSONArray("sourceItemIds") ?: JSONArray()
            // Sources must be real, distinct items; the survivor can't merge into itself.
            val sourceItems = buildList {
                for (j in 0 until sourceIds.length()) {
                    val id = sourceIds.optString(j)
                    if (id.isBlank() || id == targetId) continue
                    savedItemDao.getById(id)?.let(::add)
                }
            }.distinctBy(SavedItem::savedItemId)
            if (sourceItems.any { it.itemType != target.itemType }) continue
            if (SavedItemMergePolicy.preservedUserState(listOf(target) + sourceItems) == null) continue
            val validSources = sourceItems.map(SavedItem::savedItemId)
            if (consumed == null && validSources.isEmpty() && !op.has("changes")) continue

            rows += PendingProposedOp(
                notiKey = consumed?.notiKey ?: "",
                opType = PendingProposedOpType.Merge,
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
        if (rows.isEmpty()) return@withContext emptyList()
        val staged = db.withTransaction {
            if (consumedCreateIds.isNotEmpty()) {
                pendingProposedOpDao.deleteByIds(consumedCreateIds)
                setProposalDecision(consumedCreateIds, ProposedOpRecordDecision.Superseded, now)
            }
            val ids = pendingProposedOpDao.insertAll(rows)
            rows.mapIndexed { idx, row -> row.copy(opId = ids[idx]) }
                .also { persistProposedOpRecords(it) }
        }
        FirestoreOutboxWork.enqueue(appContext)
        staged
    }

    /**
     * Stages a user-requested structural transformation. The source row remains the durable
     * truth until review approval; the payload is only a proposal. A source can have at most one
     * pending transformation at a time.
     */
    suspend fun stageTransform(
        sourceItemId: String,
        type: String,
        result: JSONObject,
        batchId: String = "transform_${UUID.randomUUID()}",
        now: Long = System.currentTimeMillis(),
    ): PendingProposedOp? = withContext(Dispatchers.IO) {
        require(type == PendingProposedOpType.Split || type == PendingProposedOpType.Regenerate)
        val source = savedItemDao.getById(sourceItemId) ?: return@withContext null
        if (source.isCompleted || source.isArchived) return@withContext null
        val existing = pendingProposedOpDao.getByTargetItemId(sourceItemId)
        val processing = existing.singleOrNull()?.takeIf {
            it.opType == type && runCatching {
                JSONObject(it.payload).optString("status") == "processing"
            }.getOrDefault(false)
        }
        if (existing.isNotEmpty() && processing == null) return@withContext null

        val payload = JSONObject(result.toString()).apply {
            put("sourceSavedItemId", sourceItemId)
            if (!has("sourceVersion")) put("sourceVersion", source.lastUpdateTimestamp)
        }
        if (type == PendingProposedOpType.Split) {
            val children = payload.optJSONArray("children") ?: return@withContext null
            if (children.length() < 2) return@withContext null
        }
        val row = PendingProposedOp(
            opId = processing?.opId ?: 0L,
            opType = type,
            payload = payload.toString(),
            targetItemId = sourceItemId,
            evidenceRecordIds = payload.optJSONArray("evidenceRecordIds")?.toString() ?: "[]",
            reason = reasonFrom(payload),
            itemType = source.itemType,
            batchId = processing?.batchId ?: batchId,
            createdAt = now,
        )
        val staged = db.withTransaction {
            if (processing != null) {
                val current = pendingProposedOpDao.getByIds(listOf(processing.opId)).singleOrNull()
                    ?: return@withTransaction null
                if (runCatching { JSONObject(current.payload).optString("status") }.getOrDefault("") != "processing") {
                    return@withTransaction null
                }
                pendingProposedOpDao.update(row)
                savedItemDao.upsert(source.copy(
                    pendingTransformType = type,
                    pendingTransformStatus = "review",
                    syncModifiedAt = now,
                ))
                queueSavedItem(sourceItemId, FirestoreOutboxKind.UpsertSavedItem, now)
                persistProposedOpRecords(listOf(row))
                row
            } else {
                // Re-check inside the transaction in case two workers completed together.
                if (pendingProposedOpDao.getByTargetItemId(sourceItemId).isNotEmpty()) return@withTransaction null
                val id = pendingProposedOpDao.insertAll(listOf(row)).single()
                savedItemDao.upsert(source.copy(
                    pendingTransformType = type,
                    pendingTransformStatus = "review",
                    syncModifiedAt = now,
                ))
                queueSavedItem(sourceItemId, FirestoreOutboxKind.UpsertSavedItem, now)
                row.copy(opId = id).also { persistProposedOpRecords(listOf(it)) }
            }
        }
        if (staged != null) FirestoreOutboxWork.enqueue(appContext)
        staged
    }

    /**
     * Stages one E2 result set whose members are expressed as reflection candidate references.
     * Source pending groups are deliberately squashed: their evidence rides the replacement op,
     * their rows disappear from review, and rejecting the eventual target group therefore returns
     * to the last user-approved SavedItem state.
     */
    suspend fun stageReflectionOps(
        batchId: String,
        ops: JSONArray,
        pendingGroupsByRef: Map<String, OpGroup>,
        savedItemIdsByRef: Map<String, String>,
        now: Long = System.currentTimeMillis(),
    ): List<PendingProposedOp> = withContext(Dispatchers.IO) {
        data class Prepared(
            val row: PendingProposedOp,
            val consumed: List<PendingProposedOp>,
        )

        val prepared = mutableListOf<Prepared>()
        val globallyConsumed = mutableSetOf<Long>()
        for (i in 0 until ops.length()) {
            val op = ops.optJSONObject(i) ?: continue
            val targetRef = op.optString("targetCandidateRef")
            val sourceRefs = op.optJSONArray("sourceCandidateRefs")?.let { arr ->
                buildList { for (j in 0 until arr.length()) arr.optString(j).takeIf(String::isNotBlank)?.let(::add) }
            }.orEmpty().distinct()
            val allRefs = (listOf(targetRef) + sourceRefs).filter(String::isNotBlank).distinct()
            if (allRefs.size < 2) continue

            when (op.optString("op")) {
                "consolidate_create" -> {
                    val groups = allRefs.mapNotNull(pendingGroupsByRef::get)
                    if (groups.size != allRefs.size || groups.any { !it.isCreate }) continue
                    val consumed = groups.flatMap { it.ops }.distinctBy(PendingProposedOp::opId)
                    if (consumed.any { it.opId in globallyConsumed }) continue
                    val types = consumed.map(PendingProposedOp::itemType).distinct()
                    if (types.size != 1) continue
                    val evidence = consumed.flatMap(::evidenceOf).toSet()
                    val payload = JSONObject(op.toString()).apply {
                        put("op", "create")
                        put("reviewOperationKind", "merge")
                        put("evidenceRecordIds", JSONArray(evidence.toList()))
                    }
                    prepared += Prepared(
                        row = PendingProposedOp(
                            notiKey = consumed.firstNotNullOfOrNull { it.notiKey.takeIf(String::isNotBlank) }.orEmpty(),
                            opType = PendingProposedOpType.Create,
                            payload = payload.toString(),
                            evidenceRecordIds = JSONArray(evidence.toList()).toString(),
                            reason = reasonFrom(payload),
                            itemType = types.single(),
                            batchId = batchId,
                            createdAt = now,
                        ),
                        consumed = consumed,
                    )
                    globallyConsumed.addAll(consumed.map(PendingProposedOp::opId))
                }

                "merge" -> {
                    val targetGroup = pendingGroupsByRef[targetRef]
                    if (targetGroup?.isCreate == true) continue
                    val targetId = targetGroup?.targetItemId ?: savedItemIdsByRef[targetRef] ?: continue
                    val target = savedItemDao.getById(targetId) ?: continue
                    val sourceGroups = sourceRefs.mapNotNull(pendingGroupsByRef::get)
                    val consumed = sourceGroups.flatMap { it.ops }.distinctBy(PendingProposedOp::opId)
                    if (consumed.any { it.opId in globallyConsumed }) continue

                    val sourceIds = buildList {
                        sourceRefs.mapNotNull(savedItemIdsByRef::get).forEach(::add)
                        sourceGroups.forEach { group ->
                            group.targetItemId?.takeIf { it != targetId }?.let(::add)
                            group.ops.flatMap(::mergeSourceIdsOf).filter { it != targetId }.forEach(::add)
                        }
                        val explicit = op.optJSONArray("sourceItemIds")
                        if (explicit != null) for (j in 0 until explicit.length()) {
                            explicit.optString(j).takeIf { it.isNotBlank() && it != targetId }?.let(::add)
                        }
                    }.distinct()
                    val sourceItems = sourceIds.mapNotNull { savedItemDao.getById(it) }
                    if (sourceItems.size != sourceIds.size || sourceItems.any { it.itemType != target.itemType }) continue
                    if (consumed.any { it.itemType != target.itemType }) continue
                    if (SavedItemMergePolicy.preservedUserState(listOf(target) + sourceItems) == null) continue

                    val evidence = consumed.flatMap(::evidenceOf).toMutableSet().apply {
                        addAll(SavedItemAssociationMerger.evidenceIdsFrom(op))
                    }
                    val payload = JSONObject(op.toString()).apply {
                        put("targetItemId", targetId)
                        put("sourceItemIds", JSONArray(sourceIds))
                        put("evidenceRecordIds", JSONArray(evidence.toList()))
                    }
                    prepared += Prepared(
                        row = PendingProposedOp(
                            notiKey = consumed.firstNotNullOfOrNull { it.notiKey.takeIf(String::isNotBlank) }.orEmpty(),
                            opType = PendingProposedOpType.Merge,
                            payload = payload.toString(),
                            targetItemId = targetId,
                            mergeSourceItemIds = JSONArray(sourceIds).toString(),
                            evidenceRecordIds = JSONArray(evidence.toList()).toString(),
                            reason = reasonFrom(payload),
                            itemType = target.itemType,
                            batchId = batchId,
                            createdAt = now,
                        ),
                        consumed = consumed,
                    )
                    globallyConsumed.addAll(consumed.map(PendingProposedOp::opId))
                }
            }
        }
        if (prepared.isEmpty()) return@withContext emptyList()

        val staged = db.withTransaction {
            // Re-check that every source is still pending before consuming it; a review action may
            // have raced the network request while E2 was running.
            val consumedIds = prepared.flatMap { it.consumed }.map(PendingProposedOp::opId).distinct()
            val stillPending = if (consumedIds.isEmpty()) emptySet() else pendingProposedOpDao.getByIds(consumedIds)
                .map(PendingProposedOp::opId).toSet()
            if (stillPending.size != consumedIds.size) return@withTransaction emptyList()

            prepared.flatMap { it.consumed }.map { it.opId }.distinct().takeIf { it.isNotEmpty() }?.let { ids ->
                pendingProposedOpDao.deleteByIds(ids)
                setProposalDecision(ids, ProposedOpRecordDecision.Superseded, now)
            }
            prepared.flatMap { it.consumed }.map { op ->
                if (op.targetItemId.isBlank()) "create_${op.opId}" else "item_${op.targetItemId}"
            }.distinct().forEach { db.pendingReviewDraftDao().deleteByKey(it) }

            val rows = prepared.map(Prepared::row)
            val ids = pendingProposedOpDao.insertAll(rows)
            val inserted = rows.mapIndexed { index, row -> row.copy(opId = ids[index]) }
            persistProposedOpRecords(inserted)
            inserted
        }
        if (staged.isNotEmpty()) FirestoreOutboxWork.enqueue(appContext)
        staged
    }

    // ========== Item-level grouping & preview ==========

    /** One reviewable unit: a would-be item (create) or an existing item with staged changes. */
    data class OpGroup(
        val key: String,
        val ops: List<PendingProposedOp>,
        /** Null for creates — the item doesn't exist yet. */
        val targetItemId: String?,
    ) {
        val isCreate: Boolean get() = targetItemId == null
        val itemType: String get() = ops.first().itemType
        val reason: String get() = ops.mapNotNull { it.reason.takeIf(String::isNotBlank) }.joinToString("\n")
    }

    /** Groups pending ops item-level: one group per create op, one per targeted existing item. */
    fun groupOps(ops: List<PendingProposedOp>): List<OpGroup> {
        val groups = mutableListOf<OpGroup>()
        ops.filter { it.opType == PendingProposedOpType.Create }.forEach { op ->
            groups += OpGroup(key = "create_${op.opId}", ops = listOf(op), targetItemId = null)
        }
        ops.filter { it.opType != PendingProposedOpType.Create }
            .groupBy { it.targetItemId }
            .forEach { (targetId, targetOps) ->
                groups += OpGroup(key = "item_$targetId", ops = targetOps.sortedBy { it.opId }, targetItemId = targetId)
            }
        return groups.sortedBy { it.ops.first().createdAt }
    }

    data class MergeSourceSnapshot(
        val item: SavedItem,
        val steps: List<TodoStep>,
        val history: List<SavedItemChangeLog>,
        val evidenceLinks: List<NotiSavedItemLink>,
    )

    data class Preview(
        val item: SavedItem,
        val steps: List<TodoStep>,
        val survivor: MergeSourceSnapshot? = null,
        val mergeSources: List<MergeSourceSnapshot> = emptyList(),
        /** All proposed results for a Split; [item]/[steps] mirror the first child for legacy UI. */
        val splitChildren: List<ReviewItemDraft> = emptyList(),
        val sourceVersionIsCurrent: Boolean = true,
        val isProcessing: Boolean = false,
    )

    /**
     * Computes what the group would produce, without touching the database: creates render the
     * would-be item; updates/merges render the current item with the staged changes applied in
     * memory. Returns null when an update group's target vanished (the ops should be purged).
     */
    suspend fun buildPreview(
        group: OpGroup,
        now: Long = System.currentTimeMillis(),
    ): Preview? = withContext(Dispatchers.IO) {
        if (group.isCreate) {
            val op = JSONObject(group.ops.first().payload)
            val previewId = "pending_${group.ops.first().opId}"
            var item = itemFromCreateOp(op, previewId, now).copy(state = SavedItemState.New)
            val subs = N8nOpParsing.parseSteps(op.optJSONArray("steps"), previewId, now, baseSortOrder = 0)
            item = item.copy(buttons = SavedItemNormalization.mergeButtons(item.buttons, N8nOpParsing.childButtons(op.optJSONArray("steps"))))
            val normalized = SavedItemNormalization.normalize(item, subs)
            return@withContext Preview(normalized.item, normalized.steps)
        }
        val current = savedItemDao.getById(group.targetItemId!!) ?: return@withContext null
        val structural = group.ops.singleOrNull()
        if (structural != null && runCatching {
                JSONObject(structural.payload).optString("status") == "processing"
            }.getOrDefault(false)
        ) {
            val steps = todoStepDao.getBySavedItemId(current.savedItemId)
            return@withContext Preview(
                item = current,
                steps = steps,
                survivor = MergeSourceSnapshot(
                    current,
                    steps,
                    changeLogDao.getByItem(current.savedItemId),
                    db.notiSavedItemLinkDao().getBySavedItemId(current.savedItemId),
                ),
                isProcessing = true,
            )
        }
        if (structural?.opType == PendingProposedOpType.Split) {
            val payload = JSONObject(structural.payload)
            val children = payload.optJSONArray("children") ?: return@withContext null
            val generatedPreviews = buildList {
                for (index in 0 until children.length()) {
                    val child = children.optJSONObject(index) ?: continue
                    val childId = "pending_${structural.opId}_$index"
                    var item = itemFromCreateOp(child, childId, now).copy(
                        state = SavedItemState.New,
                        isStarred = current.isStarred,
                    )
                    val steps = N8nOpParsing.parseSteps(child.optJSONArray("steps"), childId, now, 0)
                    item = item.copy(
                        buttons = SavedItemNormalization.mergeButtons(
                            item.buttons,
                            N8nOpParsing.childButtons(child.optJSONArray("steps")),
                        )
                    )
                    val normalized = SavedItemNormalization.normalize(item, steps)
                    add(ReviewItemDraft(normalized.item, normalized.steps))
                }
            }
            val previews = db.pendingReviewDraftDao().getByKey(group.key)?.batchDraftJson
                ?.let(::splitDraftFromJson)
                ?.takeIf { it.size >= 2 }
                ?: generatedPreviews
            if (previews.size < 2) return@withContext null
            return@withContext Preview(
                item = previews.first().item,
                steps = previews.first().steps,
                survivor = MergeSourceSnapshot(
                    current,
                    todoStepDao.getBySavedItemId(current.savedItemId),
                    changeLogDao.getByItem(current.savedItemId),
                    db.notiSavedItemLinkDao().getBySavedItemId(current.savedItemId),
                ),
                splitChildren = previews,
                sourceVersionIsCurrent = payload.optLong("sourceVersion", current.lastUpdateTimestamp) == current.lastUpdateTimestamp,
            )
        }
        if (structural?.opType == PendingProposedOpType.Regenerate) {
            val payload = JSONObject(structural.payload)
            val result = payload.optJSONObject("result") ?: payload
            val replacementId = current.savedItemId
            var replacement = itemFromCreateOp(result, replacementId, now).copy(
                state = SavedItemState.Updated,
                isStarred = current.isStarred,
                userEdited = current.userEdited,
                humanEditCount = current.humanEditCount,
                origin = current.origin,
            )
            val replacementSteps = N8nOpParsing.parseSteps(result.optJSONArray("steps"), replacementId, now, 0)
            replacement = replacement.copy(
                buttons = SavedItemNormalization.mergeButtons(
                    result.optJSONArray("buttons")?.toString() ?: "[]",
                    N8nOpParsing.childButtons(result.optJSONArray("steps")),
                )
            )
            val normalized = SavedItemNormalization.normalize(replacement, replacementSteps)
            return@withContext Preview(
                item = normalized.item,
                steps = normalized.steps,
                survivor = MergeSourceSnapshot(
                    current,
                    todoStepDao.getBySavedItemId(current.savedItemId),
                    changeLogDao.getByItem(current.savedItemId),
                    db.notiSavedItemLinkDao().getBySavedItemId(current.savedItemId),
                ),
                sourceVersionIsCurrent = payload.optLong("sourceVersion", current.lastUpdateTimestamp) == current.lastUpdateTimestamp,
            )
        }
        val sourceItems = group.ops.flatMap(::mergeSourceIdsOf).distinct()
            .mapNotNull { savedItemDao.getById(it) }
        val preservedUserState = SavedItemMergePolicy.preservedUserState(listOf(current) + sourceItems)
            ?: return@withContext null
        val survivorSnapshot = MergeSourceSnapshot(
            item = current,
            steps = todoStepDao.getBySavedItemId(current.savedItemId),
            history = changeLogDao.getByItem(current.savedItemId),
            evidenceLinks = db.notiSavedItemLinkDao().getBySavedItemId(current.savedItemId),
        )
        val sourceSnapshots = sourceItems.map { source ->
            MergeSourceSnapshot(
                item = source,
                steps = todoStepDao.getBySavedItemId(source.savedItemId),
                history = changeLogDao.getByItem(source.savedItemId),
                evidenceLinks = db.notiSavedItemLinkDao().getBySavedItemId(source.savedItemId),
            )
        }
        var item = current
        val existingSubs = survivorSnapshot.steps
        val subs = existingSubs.toMutableList()
        sourceSnapshots.flatMap { it.steps }.forEach { sourceSub ->
            appendUniqueSubItem(subs, sourceSub.copy(parentSavedItemId = current.savedItemId))
        }
        group.ops.forEach { pending ->
            val op = JSONObject(pending.payload)
            val changes = op.optJSONObject("changes") ?: JSONObject()
            item = applyChangesInMemory(item, changes, now)
            item = applyOperationButtons(item, op, changes)
            N8nOpParsing.parseSteps(
                changes.optJSONArray("addedSteps"), current.savedItemId, now, baseSortOrder = subs.size,
            ).forEach { appendUniqueSubItem(subs, it) }
            removedStepIds(changes).forEach { removedId -> subs.removeAll { it.todoStepId == removedId } }
        }
        val normalized = SavedItemNormalization.normalize(
            item.copy(
                state = SavedItemState.Updated,
                lastUpdateTimestamp = now,
                isStarred = preservedUserState.isStarred,
                userEdited = preservedUserState.userEdited,
            ),
            subs.mapIndexed { index, sub -> sub.copy(parentSavedItemId = current.savedItemId, position = index) },
        )
        Preview(normalized.item, normalized.steps, survivorSnapshot, sourceSnapshots)
    }

    // ========== Apply (accept) ==========

    /** Everything needed to undo an accepted group. Held in memory by the review screen's snackbar. */
    data class ApplyOutcome(
        val ops: List<PendingProposedOp>,
        val createdItemId: String?,
        val createdItemIds: List<String> = listOfNotNull(createdItemId),
        val beforeTarget: SavedItem?,
        val beforeTargetSteps: List<TodoStep>,
        val deletedSourceItems: List<SavedItem>,
        val deletedSourceSteps: List<TodoStep>,
        val sourceLinks: List<NotiSavedItemLink>,
        val insertedTargetLinkIds: List<Long>,
        val sourceHistories: List<SavedItemChangeLog>,
        val transferredHistoryIds: List<Long>,
        val changeLogIds: List<Long>,
        val appliedItemId: String,
        val reviewTranslationStateJson: String? = null,
        val reviewBatchDraftJson: String? = null,
        val beforeReminders: List<Reminder> = emptyList(),
        val beforeReminderRefs: List<ReminderSavedItemRef> = emptyList(),
    )

    /**
     * Applies an accepted group to the database and deletes its op rows. Accepted items land in
     * the `saved` state directly — review acceptance *is* the acknowledgment.
     */
    suspend fun applyGroup(
        group: OpGroup,
        editedDraft: ReviewItemDraft? = null,
        editedDraftIsUserEdit: Boolean = true,
        now: Long = System.currentTimeMillis(),
    ): ApplyOutcome? = withContext(Dispatchers.IO) {
        val outcome = db.withTransaction {
            val pendingDraft = db.pendingReviewDraftDao().getByKey(group.key)
            val structuralType = group.ops.singleOrNull()?.opType
            val applied = when {
                group.isCreate -> applyCreate(group, editedDraft, editedDraftIsUserEdit, now)
                structuralType == PendingProposedOpType.Split -> applySplit(group, now)
                structuralType == PendingProposedOpType.Regenerate -> applyRegenerate(
                    group, editedDraft, editedDraftIsUserEdit, now,
                )
                else -> applyOnTarget(group, editedDraft, editedDraftIsUserEdit, now)
            }
            if (applied != null) {
                val opIds = group.ops.map { it.opId }
                pendingProposedOpDao.deleteByIds(opIds)
                db.pendingReviewDraftDao().deleteByKey(group.key)
                setProposalDecision(opIds, ProposedOpRecordDecision.Approved, now)
            }
            applied?.copy(
                reviewTranslationStateJson = pendingDraft?.translationStateJson,
                reviewBatchDraftJson = pendingDraft?.batchDraftJson,
            )
        }
        if (outcome != null) {
            ReflectionTrigger.noteDirtyItems(
                appContext,
                reflectionItemIds(outcome.createdItemIds, outcome.appliedItemId),
                now,
            )
            FirestoreOutboxWork.enqueue(appContext)
            outcome.deletedSourceItems.forEach { firestoreSync.markSavedItemDeleted(it.savedItemId, now) }
            if (outcome.beforeTarget != null && outcome.createdItemIds.isNotEmpty() &&
                outcome.beforeTarget.savedItemId !in outcome.createdItemIds
            ) {
                firestoreSync.markSavedItemDeleted(outcome.beforeTarget.savedItemId, now)
            }
            outcome.createdItemIds.ifEmpty { listOf(outcome.appliedItemId) }.forEach { itemId ->
                savedItemDao.getById(itemId)?.let { firestoreSync.syncSavedItem(it) }
            }
            outcome.beforeReminders.filter {
                it.status == ReminderStatus.Scheduled || it.status == ReminderStatus.DueUnseen
            }.forEach { ReminderScheduler.cancel(appContext, it.reminderId) }
        }
        outcome
    }

    private suspend fun applyCreate(
        group: OpGroup,
        editedDraft: ReviewItemDraft?,
        editedDraftIsUserEdit: Boolean,
        now: Long,
    ): ApplyOutcome {
        val pending = group.ops.first()
        val op = JSONObject(pending.payload)
        val itemId = "r_" + UUID.randomUUID().toString().take(8)
        var item = itemFromCreateOp(op, itemId, now)
        val subs = N8nOpParsing.parseSteps(op.optJSONArray("steps"), itemId, now, baseSortOrder = 0)
        item = item.copy(buttons = SavedItemNormalization.mergeButtons(item.buttons, N8nOpParsing.childButtons(op.optJSONArray("steps"))))
        var normalized = SavedItemNormalization.normalize(item, subs)
        val autoDraft = ReviewItemDraft(normalized.item, normalized.steps)
        if (editedDraft != null) {
            normalized = normalizeEditedDraft(autoDraft, editedDraft, itemId, now, editedDraftIsUserEdit)
        }
        item = normalized.item.copy(
            state = SavedItemState.Saved,
            isViewed = true,
            lastViewedChangeAt = now,
            lastUpdateTimestamp = now,
            syncModifiedAt = now,
        )
        savedItemDao.upsert(item)
        if (normalized.steps.isNotEmpty()) todoStepDao.upsertAll(normalized.steps)

        val evidence = evidenceOf(pending)
        itemRepo.addEvidenceLinks(itemId, evidence, item.itemType, NotiSavedItemLinkSource.LlmAutoExtraction)

        val changeLogIds = mutableListOf<Long>()
        changeLogIds += changeLogDao.insert(
            SavedItemChangeLog(
                savedItemId = itemId,
                createdAt = now,
                changeType = SavedItemChangeType.Created,
                changeSummary = reasonFrom(op),
                evidenceRecordIdsJson = pending.evidenceRecordIds,
                origin = "llm",
            )
        )
        if (editedDraftIsUserEdit && editedDraft != null && draftChangesGeneratedContent(autoDraft, editedDraft)) {
            changeLogIds += changeLogDao.insert(userEditChange(itemId, now))
        }
        journalAccepted(evidence, pending.notiKey, ExtractionJournalEventType.ItemCreated, itemId, item.title, reasonFrom(op), now)
        queueSavedItem(itemId, FirestoreOutboxKind.UpsertSavedItem, now)

        return ApplyOutcome(
            ops = group.ops,
            createdItemId = itemId,
            beforeTarget = null,
            beforeTargetSteps = emptyList(),
            deletedSourceItems = emptyList(),
            deletedSourceSteps = emptyList(),
            sourceLinks = emptyList(),
            insertedTargetLinkIds = emptyList(),
            sourceHistories = emptyList(),
            transferredHistoryIds = emptyList(),
            changeLogIds = changeLogIds,
            appliedItemId = itemId,
        )
    }

    private suspend fun applySplit(
        group: OpGroup,
        now: Long,
    ): ApplyOutcome? {
        val pending = group.ops.singleOrNull() ?: return null
        val before = savedItemDao.getById(group.targetItemId!!) ?: return null
        val payload = JSONObject(pending.payload)
        if (payload.optLong("sourceVersion", before.lastUpdateTimestamp) != before.lastUpdateTimestamp) return null
        val generatedChildren = payload.optJSONArray("children") ?: return null
        if (generatedChildren.length() < 2) return null
        val draftedChildren = db.pendingReviewDraftDao().getByKey(group.key)?.batchDraftJson
            ?.let(::splitDraftFromJson)
            ?.takeIf { it.size >= 2 }

        val beforeSteps = todoStepDao.getBySavedItemId(before.savedItemId)
        val linkDao = db.notiSavedItemLinkDao()
        val beforeLinks = linkDao.getBySavedItemId(before.savedItemId)
        val beforeHistory = changeLogDao.getByItem(before.savedItemId)
        val reminderDao = db.reminderDao()
        val beforeReminders = reminderDao.getBySavedItemId(before.savedItemId)
        val beforeReminderRefs = reminderDao.getRefsBySavedItemId(before.savedItemId)
        val createdIds = mutableListOf<String>()
        val insertedLinkIds = mutableListOf<Long>()
        val changeIds = mutableListOf<Long>()

        val childCount = draftedChildren?.size ?: generatedChildren.length()
        for (index in 0 until childCount) {
            val child = generatedChildren.optJSONObject(index) ?: JSONObject()
            val childId = "s_" + UUID.randomUUID().toString().take(8)
            val drafted = draftedChildren?.get(index)
            val generatedItem = itemFromCreateOp(child, childId, now)
            val draftedChanged = drafted != null && (
                drafted.item.title != generatedItem.title ||
                    drafted.item.content != generatedItem.content ||
                    drafted.item.itemType != generatedItem.itemType ||
                    drafted.item.deadlineAtMs != generatedItem.deadlineAtMs ||
                    drafted.item.buttons != generatedItem.buttons
                )
            var item = (drafted?.item?.copy(savedItemId = childId) ?: generatedItem).copy(
                state = SavedItemState.Saved,
                isViewed = true,
                isStarred = drafted?.item?.isStarred ?: before.isStarred,
                lastViewedChangeAt = now,
                userEdited = draftedChanged,
                humanEditCount = if (draftedChanged) 1 else 0,
                origin = if (draftedChanged) "manual" else generatedItem.origin,
            )
            val steps = drafted?.steps?.mapIndexed { position, step ->
                step.copy(parentSavedItemId = childId, position = position)
            } ?: N8nOpParsing.parseSteps(child.optJSONArray("steps"), childId, now, 0)
            item = item.copy(
                buttons = SavedItemNormalization.mergeButtons(
                    item.buttons,
                    N8nOpParsing.childButtons(child.optJSONArray("steps")),
                )
            )
            val normalized = SavedItemNormalization.normalize(item, steps)
            savedItemDao.upsert(normalized.item)
            if (normalized.steps.isNotEmpty()) todoStepDao.upsertAll(normalized.steps)

            val requestedEvidence = child.optJSONArray("evidenceRecordIds")?.let { arr ->
                buildSet { for (i in 0 until arr.length()) arr.optString(i).takeIf(String::isNotBlank)?.let(::add) }
            }.orEmpty()
            val relevantLinks = if (requestedEvidence.isEmpty()) emptyList() else beforeLinks.filter {
                it.notiRecordId in requestedEvidence
            }
            if (relevantLinks.isNotEmpty()) {
                insertedLinkIds += linkDao.insertAll(relevantLinks.map { link ->
                    link.copy(linkId = 0L, savedItemId = childId, type = normalized.item.itemType)
                }).filter { it > 0L }
            }
            changeIds += changeLogDao.insert(
                SavedItemChangeLog(
                    savedItemId = childId,
                    createdAt = now,
                    changeType = SavedItemChangeType.Split,
                    changeSummary = "Split from ${before.title}",
                    evidenceRecordIdsJson = JSONArray(relevantLinks.map { it.notiRecordId }).toString(),
                    origin = "llm",
                    sourceSavedItemId = before.savedItemId,
                    sourceItemTitle = before.title,
                )
            )
            queueSavedItem(childId, FirestoreOutboxKind.UpsertSavedItem, now)
            createdIds += childId
        }

        // A deliberate split is evidence that its independently handled results should not be
        // immediately suggested for merging again.
        val cooldownPairs = buildList {
            for (a in createdIds.indices) for (b in a + 1 until createdIds.size) {
                add(RejectedMerge.of(createdIds[a], createdIds[b], now))
            }
        }
        if (cooldownPairs.isNotEmpty()) rejectedMergeDao.upsertAll(cooldownPairs)

        todoStepDao.hardDeleteByParentId(before.savedItemId)
        beforeReminders.filter {
            it.status == ReminderStatus.Scheduled || it.status == ReminderStatus.DueUnseen
        }.forEach { reminder ->
            reminderDao.cancel(
                reminderId = reminder.reminderId,
                cancelledAtMs = now,
                updatedAtMs = now,
            )
        }
        savedItemDao.hardDeleteById(before.savedItemId)
        queueSavedItem(before.savedItemId, FirestoreOutboxKind.DeleteSavedItem, now)

        return ApplyOutcome(
            ops = group.ops,
            createdItemId = createdIds.first(),
            createdItemIds = createdIds,
            beforeTarget = before,
            beforeTargetSteps = beforeSteps,
            deletedSourceItems = emptyList(),
            deletedSourceSteps = emptyList(),
            sourceLinks = beforeLinks,
            insertedTargetLinkIds = insertedLinkIds,
            sourceHistories = beforeHistory,
            transferredHistoryIds = emptyList(),
            changeLogIds = changeIds,
            appliedItemId = createdIds.first(),
            beforeReminders = beforeReminders,
            beforeReminderRefs = beforeReminderRefs,
        )
    }

    private suspend fun applyRegenerate(
        group: OpGroup,
        editedDraft: ReviewItemDraft?,
        editedDraftIsUserEdit: Boolean,
        now: Long,
    ): ApplyOutcome? {
        val pending = group.ops.singleOrNull() ?: return null
        val before = savedItemDao.getById(group.targetItemId!!) ?: return null
        val payload = JSONObject(pending.payload)
        if (payload.optLong("sourceVersion", before.lastUpdateTimestamp) != before.lastUpdateTimestamp) return null
        val result = payload.optJSONObject("result") ?: payload
        val beforeSteps = todoStepDao.getBySavedItemId(before.savedItemId)
        var replacement = itemFromCreateOp(result, before.savedItemId, now).copy(
            state = before.state,
            isViewed = true,
            isStarred = before.isStarred,
            userEdited = before.userEdited,
            humanEditCount = before.humanEditCount,
            origin = before.origin,
            lastViewedChangeAt = now,
        )
        val generatedSteps = N8nOpParsing.parseSteps(result.optJSONArray("steps"), before.savedItemId, now, 0)
        replacement = replacement.copy(
            buttons = SavedItemNormalization.mergeButtons(
                result.optJSONArray("buttons")?.toString() ?: "[]",
                N8nOpParsing.childButtons(result.optJSONArray("steps")),
            )
        )
        var normalized = SavedItemNormalization.normalize(replacement, generatedSteps)
        val autoDraft = ReviewItemDraft(normalized.item, normalized.steps)
        if (editedDraft != null) {
            normalized = normalizeEditedDraft(
                autoDraft, editedDraft, before.savedItemId, now, editedDraftIsUserEdit,
            )
        }
        todoStepDao.hardDeleteByParentId(before.savedItemId)
        savedItemDao.upsert(normalized.item.copy(syncModifiedAt = now, lastUpdateTimestamp = now))
        if (normalized.steps.isNotEmpty()) todoStepDao.upsertAll(normalized.steps)
        val changeId = changeLogDao.insert(
            SavedItemChangeLog(
                savedItemId = before.savedItemId,
                createdAt = now,
                changeType = SavedItemChangeType.Regenerated,
                changeSummary = result.optString("changeSummary", pending.reason),
                changedFieldsJson = JSONObject().apply {
                    if (before.title != normalized.item.title) put("title", JSONObject().put("old", before.title).put("new", normalized.item.title))
                    if (before.content != normalized.item.content) put("content", JSONObject().put("old", before.content).put("new", normalized.item.content))
                    if (before.deadlineAtMs != normalized.item.deadlineAtMs) put("deadlineAtMs", JSONObject().put("old", before.deadlineAtMs).put("new", normalized.item.deadlineAtMs))
                }.toString(),
                origin = "llm",
            )
        )
        queueSavedItem(before.savedItemId, FirestoreOutboxKind.UpsertSavedItem, now)
        return ApplyOutcome(
            ops = group.ops,
            createdItemId = null,
            createdItemIds = emptyList(),
            beforeTarget = before,
            beforeTargetSteps = beforeSteps,
            deletedSourceItems = emptyList(),
            deletedSourceSteps = emptyList(),
            sourceLinks = emptyList(),
            insertedTargetLinkIds = emptyList(),
            sourceHistories = emptyList(),
            transferredHistoryIds = emptyList(),
            changeLogIds = listOf(changeId),
            appliedItemId = before.savedItemId,
        )
    }

    private suspend fun applyOnTarget(
        group: OpGroup,
        editedDraft: ReviewItemDraft?,
        editedDraftIsUserEdit: Boolean,
        now: Long,
    ): ApplyOutcome? {
        val targetId = group.targetItemId!!
        val before = savedItemDao.getById(targetId) ?: return null
        val sourceSnapshots = buildList {
            group.ops.flatMap(::mergeSourceIdsOf).distinct().forEach { sourceId ->
                savedItemDao.getById(sourceId)?.let(::add)
            }
        }
        val preservedUserState = SavedItemMergePolicy.preservedUserState(listOf(before) + sourceSnapshots)
            ?: return null

        var item = before
        val beforeTargetSteps = todoStepDao.getBySavedItemId(targetId)
        val deletedSourceSteps = sourceSnapshots.flatMap { todoStepDao.getBySavedItemId(it.savedItemId) }
        val workingSteps = beforeTargetSteps.toMutableList()
        deletedSourceSteps.forEach { sourceSub ->
            appendUniqueSubItem(workingSteps, sourceSub.copy(parentSavedItemId = targetId))
        }
        val linkDao = db.notiSavedItemLinkDao()
        val sourceIds = sourceSnapshots.map { it.savedItemId }
        val sourceLinks = if (sourceIds.isEmpty()) emptyList() else linkDao.getBySavedItemIds(sourceIds)
        val sourceHistories = if (sourceIds.isEmpty()) emptyList() else changeLogDao.getByItems(sourceIds)
        val insertedTargetLinkIds = mutableListOf<Long>()
        val transferredHistoryIds = mutableListOf<Long>()
        val changeLogIds = mutableListOf<Long>()
        val deletedItems = mutableListOf<SavedItem>()

        group.ops.forEach { pending ->
            val op = JSONObject(pending.payload)
            val changes = op.optJSONObject("changes") ?: JSONObject()
            item = applyChangesInMemory(item, changes, now)
            item = applyOperationButtons(item, op, changes)

            val added = N8nOpParsing.parseSteps(
                changes.optJSONArray("addedSteps"), targetId, now,
                baseSortOrder = workingSteps.size,
            )
            added.forEach { appendUniqueSubItem(workingSteps, it) }
            val removedIds = removedStepIds(changes)
            val removed = workingSteps.filter { it.todoStepId in removedIds }
            if (removedIds.isNotEmpty()) workingSteps.removeAll { it.todoStepId in removedIds }

            val evidence = evidenceOf(pending)
            if (evidence.isNotEmpty()) {
                insertedTargetLinkIds += itemRepo.addEvidenceLinks(
                    targetId, evidence, before.itemType, NotiSavedItemLinkSource.LlmAutoExtraction,
                ).filter { it > 0L }
            }
            changeLogIds += changeLogDao.insert(
                SavedItemChangeLog(
                    savedItemId = targetId,
                    createdAt = now,
                    changeType = SavedItemChangeType.LlmUpdate,
                    changeSummary = changes.optString("changeSummary", pending.reason),
                    appendedContent = changes.optString("appendedContent", "").trim(),
                    addedStepsJson = stepsToJson(added),
                    removedStepsJson = stepsToJson(removed),
                    changedFieldsJson = (changes.optJSONObject("changedFields") ?: JSONObject()).toString(),
                    evidenceRecordIdsJson = pending.evidenceRecordIds,
                    origin = "llm",
                )
            )
            journalAccepted(evidence, pending.notiKey, ExtractionJournalEventType.ItemUpdated, targetId, item.title, pending.reason, now)
        }

        // Preserve every source evidence link before deleting the source item. Duplicate target
        // links are ignored by the unique index and therefore do not participate in Undo.
        if (sourceLinks.isNotEmpty()) {
            insertedTargetLinkIds += linkDao.insertAll(sourceLinks.map { link ->
                link.copy(linkId = 0L, savedItemId = targetId, type = before.itemType)
            }).filter { it > 0L }
        }

        // Move source histories to the survivor without rewriting their original content, and add
        // one explicit snapshot row per source so its final description/steps remain inspectable.
        sourceSnapshots.forEach { source ->
            val history = sourceHistories.filter { it.savedItemId == source.savedItemId }
            if (history.isNotEmpty()) {
                val ids = history.map { it.changeId }
                changeLogDao.deleteByIds(ids)
                changeLogDao.upsertAll(history.map { row ->
                    row.copy(
                        savedItemId = targetId,
                        sourceSavedItemId = row.sourceSavedItemId.ifBlank { source.savedItemId },
                        sourceItemTitle = row.sourceItemTitle.ifBlank { source.title },
                    )
                })
                transferredHistoryIds += ids
            }
            val sourceSubs = deletedSourceSteps.filter { it.parentSavedItemId == source.savedItemId }
            val sourceEvidence = sourceLinks.filter { it.savedItemId == source.savedItemId }.map { it.notiRecordId }
            changeLogIds += changeLogDao.insert(
                SavedItemChangeLog(
                    savedItemId = targetId,
                    createdAt = now,
                    changeType = SavedItemChangeType.Merged,
                    changeSummary = source.title,
                    appendedContent = source.content,
                    addedStepsJson = stepsToJson(sourceSubs),
                    evidenceRecordIdsJson = JSONArray(sourceEvidence).toString(),
                    origin = "llm",
                    sourceSavedItemId = source.savedItemId,
                    sourceItemTitle = source.title,
                )
            )
        }

        sourceSnapshots.forEach { source ->
            deletedItems += source
            todoStepDao.hardDeleteByParentId(source.savedItemId)
            savedItemDao.hardDeleteById(source.savedItemId)
            rejectedMergeDao.deleteForItem(source.savedItemId)
            queueSavedItem(source.savedItemId, FirestoreOutboxKind.DeleteSavedItem, now)
        }

        // Acceptance is the acknowledgment: land in `saved` with the change cursor moved.
        item = item.copy(
            isStarred = preservedUserState.isStarred,
            userEdited = preservedUserState.userEdited,
        )
        var normalized = SavedItemNormalization.normalize(
            item,
            workingSteps.mapIndexed { index, sub -> sub.copy(parentSavedItemId = targetId, position = index) },
        )
        val autoDraft = ReviewItemDraft(normalized.item, normalized.steps)
        if (editedDraft != null) {
            normalized = normalizeEditedDraft(autoDraft, editedDraft, targetId, now, editedDraftIsUserEdit)
        }
        todoStepDao.hardDeleteByParentId(targetId)
        if (normalized.steps.isNotEmpty()) {
            todoStepDao.upsertAll(normalized.steps)
        }
        val applied = normalized.item.copy(
            state = if (before.isCompleted || before.isArchived) before.state else SavedItemState.Saved,
            isViewed = true,
            lastViewedChangeAt = now,
            lastUpdateTimestamp = now,
            syncModifiedAt = now,
        )
        savedItemDao.upsert(applied)
        if (editedDraftIsUserEdit && editedDraft != null && draftChangesGeneratedContent(autoDraft, editedDraft)) {
            changeLogIds += changeLogDao.insert(userEditChange(targetId, now))
        }
        queueSavedItem(targetId, FirestoreOutboxKind.UpsertSavedItem, now)

        return ApplyOutcome(
            ops = group.ops,
            createdItemId = null,
            beforeTarget = before,
            beforeTargetSteps = beforeTargetSteps,
            deletedSourceItems = deletedItems,
            deletedSourceSteps = deletedSourceSteps,
            sourceLinks = sourceLinks,
            insertedTargetLinkIds = insertedTargetLinkIds,
            sourceHistories = sourceHistories,
            transferredHistoryIds = transferredHistoryIds,
            changeLogIds = changeLogIds,
            appliedItemId = targetId,
        )
    }

    /** Reverses an [applyGroup]: restores the pre-apply state and re-stages the ops. */
    suspend fun undoApply(outcome: ApplyOutcome, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        db.withTransaction {
            outcome.createdItemIds.forEach { id ->
                todoStepDao.hardDeleteByParentId(id)
                savedItemDao.hardDeleteById(id)
                rejectedMergeDao.deleteForItem(id)
                queueSavedItem(id, FirestoreOutboxKind.DeleteSavedItem, now)
            }
            outcome.beforeTarget?.let { before ->
                savedItemDao.upsert(before)
                todoStepDao.hardDeleteByParentId(before.savedItemId)
                if (outcome.beforeTargetSteps.isNotEmpty()) todoStepDao.upsertAll(outcome.beforeTargetSteps)
                queueSavedItem(before.savedItemId, FirestoreOutboxKind.UpsertSavedItem, now)
            }
            outcome.deletedSourceItems.forEach {
                savedItemDao.upsert(it)
                queueSavedItem(it.savedItemId, FirestoreOutboxKind.UpsertSavedItem, now)
            }
            if (outcome.deletedSourceSteps.isNotEmpty()) todoStepDao.upsertAll(outcome.deletedSourceSteps)
            if (outcome.insertedTargetLinkIds.isNotEmpty()) {
                db.notiSavedItemLinkDao().deleteByIds(outcome.insertedTargetLinkIds)
            }
            if (outcome.sourceLinks.isNotEmpty()) db.notiSavedItemLinkDao().insertAll(outcome.sourceLinks)
            outcome.changeLogIds.forEach { changeLogDao.deleteById(it) }
            if (outcome.transferredHistoryIds.isNotEmpty()) changeLogDao.deleteByIds(outcome.transferredHistoryIds)
            if (outcome.sourceHistories.isNotEmpty()) changeLogDao.upsertAll(outcome.sourceHistories)
            outcome.beforeReminders.forEach { db.reminderDao().upsert(it) }
            if (outcome.beforeReminderRefs.isNotEmpty()) db.reminderDao().insertSavedItemRefs(outcome.beforeReminderRefs)
            pendingProposedOpDao.insertAll(outcome.ops)
            if (outcome.reviewTranslationStateJson != null || outcome.reviewBatchDraftJson != null) {
                db.pendingReviewDraftDao().upsert(
                    org.muilab.notigpt.model.features.PendingReviewDraft(
                        reviewKey = if (outcome.createdItemId != null) "create_${outcome.ops.first().opId}" else "item_${outcome.appliedItemId}",
                        translationStateJson = outcome.reviewTranslationStateJson,
                        batchDraftJson = outcome.reviewBatchDraftJson,
                        updatedAt = now,
                    )
                )
            }
            setProposalDecision(
                outcome.ops.map { it.opId },
                ProposedOpRecordDecision.Pending,
                now,
            )
        }
        FirestoreOutboxWork.enqueue(appContext)
        outcome.createdItemIds.forEach { firestoreSync.markSavedItemDeleted(it, now) }
        outcome.beforeTarget?.let { firestoreSync.syncSavedItem(it) }
        outcome.deletedSourceItems.forEach { firestoreSync.syncSavedItem(it) }
        outcome.beforeReminders.filter { it.status == ReminderStatus.Scheduled }.forEach {
            ReminderScheduler.schedule(appContext, it.reminderId, it.remindAtMs)
        }
    }

    // ========== Discard (reject) ==========

    /**
     * Rejects a group: op rows are deleted (nothing was ever applied), the verdict is journaled so
     * later extraction runs know, and rejected merges enter the cool-down table.
     */
    suspend fun discardGroup(
        group: OpGroup,
        now: Long = System.currentTimeMillis(),
    ): PendingReviewDraft? = withContext(Dispatchers.IO) {
        val draft = db.withTransaction {
          val reviewDraft = db.pendingReviewDraftDao().getByKey(group.key)
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
            if (pending.opType == PendingProposedOpType.Merge) {
                val ids = (mergeSourceIdsOf(pending) + pending.targetItemId).filter { it.isNotBlank() }.distinct()
                val pairs = buildList {
                    for (a in ids.indices) for (b in a + 1 until ids.size) add(RejectedMerge.of(ids[a], ids[b], now))
                }
                if (pairs.isNotEmpty()) rejectedMergeDao.upsertAll(pairs)
            }
          }
          group.ops.firstOrNull { it.opType == PendingProposedOpType.Split || it.opType == PendingProposedOpType.Regenerate }
              ?.targetItemId
              ?.takeIf(String::isNotBlank)
              ?.let { targetId ->
                  savedItemDao.getById(targetId)?.let { item ->
                      savedItemDao.upsert(item.copy(
                          pendingTransformType = "",
                          pendingTransformStatus = "",
                          syncModifiedAt = now,
                      ))
                      queueSavedItem(targetId, FirestoreOutboxKind.UpsertSavedItem, now)
                  }
              }
          val opIds = group.ops.map { it.opId }
          pendingProposedOpDao.deleteByIds(opIds)
          db.pendingReviewDraftDao().deleteByKey(group.key)
          setProposalDecision(opIds, ProposedOpRecordDecision.Rejected, now)
          reviewDraft
        }
        FirestoreOutboxWork.enqueue(appContext)
        draft
    }

    private suspend fun queueSavedItem(savedItemId: String, kind: String, ts: Long) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        db.firestoreOutboxDao().upsert(FirestoreOutboxOp.savedItem(uid, kind, savedItemId, ts))
    }

    private suspend fun persistProposedOpRecords(ops: List<PendingProposedOp>) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank() || ops.isEmpty()) return
        val proposals = ops.map { op ->
            ProposedOpRecord(
                proposalId = "$uid:p_${op.opId}",
                uid = uid,
                opId = op.opId,
                batchId = op.batchId,
                opType = op.opType,
                payload = op.payload,
                targetItemId = op.targetItemId,
                itemType = op.itemType,
                createdAt = op.createdAt,
            )
        }
        db.proposedOpRecordDao().upsertAll(proposals)
        proposals.forEach {
            db.firestoreOutboxDao().upsert(
                FirestoreOutboxOp.proposedOpRecord(uid, it.proposalId, it.createdAt)
            )
        }
    }

    private suspend fun setProposalDecision(opIds: List<Long>, decision: String, now: Long) {
        if (opIds.isEmpty()) return
        val dao = db.proposedOpRecordDao()
        dao.setDecision(opIds, decision, now)
        dao.getByOpIds(opIds).forEach { proposal ->
            db.firestoreOutboxDao().upsert(
                FirestoreOutboxOp.proposedOpRecord(proposal.uid, proposal.proposalId, now)
            )
        }
    }

    /** Undo of a reject: the ops come back exactly as they were (fresh row ids). */
    suspend fun restoreDiscarded(group: OpGroup, reviewDraft: PendingReviewDraft? = null) = withContext(Dispatchers.IO) {
        val insertedIds = pendingProposedOpDao.insertAll(group.ops.map { it.copy(opId = 0L) })
        group.ops.firstOrNull { it.opType == PendingProposedOpType.Split || it.opType == PendingProposedOpType.Regenerate }
            ?.let { transform ->
                savedItemDao.getById(transform.targetItemId)?.let { item ->
                    val now = System.currentTimeMillis()
                    savedItemDao.upsert(item.copy(
                        pendingTransformType = transform.opType,
                        pendingTransformStatus = "review",
                        syncModifiedAt = now,
                    ))
                    queueSavedItem(item.savedItemId, FirestoreOutboxKind.UpsertSavedItem, now)
                    FirestoreOutboxWork.enqueue(appContext)
                }
            }
        reviewDraft?.let { draft ->
            val restoredKey = if (group.isCreate) "create_${insertedIds.first()}" else group.key
            db.pendingReviewDraftDao().upsert(draft.copy(reviewKey = restoredKey))
        }
        // The cool-down entries a rejected merge wrote are left in place; re-rejecting would
        // recreate them anyway and an accepted merge deletes them via deleteForItem.
    }

    /** Drops staged ops whose target item no longer exists (deleted out from under review). */
    suspend fun purgeOrphanedOps() = withContext(Dispatchers.IO) {
        pendingProposedOpDao.getAll()
            .filter { it.targetItemId.isNotBlank() && savedItemDao.getById(it.targetItemId) == null }
            .map { it.opId }
            .takeIf { it.isNotEmpty() }
            ?.let { pendingProposedOpDao.deleteByIds(it) }
        val activeKeys = groupOps(pendingProposedOpDao.getAll()).map { it.key }
        if (activeKeys.isEmpty()) db.pendingReviewDraftDao().deleteNonLegacy()
        else db.pendingReviewDraftDao().deleteOrphans(activeKeys)
    }

    // ========== Helpers ==========

    private fun itemFromCreateOp(op: JSONObject, itemId: String, now: Long): SavedItem {
        val isTodo = op.optString("itemType", SavedItemType.Todo) == SavedItemType.Todo
        return SavedItem(
            savedItemId = itemId,
            title = N8nOpParsing.titleFrom(op),
            content = N8nOpParsing.contentFrom(op),
            itemType = if (isTodo) SavedItemType.Todo else SavedItemType.Keep,
            state = SavedItemState.Saved,
            lastUpdateTimestamp = now,
            syncModifiedAt = now,
            deadlineAtMs = N8nOpParsing.isoToUnixMillis(op.optString("deadlineTimeString", "-1")),
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
     * user has already read what's there); title and the evidence-backed deadline may be replaced.
     * isStarred is user-owned and never touched.
     */
    private fun applyChangesInMemory(item: SavedItem, changes: JSONObject, now: Long): SavedItem {
        val fragment = changes.optString("appendedContent", "").trim()
        val content = if (fragment.isNotBlank()) item.content + itemRepo.buildUpdateSection(fragment, now) else item.content
        val changedFields = changes.optJSONObject("changedFields") ?: JSONObject()
        val title = changedFields.optJSONObject("title")?.optString("new")?.takeIf { it.isNotBlank() } ?: item.title
        val deadline = changedFields.optJSONObject("deadlineTimeString")
            ?.let { N8nOpParsing.isoToUnixMillis(it.optString("new", "-1")) } ?: item.deadlineAtMs
        return item.copy(
            title = title,
            content = content,
            deadlineAtMs = deadline,
            lastUpdateTimestamp = now,
            syncModifiedAt = now,
        )
    }

    /** Buttons are append-only in v3, including legacy top-level and child-owned payloads. */
    private fun applyOperationButtons(item: SavedItem, op: JSONObject, changes: JSONObject): SavedItem = item.copy(
        buttons = SavedItemNormalization.mergeButtons(
            item.buttons,
            op.optJSONArray("buttons")?.toString() ?: "[]",
            changes.optJSONArray("addedButtons")?.toString() ?: "[]",
            N8nOpParsing.childButtons(changes.optJSONArray("addedSteps")),
        )
    )

    private fun removedStepIds(changes: JSONObject): List<String> {
        val arr = changes.optJSONArray("removedSteps") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val id = r.optString("todoStepId")
                if (id.isNotBlank()) add(id)
            }
        }
    }

    private fun stepsToJson(items: List<TodoStep>): String = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("todoStepId", item.todoStepId)
                put("text", item.text)
                put("isCompleted", item.isCompleted)
                put("position", item.position)
            })
        }
    }.toString()

    private fun splitDraftToJson(children: List<ReviewItemDraft>): String = JSONArray().apply {
        children.forEach { draft ->
            put(JSONObject().apply {
                put("savedItemId", draft.item.savedItemId)
                put("title", draft.item.title)
                put("content", draft.item.content)
                put("itemType", draft.item.itemType)
                put("deadlineAtMs", draft.item.deadlineAtMs)
                put("state", draft.item.state)
                put("isStarred", draft.item.isStarred)
                put("buttons", draft.item.buttons)
                put("steps", JSONArray(stepsToJson(draft.steps)))
            })
        }
    }.toString()

    private fun splitDraftFromJson(raw: String): List<ReviewItemDraft> = try {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val id = obj.optString("savedItemId", "pending_draft_$index")
                val item = SavedItem(
                    savedItemId = id,
                    title = obj.optString("title"),
                    content = obj.optString("content"),
                    itemType = if (obj.optString("itemType") == SavedItemType.Keep) SavedItemType.Keep else SavedItemType.Todo,
                    state = obj.optString("state", SavedItemState.New),
                    lastUpdateTimestamp = 0L,
                    deadlineAtMs = obj.optLong("deadlineAtMs", 0L),
                    buttons = obj.optString("buttons", "[]"),
                    isStarred = obj.optBoolean("isStarred", false),
                )
                val stepArray = obj.optJSONArray("steps") ?: JSONArray()
                val steps = buildList {
                    for (stepIndex in 0 until stepArray.length()) {
                        val step = stepArray.optJSONObject(stepIndex) ?: continue
                        add(TodoStep(
                            todoStepId = step.optString("todoStepId").ifBlank { "st_${UUID.randomUUID().toString().take(8)}" },
                            parentSavedItemId = id,
                            text = step.optString("text"),
                            isCompleted = step.optBoolean("isCompleted", false),
                            position = stepIndex,
                        ))
                    }
                }
                add(ReviewItemDraft(item, steps))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun appendUniqueSubItem(target: MutableList<TodoStep>, candidate: TodoStep) {
        ReviewMergeSemantics.appendUnique(target, candidate)
    }

    private fun draftChangesGeneratedContent(auto: ReviewItemDraft, edited: ReviewItemDraft): Boolean {
        val a = auto.item
        val e = edited.item
        if (a.title != e.title || a.content != e.content || a.itemType != e.itemType ||
            a.deadlineAtMs != e.deadlineAtMs || a.buttons != e.buttons
        ) return true
        fun normalized(items: List<TodoStep>) = items.map {
            TodoStep.normalizeText(it.text) to it.isCompleted
        }
        return normalized(auto.steps) != normalized(edited.steps)
    }

    private fun normalizeEditedDraft(
        auto: ReviewItemDraft,
        edited: ReviewItemDraft,
        realItemId: String,
        now: Long,
        markAsUserEdit: Boolean,
    ): SavedItemNormalization.Result {
        val contentEdited = draftChangesGeneratedContent(auto, edited)
        val retargetedSubs = edited.steps.mapIndexed { index, sub ->
            sub.copy(parentSavedItemId = realItemId, position = index)
        }
        val item = edited.item.copy(
            savedItemId = realItemId,
            origin = if (markAsUserEdit && contentEdited) "manual" else auto.item.origin,
            humanEditCount = auto.item.humanEditCount + if (markAsUserEdit && contentEdited) 1 else 0,
            userEdited = auto.item.userEdited || (markAsUserEdit && contentEdited),
            lastUpdateTimestamp = now,
        )
        return SavedItemNormalization.normalize(item, retargetedSubs)
    }

    private fun userEditChange(savedItemId: String, now: Long) = SavedItemChangeLog(
        savedItemId = savedItemId,
        createdAt = now,
        changeType = SavedItemChangeType.UserEdit,
        origin = "user",
    )

    private fun mergeSourceIdsOf(pending: PendingProposedOp): List<String> = try {
        val arr = JSONArray(pending.mergeSourceItemIds)
        buildList { for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(::add) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun evidenceOf(pending: PendingProposedOp): Set<String> = try {
        val arr = JSONArray(pending.evidenceRecordIds)
        buildSet { for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(::add) }
    } catch (_: Exception) {
        emptySet()
    }

    /** Threads to journal a verdict to: the evidence keys, falling back to the op's own notiKey. */
    private fun journalKeysOf(pending: PendingProposedOp): List<String> {
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

    companion object {
        internal fun reflectionItemIds(createdItemIds: List<String>, appliedItemId: String): List<String> =
            createdItemIds.ifEmpty { listOf(appliedItemId) }
    }
}
