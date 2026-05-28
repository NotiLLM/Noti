package org.muilab.notigpt.data.remote.n8n.workers.handlers.utils

import android.content.Context
import androidx.work.ListenableWorker
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.data.remote.n8n.N8nAPIClient
import org.muilab.notigpt.repository.NotiRepository
import org.muilab.notigpt.repository.NotiRepositoryProvider
import org.muilab.notigpt.repository.ReminderRepository
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Common dependencies for N8n worker handlers.
 */
internal class N8nWorkerContext(
    val appContext: Context,
) {
    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val n8nApiService by lazy { N8nAPIClient.n8nAPIService }

    val notiRepository: NotiRepository by lazy {
        NotiRepositoryProvider.provideNotiRepository(appContext)
    }

    val reminderRepository: ReminderRepository by lazy {
        ReminderRepository(database.reminderListDao(), appContext)
    }

    /**
     * Returns the current active extraction-preference statements formatted
     * for inclusion in n8n webhook payloads.
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
