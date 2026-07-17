package org.muilab.notigpt.data.remote.n8n.context

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.work.ListenableWorker
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.remote.n8n.N8nAPIClient
import org.muilab.notigpt.data.repository.notification.NotiLlmStateRepository
import org.muilab.notigpt.data.repository.notification.NotiRepository
import org.muilab.notigpt.data.repository.reminder.ExtractionJournalRepository
import org.muilab.notigpt.data.repository.reminder.PendingProposedOpRepository
import org.muilab.notigpt.data.repository.reminder.SavedItemChangeLogRepository
import org.muilab.notigpt.data.repository.reminder.SavedItemRepository
import org.muilab.notigpt.data.repository.reminder.SavedSubItemRepository
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Common dependencies for N8n worker handlers.
 */
class N8nWorkerContext @Inject constructor(
    @param:ApplicationContext val appContext: Context,
    val database: AppDatabase,
    val notiRepository: NotiRepository,
) {
    val n8nApiService by lazy { N8nAPIClient.n8nAPIService }

    val reminderRepository: SavedItemRepository by lazy {
        SavedItemRepository(database.reminderListDao(), appContext)
    }

    val savedSubItemRepository: SavedSubItemRepository by lazy {
        SavedSubItemRepository(database.subTaskDao())
    }

    val journalRepository: ExtractionJournalRepository by lazy {
        ExtractionJournalRepository(database.extractionJournalDao())
    }

    val notiLlmStateRepository: NotiLlmStateRepository by lazy {
        NotiLlmStateRepository(database.notiLlmStateDao())
    }

    val changeLogRepository: SavedItemChangeLogRepository by lazy {
        SavedItemChangeLogRepository(database.savedItemChangeLogDao())
    }

    private val pendingProposedOpRepository: PendingProposedOpRepository by lazy { PendingProposedOpRepository(appContext) }

    fun pendingProposedOpRepository(): PendingProposedOpRepository = pendingProposedOpRepository

/**
 * Shared dependency and payload helper context for n8n worker handlers.
 *
 * Handlers should use this context for repository access and common preference/user-context payloads. Keep it as
 * a convenience boundary, not a place for workflow-specific business logic.
 */
    suspend fun getExtractionPreferencesPayload(): List<Map<String, String>> {
        return try {
            database.extractionPreferenceDao().getAllPreferences().map { p ->
                mapOf(
                    "id" to p.id,
                    "statement" to p.statement,
                    "type" to p.preferenceType,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Returns the user's chosen target extraction language for n8n payloads.
     * Values: "original", "en", "zh-TW".
     */
    fun getTargetExtractionLanguage(): String {
        return SharedPreferencesManager.targetExtractionLanguage
    }

    /**
     * Returns the current user context facts formatted
     * for inclusion in n8n webhook payloads.
     */
    suspend fun getUserContextsPayload(): List<Map<String, String>> {
        return try {
            database.userContextDao().getAllContexts().map { c ->
                mapOf(
                    "id" to c.id,
                    "statement" to c.statement,
                    "category" to c.category,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Suppress("unused")
    fun getNotiUnit(notiKey: String) = notiRepository.getNotiUnit(notiKey)

    @Suppress("unused")
    fun getNotSyncedNotiActions(notiKey: String, sinceTimeMs: Long) = notiRepository.getNotSyncedNotiActions(notiKey, sinceTimeMs)

    fun success(): ListenableWorker.Result = ListenableWorker.Result.success()
    fun retry(): ListenableWorker.Result = ListenableWorker.Result.retry()
    fun failure(): ListenableWorker.Result = ListenableWorker.Result.failure()
}
