package org.muilab.notigpt.data.remote.n8n

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.data.remote.n8n.workers.N8nAPIWorker
import org.muilab.notigpt.util.Constants.Companion.N8N_TASK_SCAN
import org.muilab.notigpt.util.Constants.Companion.N8N_TASK_EXTRACTION
import org.muilab.notigpt.util.Constants.Companion.N8N_REGENERATE_ONE
import org.muilab.notigpt.util.Constants.Companion.N8N_REGENERATE_ALL
import org.muilab.notigpt.util.Constants.Companion.N8N_RERANK
import org.muilab.notigpt.util.Constants.Companion.DIFY_POST_NOTIFICATION_ACTION
import java.util.concurrent.TimeUnit
import androidx.work.ExistingWorkPolicy

/**
 * Payload shape for the notification-update webhook.
 *
 * Field names intentionally match the n8n workflow contract. Keep transport fields here and use worker handlers
 * to build the payload from app models.
 */
data class N8nUpdateNotificationPayload(
    val userId: String,
    val noti_contents_str: String,
    val noti_actions_str: String,
    val noti_past_summary: String
)

// === WorkManager enqueuers ===

/**
 * Queues one user action event for backend delivery.
 *
 * The unique work name is scoped by notification key and action type so duplicate taps do not accumulate
 * unbounded jobs while an equivalent action is already pending.
 */
fun enqueueNotificationAction(
    context: Context,
    notiKey: String,
    actionType: String,
    actionTime: Long = System.currentTimeMillis()
) {
    val inputData = Data.Builder()
        .putString("api_type", DIFY_POST_NOTIFICATION_ACTION)
        .putString("noti_key", notiKey)
        .putString("action_type", actionType)
        .putLong("action_time", actionTime)
        .putString("webhook_path", BuildConfig.N8N_POST_NOTIFICATION_ACTION_PATH)
        .build()

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workerRequest = OneTimeWorkRequestBuilder<N8nAPIWorker>()
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setInputData(inputData)
        .build()

    val uniqueName = "n8n_post_action_${notiKey}_$actionType"
    WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, workerRequest)
}

/**
 * Queues reminder scan for one notification key.
 *
 * A per-key KEEP policy bounds scheduler growth and lets an in-flight scan finish before another identical scan
 * is added. Extraction is scheduled separately after scan acceptance.
 */
fun enqueueTaskScan(context: Context, notiKey: String) {
    Log.d("N8nAPI", "enqueueTaskScan: key=$notiKey")
     val inputData = Data.Builder()
         .putString("api_type", N8N_TASK_SCAN)
         .putString("noti_key", notiKey)
         .putString("webhook_path", BuildConfig.N8N_TASK_SCAN_PATH)
         .build()

     val constraints = Constraints.Builder()
         .setRequiredNetworkType(NetworkType.CONNECTED)
         .build()

     val workerRequest = OneTimeWorkRequestBuilder<N8nAPIWorker>()
         .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
         .setConstraints(constraints)
         .setInputData(inputData)
         .build()

     // Use per-key unique name to prevent unbounded job accumulation in JobScheduler.
     // KEEP: if a scan for this key is already pending/running, don't restart it.
     WorkManager.getInstance(context)
         .enqueueUniqueWork("n8n_task_scan_$notiKey", ExistingWorkPolicy.KEEP, workerRequest)
 }

/**
 * Queues reminder extraction for a set of notification keys or visible record ids.
 *
 * Extraction uses a single REPLACE slot so the latest selected/eligible context wins and the worker queue remains
 * bounded. Use visibleRecordIds for user-triggered extraction from explicit UI context.
 */
