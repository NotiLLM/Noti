package org.muilab.notigpt.database.server.workers.n8n

import android.content.Context
import androidx.work.ListenableWorker
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.database.server.N8nAPIClient
import org.muilab.notigpt.repository.NotiRepository
import org.muilab.notigpt.repository.NotiRepositoryProvider
import org.muilab.notigpt.repository.TaskRepository
import org.muilab.notigpt.repository.TaskRepositoryProvider

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

    val taskRepository: TaskRepository by lazy {
        TaskRepositoryProvider.provideTaskRepository(appContext)
    }

    @Suppress("unused")
    fun getNotiUnit(notiKey: String) = notiRepository.getNotiUnit(notiKey)

    @Suppress("unused")
    fun getNotSyncedNotiActions(notiKey: String, sinceTimeMs: Long) = notiRepository.getNotSyncedNotiActions(notiKey, sinceTimeMs)

    fun success(): ListenableWorker.Result = ListenableWorker.Result.success()
    fun retry(): ListenableWorker.Result = ListenableWorker.Result.retry()
    fun failure(): ListenableWorker.Result = ListenableWorker.Result.failure()
}
