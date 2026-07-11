package org.muilab.notigpt.data.repository.notification

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.muilab.notigpt.data.local.room.dao.NotiLlmStateDao
import org.muilab.notigpt.model.features.NotiLlmState

/**
 * Repository for per-notiKey LLM-derived thread state.
 *
 * Scan gate flags are owned by NotiActionsRepository (they drive extraction scheduling); this
 * repository owns the classification side (categories + re-check bookkeeping) and generic reads.
 */
class NotiLlmStateRepository(private val llmStateDao: NotiLlmStateDao) {

    suspend fun getByKey(notiKey: String): NotiLlmState? = withContext(Dispatchers.IO) {
        llmStateDao.getByKey(notiKey)
    }

    suspend fun getByKeys(notiKeys: List<String>): Map<String, NotiLlmState> = withContext(Dispatchers.IO) {
        if (notiKeys.isEmpty()) emptyMap()
        else llmStateDao.getByKeys(notiKeys).associateBy { it.notiKey }
    }

    /**
     * Stores a classification result without touching the scan gate flags.
     *
     * A user override ([NotiLlmState.categorySource] == "user") is never overwritten by a later
     * model pass; re-check bookkeeping still advances so the thread isn't re-enqueued forever.
     */
    suspend fun applyClassification(
        notiKey: String,
        categories: List<String>,
        reason: String,
        source: String,
        recordCount: Int,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = llmStateDao.getByKey(notiKey) ?: NotiLlmState(notiKey = notiKey)
        val keepUserOverride = existing.categorySource == "user" && source != "user"
        llmStateDao.upsert(
            existing.copy(
                categories = if (keepUserOverride) existing.categories else JSONArray(categories).toString(),
                categoryReason = if (keepUserOverride) existing.categoryReason else reason,
                categorySource = if (keepUserOverride) existing.categorySource else source,
                lastClassifiedAt = now,
                lastClassifiedRecordCount = recordCount,
                updatedAt = now,
            )
        )
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        llmStateDao.deleteAll()
    }
}