fun enqueueTaskExtraction(
    context: Context,
    notiKeys: List<String>,
    userTriggered: Boolean = false,
    visibleRecordIds: List<String> = emptyList(),
) {
    Log.d("N8nAPI", "enqueueTaskExtraction: keys=${notiKeys.size} ${notiKeys.take(5)} userTriggered=$userTriggered visibleRecordIds=${visibleRecordIds.size}")
    val inputDataBuilder = Data.Builder()
        .putString("api_type", N8N_TASK_EXTRACTION)
        .putString("webhook_path", BuildConfig.N8N_TASK_EXTRACTION_PATH)
        .putBoolean("user_triggered", userTriggered)

    val gson = com.google.gson.Gson()
    inputDataBuilder.putString("noti_keys_json", gson.toJson(notiKeys))

    if (visibleRecordIds.isNotEmpty()) {
        inputDataBuilder.putString("visible_record_ids_json", gson.toJson(visibleRecordIds))
    }

    val inputData = inputDataBuilder.build()

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workerRequest = OneTimeWorkRequestBuilder<N8nAPIWorker>()
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setInputData(inputData)
        .build()

    // Single unique slot for all extraction work: REPLACE so newer key lists win and the
    // job count stays bounded. Consistent with enqueueDelayedTaskExtraction.
    WorkManager.getInstance(context)
        .enqueueUniqueWork("n8n_task_extraction", ExistingWorkPolicy.REPLACE, workerRequest)
 }

/**
 * Queues delayed extraction after scan/capture activity settles.
 *
 * This shares the extraction unique-work slot with immediate extraction. A newer extraction intent restarts the
 * debounce timer and prevents overlapping backend jobs.
 */
fun enqueueDelayedTaskExtraction(context: Context, delaySeconds: Long, userTriggered: Boolean = false) {
    Log.d("N8nAPI", "enqueueDelayedTaskExtraction: delaySeconds=$delaySeconds userTriggered=$userTriggered")
     val inputData = Data.Builder()
         .putString("api_type", N8N_TASK_EXTRACTION)
         .putString("webhook_path", BuildConfig.N8N_TASK_EXTRACTION_PATH)
         .putBoolean("user_triggered", userTriggered)
         // No noti_keys_json passed -> worker will query DB for shouldExtractTask==true
         .putString("noti_keys_json", "[]")
         .build()

     val constraints = Constraints.Builder()
         .setRequiredNetworkType(NetworkType.CONNECTED)
         .build()

     val workerRequest = OneTimeWorkRequestBuilder<N8nAPIWorker>()
         .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
         .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
         .setConstraints(constraints)
         .setInputData(inputData)
         .build()

     // Use a unique name so subsequent triggers replace the scheduled work, restarting the timer.
     WorkManager.getInstance(context)
         .enqueueUniqueWork("n8n_task_extraction_debounce", ExistingWorkPolicy.REPLACE, workerRequest)
 }

/** Queues regeneration for one reminder from its stored notification context. */
fun enqueueRegenerateOne(context: Context, savedItemId: String) {
    Log.d("N8nAPI", "enqueueRegenerateOne: savedItemId=$savedItemId")
    val inputData = Data.Builder()
        .putString("api_type", N8N_REGENERATE_ONE)
        .putString("webhook_path", BuildConfig.N8N_REGENERATE_ONE_PATH)
        .putString("reminder_id", savedItemId)
        .build()

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workerRequest = OneTimeWorkRequestBuilder<N8nAPIWorker>()
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setInputData(inputData)
        .build()

    val uniqueName = "n8n_regenerate_one_$savedItemId"
    WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, workerRequest)
 }

/** Queues regeneration for all currently visible reminders. */
fun enqueueRegenerateAll(context: Context) {
    Log.d("N8nAPI", "enqueueRegenerateAll")
    val inputData = Data.Builder()
        .putString("api_type", N8N_REGENERATE_ALL)
        .putString("webhook_path", BuildConfig.N8N_REGENERATE_ALL_PATH)
        .build()

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workerRequest = OneTimeWorkRequestBuilder<N8nAPIWorker>()
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setInputData(inputData)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork("n8n_regenerate_all", ExistingWorkPolicy.REPLACE, workerRequest)
 }

/** Queues reminder reranking for one feedback-triggered reminder update. */
fun enqueueRerank(context: Context, savedItemId: String, trigger: String) {
    Log.d("N8nAPI", "enqueueRerank: savedItemId=$savedItemId trigger=$trigger")
    val inputData = Data.Builder()
        .putString("api_type", N8N_RERANK)
        .putString("webhook_path", BuildConfig.N8N_RERANK_PATH)
        .putString("reminder_id", savedItemId)
        .putString("trigger", trigger)
        .build()

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workerRequest = OneTimeWorkRequestBuilder<N8nAPIWorker>()
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setInputData(inputData)
        .build()

    val uniqueName = "n8n_rerank_${savedItemId}_$trigger"
    WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, workerRequest)
 }

