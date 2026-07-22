package org.muilab.notigpt.data.remote.googletasks

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.export.ExportableItem
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.data.export.asExportable
import org.muilab.notigpt.data.remote.googletasks.GoogleTasksAuthManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Repository for interacting with Google Tasks API.
 *
 * This creates tasks in the user's default task list.
 */
class GoogleTasksRepository(
    private val appContext: Context,
) {
    companion object {
        private const val TAG = "GoogleTasksRepo"
        private const val APP_NAME = "NotiGPT"
    }

    /**
     * Result wrapper for task creation operations.
     */
    sealed class TaskResult {
        data class Success(val taskId: String, val taskTitle: String) : TaskResult()
        data class Error(val message: String, val exception: Throwable? = null) : TaskResult()
        object NotSignedIn : TaskResult()
    }

    /**
     * Build the Google Tasks API service using the signed-in account.
     */
    private fun buildTasksService(account: GoogleSignInAccount): Tasks {
        val credential = GoogleAccountCredential.usingOAuth2(
            appContext,
            listOf(TasksScopes.TASKS)
        ).apply {
            selectedAccount = account.account
        }

        return Tasks.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(APP_NAME)
            .build()
    }

    /**
     * Create a task in the user's default task list from any [ExportableItem]
     * (works for both [SavedItem] and [TodoStep]).
     */
    suspend fun createTaskFromExportable(item: ExportableItem): TaskResult = withContext(Dispatchers.IO) {
        val account = GoogleTasksAuthManager.getAccount(appContext)
        if (account == null) {
            Log.w(TAG, "User not signed in to Google")
            return@withContext TaskResult.NotSignedIn
        }

        try {
            val service = buildTasksService(account)

            val task = Task().apply {
                title = item.exportTitle.ifBlank { "(Untitled)" }
                if (item.exportDescription.isNotBlank()) notes = item.exportDescription
                status = if (item.exportIsCompleted) "completed" else "needsAction"
                if (item.exportIsCompleted) completed = formatRfc3339(System.currentTimeMillis())
                if (item.exportDeadlineTimestamp > 0L) due = formatRfc3339(item.exportDeadlineTimestamp)
            }

            val createdTask = service.tasks().insert("@default", task).execute()
            Log.d(TAG, "Task created: id=${createdTask.id}, title=${createdTask.title}")
            TaskResult.Success(taskId = createdTask.id ?: "", taskTitle = createdTask.title ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create task", e)
            TaskResult.Error(message = e.message ?: "Unknown error", exception = e)
        }
    }

    /**
     * Create a task in the user's default task list from a SavedItem.
     */
    suspend fun createTaskFromSavedItem(reminder: SavedItem): TaskResult =
        createTaskFromExportable(reminder.asExportable())

    /**
     * Format timestamp as RFC 3339 date-time string for Google Tasks API.
     */
    private fun formatRfc3339(timestampMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestampMs))
    }
}
