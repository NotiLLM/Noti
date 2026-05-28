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
import org.muilab.notigpt.util.Constants.Companion.DIFY_UPDATE_NOTIFICATION
import java.util.concurrent.TimeUnit
import androidx.work.ExistingWorkPolicy

// You can rename these fields if you want, n8n doesn't care about the names.
// Just make your workflow's Webhook node expect these keys.
data class N8nUpdateNotificationPayload(
    val userId: String,
    val noti_contents_str: String,
    val noti_actions_str: String,
    val noti_past_summary: String
)

// === WorkManager enqueuers ===

// Still using the same api_type flag; you can rename them later if you want.
fun enqueueUpdateNotification(context: Context, notiKey: String) {
    val inputData = Data.Builder()
        .putString("api_type", DIFY_UPDATE_NOTIFICATION)
        .putString("noti_key", notiKey)
        // Let the Worker know which n8n webhook to call
        .putString("webhook_path", BuildConfig.N8N_UPDATE_NOTIFICATION_PATH)
        .build()

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workerRequest = OneTimeWorkRequestBuilder<N8nAPIWorker>()
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setInputData(inputData)
        .build()

    val uniqueName = "n8n_update_notification_$notiKey"
    WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, workerRequest)
}

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
  * Enqueue a unique delayed extraction work. Each call will replace the previous one so
  * the timer restarts when another trigger comes in. This makes the debounce robust even
  * when the app process dies or is backgrounded.
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

 fun enqueueRegenerateOne(context: Context, reminderId: String) {
    Log.d("N8nAPI", "enqueueRegenerateOne: reminderId=$reminderId")
    val inputData = Data.Builder()
        .putString("api_type", N8N_REGENERATE_ONE)
        .putString("webhook_path", BuildConfig.N8N_REGENERATE_ONE_PATH)
        .putString("reminder_id", reminderId)
        .build()

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workerRequest = OneTimeWorkRequestBuilder<N8nAPIWorker>()
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setInputData(inputData)
        .build()

    val uniqueName = "n8n_regenerate_one_$reminderId"
    WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, workerRequest)
 }

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

 fun enqueueRerank(context: Context, reminderId: String, trigger: String) {
    Log.d("N8nAPI", "enqueueRerank: reminderId=$reminderId trigger=$trigger")
    val inputData = Data.Builder()
        .putString("api_type", N8N_RERANK)
        .putString("webhook_path", BuildConfig.N8N_RERANK_PATH)
        .putString("reminder_id", reminderId)
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

    val uniqueName = "n8n_rerank_${reminderId}_$trigger"
    WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, workerRequest)
 }

