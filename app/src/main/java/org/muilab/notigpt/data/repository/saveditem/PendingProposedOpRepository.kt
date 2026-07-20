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
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.SavedItemChangeType
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedSubItem
import org.muilab.notigpt.model.features.ReviewItemDraft
import org.muilab.notigpt.model.features.PendingReviewDraft
import java.util.UUID
import org.muilab.notigpt.work.FirestoreOutboxWork

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
    private val subTaskDao = db.subTaskDao()
    private val changeLogDao = db.savedItemChangeLogDao()
    private val journalDao = db.extractionJournalDao()
    private val itemRepo by lazy { SavedItemRepository(savedItemDao, appContext.applicationContext) }
    private val journalRepo by lazy { ExtractionJournalRepository(journalDao) }
    private val firestoreSync by lazy { FirestoreSyncRepository(appContext.applicationContext) }

    fun observePending(): Flow<List<PendingProposedOp>> = pendingProposedOpDao.observeAll()

    fun observeReviewDrafts(): Flow<List<PendingReviewDraft>> = db.pendingReviewDraftDao().observeAll()

    suspend fun setReviewWhen(reviewKey: String, whenAtMs: Long, now: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            db.pendingReviewDraftDao().upsert(PendingReviewDraft(reviewKey, whenAtMs, now))
        }

    suspend fun getPending(): List<PendingProposedOp> = withContext(Dispatchers.IO) { pendingProposedOpDao.getAll() }

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
                    itemType = if (op.optBoolean("isTask", true)) SavedItemType.Task else SavedItemType.Keep,
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
        candidateWhenByRef: Map<String, Long>,
        now: Long = System.currentTimeMillis(),
    ): List<PendingProposedOp> = withContext(Dispatchers.IO) {
        data class Prepared(
            val row: PendingProposedOp,
            val consumed: List<PendingProposedOp>,
            val inheritedWhen: Long?,
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

            val whenValues = allRefs.mapNotNull(candidateWhenByRef::get)
                .filter { SavedItem.hasPlannedDate(it) }
                .distinct()
            if (whenValues.size > 1) continue
            val inheritedWhen = whenValues.singleOrNull()

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
                        inheritedWhen = inheritedWhen,
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
                        inheritedWhen = inheritedWhen,
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
            inserted.forEachIndexed { index, row ->
                prepared[index].inheritedWhen?.let { whenAt ->
                    val reviewKey = if (row.opType == PendingProposedOpType.Create) "create_${row.opId}" else "item_${row.targetItemId}"
                    db.pendingReviewDraftDao().upsert(PendingReviewDraft(reviewKey, whenAt, now))
                }
            }
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
        val subItems: List<SavedSubItem>,
        val history: List<SavedItemChangeLog>,
        val evidenceLinks: List<NotiSavedItemLink>,
    )

    data class Preview(
        val item: SavedItem,
        val subItems: List<SavedSubItem>,
        val survivor: MergeSourceSnapshot? = null,
        val mergeSources: List<MergeSourceSnapshot> = emptyList(),
    )

    /**
     * Computes what the group would produce, without touching the database: creates render the
     * would-be item; updates/merges render the current item with the staged changes applied in
     * memory. Returns null when an update group's target vanished (the ops should be purged).
     */
    suspend fun buildPreview(
        group: OpGroup,
        now: Long = System.currentTimeMillis(),
        reviewWhenAtMs: Long? = null,
    ): Preview? = withContext(Dispatchers.IO) {
        if (group.isCreate) {
            val op = JSONObject(group.ops.first().payload)
            val previewId = "pending_${group.ops.first().opId}"
            var item = itemFromCreateOp(op, previewId, now).copy(state = SavedItemState.New)
            val subs = N8nOpParsing.parseSubTasks(op.optJSONArray("subTasks"), previewId, now, baseSortOrder = 0)
            item = item.copy(buttons = SavedItemNormalization.mergeButtons(item.buttons, N8nOpParsing.childButtons(op.optJSONArray("subTasks"))))
            if (reviewWhenAtMs != null) item = item.copy(whenAtMs = reviewWhenAtMs)
            val normalized = SavedItemNormalization.normalize(item, subs)
            return@withContext Preview(normalized.item, normalized.subItems)
        }
        val current = savedItemDao.getById(group.targetItemId!!) ?: return@withContext null
        val sourceItems = group.ops.flatMap(::mergeSourceIdsOf).distinct()
            .mapNotNull { savedItemDao.getById(it) }
        val preservedUserState = SavedItemMergePolicy.preservedUserState(listOf(current) + sourceItems)
            ?: return@withContext null
        val survivorSnapshot = MergeSourceSnapshot(
            item = current,
            subItems = subTaskDao.getBySavedItemId(current.savedItemId),
            history = changeLogDao.getByItem(current.savedItemId),
            evidenceLinks = db.notiSavedItemLinkDao().getBySavedItemId(current.savedItemId),
        )
        val sourceSnapshots = sourceItems.map { source ->
            MergeSourceSnapshot(
                item = source,
                subItems = subTaskDao.getBySavedItemId(source.savedItemId),
                history = changeLogDao.getByItem(source.savedItemId),
                evidenceLinks = db.notiSavedItemLinkDao().getBySavedItemId(source.savedItemId),
            )
        }
        var item = current
        val existingSubs = survivorSnapshot.subItems
        val subs = existingSubs.toMutableList()
        sourceSnapshots.flatMap { it.subItems }.forEach { sourceSub ->
            appendUniqueSubItem(subs, sourceSub.copy(parentSavedItemId = current.savedItemId))
        }
        group.ops.forEach { pending ->
            val op = JSONObject(pending.payload)
            val changes = op.optJSONObject("changes") ?: JSONObject()
            item = applyChangesInMemory(item, changes, now)
            item = applyOperationButtons(item, op, changes)
            N8nOpParsing.parseSubTasks(
                changes.optJSONArray("addedSubTasks"), current.savedItemId, now, baseSortOrder = subs.size,
            ).forEach { appendUniqueSubItem(subs, it) }
            removedSubTaskIds(changes).forEach { removedId -> subs.removeAll { it.savedSubItemId == removedId } }
        }
        val normalized = SavedItemNormalization.normalize(
            item.copy(
                state = SavedItemState.Updated,
                lastUpdateTimestamp = now,
                whenAtMs = reviewWhenAtMs ?: preservedUserState.whenAtMs,
                isStarred = preservedUserState.isStarred,
                userEdited = preservedUserState.userEdited,
            ),
            subs.mapIndexed { index, sub -> sub.copy(parentSavedItemId = current.savedItemId, position = index) },
        )
        Preview(normalized.item, normalized.subItems, survivorSnapshot, sourceSnapshots)
    }

    // ========== Apply (accept) ==========

    /** Everything needed to undo an accepted group. Held in memory by the review screen's snackbar. */
    data class ApplyOutcome(
        val ops: List<PendingProposedOp>,
        val createdItemId: String?,
        val beforeTarget: SavedItem?,
        val beforeTargetSubItems: List<SavedSubItem>,
        val deletedSourceItems: List<SavedItem>,
        val deletedSourceSubItems: List<SavedSubItem>,
        val sourceLinks: List<NotiSavedItemLink>,
        val insertedTargetLinkIds: List<Long>,
        val sourceHistories: List<SavedItemChangeLog>,
        val transferredHistoryIds: List<Long>,
        val changeLogIds: List<Long>,
        val appliedItemId: String,
        val reviewWhenAtMs: Long?,
    )

    /**
     * Applies an accepted group to the database and deletes its op rows. Accepted items land in
     * the `saved` state directly — review acceptance *is* the acknowledgment.
     */
    suspend fun applyGroup(
        group: OpGroup,
        editedDraft: ReviewItemDraft? = null,
        now: Long = System.currentTimeMillis(),
    ): ApplyOutcome? = withContext(Dispatchers.IO) {
        val outcome = db.withTransaction {
            val pendingDraft = db.pendingReviewDraftDao().getByKey(group.key)
            val applied = if (group.isCreate) {
                applyCreate(group, pendingDraft?.whenAtMs, editedDraft, now)
            } else {
                applyOnTarget(group, pendingDraft?.whenAtMs, editedDraft, now)
            }
            if (applied != null) {
                val opIds = group.ops.map { it.opId }
                pendingProposedOpDao.deleteByIds(opIds)
                db.pendingReviewDraftDao().deleteByKey(group.key)
                setProposalDecision(opIds, ProposedOpRecordDecision.Approved, now)
            }
            applied
        }
        if (outcome != null) {
            FirestoreOutboxWork.enqueue(appContext)
            outcome.deletedSourceItems.forEach { firestoreSync.markSavedItemDeleted(it.savedItemId, now) }
            savedItemDao.getById(outcome.appliedItemId)?.let { firestoreSync.syncSavedItem(it) }
        }
        outcome
    }

    private suspend fun applyCreate(
        group: OpGroup,
        reviewWhenAtMs: Long?,
        editedDraft: ReviewItemDraft?,
        now: Long,
    ): ApplyOutcome {
        val pending = group.ops.first()
        val op = JSONObject(pending.payload)
        val itemId = "r_" + UUID.randomUUID().toString().take(8)
        var item = itemFromCreateOp(op, itemId, now)
        val subs = N8nOpParsing.parseSubTasks(op.optJSONArray("subTasks"), itemId, now, baseSortOrder = 0)
        item = item.copy(buttons = SavedItemNormalization.mergeButtons(item.buttons, N8nOpParsing.childButtons(op.optJSONArray("subTasks"))))
        if (reviewWhenAtMs != null) item = item.copy(whenAtMs = reviewWhenAtMs)
        var normalized = SavedItemNormalization.normalize(item, subs)
        val autoDraft = ReviewItemDraft(normalized.item, normalized.subItems)
        if (editedDraft != null) normalized = normalizeEditedDraft(autoDraft, editedDraft, itemId, now)
        item = normalized.item.copy(
            state = SavedItemState.Saved,
            isViewed = true,
            lastViewedChangeAt = now,
            lastUpdateTimestamp = now,
        )
        savedItemDao.upsert(item)
        if (normalized.subItems.isNotEmpty()) subTaskDao.upsertAll(normalized.subItems)

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
        if (editedDraft != null && draftChangesGeneratedContent(autoDraft, editedDraft)) {
            changeLogIds += changeLogDao.insert(userEditChange(itemId, now))
        }
        journalAccepted(evidence, pending.notiKey, ExtractionJournalEventType.ItemCreated, itemId, item.title, reasonFrom(op), now)
        queueSavedItem(itemId, FirestoreOutboxKind.UpsertSavedItem, now)

        return ApplyOutcome(
            ops = group.ops,
            createdItemId = itemId,
            beforeTarget = null,
            beforeTargetSubItems = emptyList(),
            deletedSourceItems = emptyList(),
            deletedSourceSubItems = emptyList(),
            sourceLinks = emptyList(),
            insertedTargetLinkIds = emptyList(),
            sourceHistories = emptyList(),
            transferredHistoryIds = emptyList(),
            changeLogIds = changeLogIds,
            appliedItemId = itemId,
            reviewWhenAtMs = reviewWhenAtMs,
        )
    }

    private suspend fun applyOnTarget(
        group: OpGroup,
        reviewWhenAtMs: Long?,
        editedDraft: ReviewItemDraft?,
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
        val beforeTargetSubItems = subTaskDao.getBySavedItemId(targetId)
        val deletedSourceSubItems = sourceSnapshots.flatMap { subTaskDao.getBySavedItemId(it.savedItemId) }
        val workingSubItems = beforeTargetSubItems.toMutableList()
        deletedSourceSubItems.forEach { sourceSub ->
            appendUniqueSubItem(workingSubItems, sourceSub.copy(parentSavedItemId = targetId))
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

            val added = N8nOpParsing.parseSubTasks(
                changes.optJSONArray("addedSubTasks"), targetId, now,
                baseSortOrder = workingSubItems.size,
            )
            added.forEach { appendUniqueSubItem(workingSubItems, it) }
            val removedIds = removedSubTaskIds(changes)
            val removed = workingSubItems.filter { it.savedSubItemId in removedIds }
            if (removedIds.isNotEmpty()) workingSubItems.removeAll { it.savedSubItemId in removedIds }

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
                    addedSubTasksJson = subItemsToJson(added),
                    removedSubTasksJson = subItemsToJson(removed),
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
        // one explicit snapshot row per source so its final description/subtasks remain inspectable.
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
            val sourceSubs = deletedSourceSubItems.filter { it.parentSavedItemId == source.savedItemId }
            val sourceEvidence = sourceLinks.filter { it.savedItemId == source.savedItemId }.map { it.notiRecordId }
            changeLogIds += changeLogDao.insert(
                SavedItemChangeLog(
                    savedItemId = targetId,
                    createdAt = now,
                    changeType = SavedItemChangeType.Merged,
                    changeSummary = source.title,
                    appendedContent = source.content,
                    addedSubTasksJson = subItemsToJson(sourceSubs),
                    evidenceRecordIdsJson = JSONArray(sourceEvidence).toString(),
                    origin = "llm",
                    sourceSavedItemId = source.savedItemId,
                    sourceItemTitle = source.title,
                )
            )
        }

        sourceSnapshots.forEach { source ->
            deletedItems += source
            subTaskDao.hardDeleteByParentId(source.savedItemId)
            savedItemDao.hardDeleteById(source.savedItemId)
            rejectedMergeDao.deleteForItem(source.savedItemId)
            queueSavedItem(source.savedItemId, FirestoreOutboxKind.DeleteSavedItem, now)
        }

        // Acceptance is the acknowledgment: land in `saved` with the change cursor moved.
        item = item.copy(
            whenAtMs = reviewWhenAtMs ?: preservedUserState.whenAtMs,
            isStarred = preservedUserState.isStarred,
            userEdited = preservedUserState.userEdited,
        )
        var normalized = SavedItemNormalization.normalize(
            item,
            workingSubItems.mapIndexed { index, sub -> sub.copy(parentSavedItemId = targetId, position = index) },
        )
        val autoDraft = ReviewItemDraft(normalized.item, normalized.subItems)
        if (editedDraft != null) normalized = normalizeEditedDraft(autoDraft, editedDraft, targetId, now)
        subTaskDao.hardDeleteByParentId(targetId)
        if (normalized.subItems.isNotEmpty()) {
            subTaskDao.upsertAll(normalized.subItems)
        }
        val applied = normalized.item.copy(
            state = if (before.isCompleted || before.isArchived) before.state else SavedItemState.Saved,
            isViewed = true,
            lastViewedChangeAt = now,
            lastUpdateTimestamp = now,
        )
        savedItemDao.upsert(applied)
        if (editedDraft != null && draftChangesGeneratedContent(autoDraft, editedDraft)) {
            changeLogIds += changeLogDao.insert(userEditChange(targetId, now))
        }
        queueSavedItem(targetId, FirestoreOutboxKind.UpsertSavedItem, now)

        return ApplyOutcome(
            ops = group.ops,
            createdItemId = null,
            beforeTarget = before,
            beforeTargetSubItems = beforeTargetSubItems,
            deletedSourceItems = deletedItems,
            deletedSourceSubItems = deletedSourceSubItems,
            sourceLinks = sourceLinks,
            insertedTargetLinkIds = insertedTargetLinkIds,
            sourceHistories = sourceHistories,
            transferredHistoryIds = transferredHistoryIds,
            changeLogIds = changeLogIds,
            appliedItemId = targetId,
            reviewWhenAtMs = reviewWhenAtMs,
        )
    }

    /** Reverses an [applyGroup]: restores the pre-apply state and re-stages the ops. */
    suspend fun undoApply(outcome: ApplyOutcome, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        db.withTransaction {
            outcome.createdItemId?.let { id ->
                subTaskDao.hardDeleteByParentId(id)
                savedItemDao.hardDeleteById(id)
                queueSavedItem(id, FirestoreOutboxKind.DeleteSavedItem, now)
            }
            outcome.beforeTarget?.let { before ->
                savedItemDao.upsert(before)
                subTaskDao.hardDeleteByParentId(before.savedItemId)
                if (outcome.beforeTargetSubItems.isNotEmpty()) subTaskDao.upsertAll(outcome.beforeTargetSubItems)
                queueSavedItem(before.savedItemId, FirestoreOutboxKind.UpsertSavedItem, now)
            }
            outcome.deletedSourceItems.forEach {
                savedItemDao.upsert(it)
                queueSavedItem(it.savedItemId, FirestoreOutboxKind.UpsertSavedItem, now)
            }
            if (outcome.deletedSourceSubItems.isNotEmpty()) subTaskDao.upsertAll(outcome.deletedSourceSubItems)
            if (outcome.insertedTargetLinkIds.isNotEmpty()) {
                db.notiSavedItemLinkDao().deleteByIds(outcome.insertedTargetLinkIds)
            }
            if (outcome.sourceLinks.isNotEmpty()) db.notiSavedItemLinkDao().insertAll(outcome.sourceLinks)
            outcome.changeLogIds.forEach { changeLogDao.deleteById(it) }
            if (outcome.transferredHistoryIds.isNotEmpty()) changeLogDao.deleteByIds(outcome.transferredHistoryIds)
            if (outcome.sourceHistories.isNotEmpty()) changeLogDao.upsertAll(outcome.sourceHistories)
            pendingProposedOpDao.insertAll(outcome.ops)
            if (outcome.reviewWhenAtMs != null) {
                db.pendingReviewDraftDao().upsert(
                    org.muilab.notigpt.model.features.PendingReviewDraft(
                        reviewKey = if (outcome.createdItemId != null) "create_${outcome.ops.first().opId}" else "item_${outcome.appliedItemId}",
                        whenAtMs = outcome.reviewWhenAtMs,
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
        outcome.createdItemId?.let { firestoreSync.markSavedItemDeleted(it, now) }
        outcome.beforeTarget?.let { firestoreSync.syncSavedItem(it) }
        outcome.deletedSourceItems.forEach { firestoreSync.syncSavedItem(it) }
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
     * whenAtMs/isStarred are user-owned and never touched.
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

    /** Buttons are append-only in v3, including legacy top-level and child-owned payloads. */
    private fun applyOperationButtons(item: SavedItem, op: JSONObject, changes: JSONObject): SavedItem = item.copy(
        buttons = SavedItemNormalization.mergeButtons(
            item.buttons,
            op.optJSONArray("buttons")?.toString() ?: "[]",
            changes.optJSONArray("addedButtons")?.toString() ?: "[]",
            N8nOpParsing.childButtons(changes.optJSONArray("addedSubTasks")),
        )
    )

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

    private fun subItemsToJson(items: List<SavedSubItem>): String = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("savedSubItemId", item.savedSubItemId)
                put("text", item.text)
                put("isCompleted", item.isCompleted)
                put("position", item.position)
            })
        }
    }.toString()

    private fun appendUniqueSubItem(target: MutableList<SavedSubItem>, candidate: SavedSubItem) {
        ReviewMergeSemantics.appendUnique(target, candidate)
    }

    private fun draftChangesGeneratedContent(auto: ReviewItemDraft, edited: ReviewItemDraft): Boolean {
        val a = auto.item
        val e = edited.item
        if (a.title != e.title || a.content != e.content || a.itemType != e.itemType ||
            a.deadlineAtMs != e.deadlineAtMs || a.startAtMs != e.startAtMs ||
            a.endAtMs != e.endAtMs || a.buttons != e.buttons
        ) return true
        fun normalized(items: List<SavedSubItem>) = items.map {
            SavedSubItem.normalizeText(it.text) to it.isCompleted
        }
        return normalized(auto.subItems) != normalized(edited.subItems)
    }

    private fun normalizeEditedDraft(
        auto: ReviewItemDraft,
        edited: ReviewItemDraft,
        realItemId: String,
        now: Long,
    ): SavedItemNormalization.Result {
        val contentEdited = draftChangesGeneratedContent(auto, edited)
        val retargetedSubs = edited.subItems.mapIndexed { index, sub ->
            sub.copy(parentSavedItemId = realItemId, position = index)
        }
        val item = edited.item.copy(
            savedItemId = realItemId,
            origin = if (contentEdited) "manual" else auto.item.origin,
            humanEditCount = auto.item.humanEditCount + if (contentEdited) 1 else 0,
            userEdited = auto.item.userEdited || contentEdited,
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
}
