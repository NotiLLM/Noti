package org.muilab.notigpt.data.remote.n8n.context

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.work.ListenableWorker
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.remote.n8n.N8nAPIClient
import org.muilab.notigpt.data.remote.n8n.PersonalizationPayloadBuilder
import org.muilab.notigpt.data.repository.notification.NotiLlmStateRepository
import org.muilab.notigpt.data.repository.notification.NotiRepository
import org.muilab.notigpt.data.repository.personalization.PersonalizationRepository
import org.muilab.notigpt.data.repository.saveditem.ExtractionJournalRepository
import org.muilab.notigpt.data.repository.saveditem.PendingProposedOpRepository
import org.muilab.notigpt.data.repository.saveditem.SavedItemChangeLogRepository
import org.muilab.notigpt.data.repository.saveditem.SavedItemRepository
import org.muilab.notigpt.data.repository.saveditem.TodoStepRepository
import org.muilab.notigpt.util.SharedPreferencesManager
import java.util.Locale

/**
 * Common dependencies for N8n worker handlers.
 */
class N8nWorkerContext @Inject constructor(
    @param:ApplicationContext val appContext: Context,
    val database: AppDatabase,
    val notiRepository: NotiRepository,
    private val personalizationRepository: PersonalizationRepository,
) {
    val n8nApiService by lazy { N8nAPIClient.n8nAPIService }

    val savedItemRepository: SavedItemRepository by lazy {
        SavedItemRepository(database.savedItemDao(), appContext)
    }

    val todoStepRepository: TodoStepRepository by lazy {
        TodoStepRepository(database.todoStepDao())
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

    /** Creates one immutable stage matrix from a single confirmed repository snapshot. */
    suspend fun personalizationPayloadBuilder(
        itemLanguage: String = SharedPreferencesManager.targetExtractionLanguage,
    ): PersonalizationPayloadBuilder = PersonalizationPayloadBuilder(
        snapshots = personalizationRepository.getConfirmedSnapshots(),
        uiLanguage = Locale.getDefault().toLanguageTag(),
        itemLanguage = itemLanguage,
    )

    fun getNotiUnit(notiKey: String) = notiRepository.getNotiUnit(notiKey)

    fun success(): ListenableWorker.Result = ListenableWorker.Result.success()
    fun retry(): ListenableWorker.Result = ListenableWorker.Result.retry()
    fun failure(): ListenableWorker.Result = ListenableWorker.Result.failure()
}
