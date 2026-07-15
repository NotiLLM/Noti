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
import org.muilab.notigpt.util.Constants.Companion.N8N_EXTRACTION_PIPELINE
import org.muilab.notigpt.util.Constants.Companion.N8N_REFLECTION_PIPELINE
import org.muilab.notigpt.util.Constants.Companion.N8N_REGENERATE_ONE
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

private fun isSignedIn(): Boolean =
    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null

/** Per-notiKey extraction work slot; forced and auto runs share it so they never overlap. */
private fun extractionWorkName(notiKey: String) = "n8n_extraction_${notiKey}"

/**
 * Queues the per-notiKey extraction pipeline (contract v3).
 *
 * One worker job runs the pipeline stages sequentially — auto runs start at scan (A), then
 * item-extraction (B), summary fold (C), merge shortlist (D1) and merge resolution (E1); a forced
 * run (manual "extract" from a notification) skips scan and starts at B. Staged ops land in
 * `pending_op` for review.
 *
 * The slot is per-key: an auto run KEEPs (an in-flight run for the key is left to finish), while a
 * forced run REPLACEs so a manual trigger preempts any pending auto run for the same thread.
 */
fun enqueueExtractionPipeline(context: Context, notiKey: String, forced: Boolean = false) {
    if (!isSignedIn()) {
        Log.d("N8nAPI", "Skipping extraction pipeline: not signed in")
        return
    }
    if (notiKey.isBlank()) return
    Log.d("N8nAPI", "enqueueExtractionPipeline: key=$notiKey forced=$forced")
    val inputData = Data.Builder()
        .putString("api_type", N8N_EXTRACTION_PIPELINE)
        // The pipeline calls several stage webhooks; each stage picks its own path from BuildConfig.
        .putString("webhook_path", BuildConfig.N8N_EXTRACT_A_SCAN_PATH)
        .putString("noti_key", notiKey)
        .putBoolean("forced", forced)
        .build()

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workerRequest = OneTimeWorkRequestBuilder<N8nAPIWorker>()
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .setConstraints(constraints)
        .setInputData(inputData)
        .addTag(EXTRACTION_WORK_TAG)
        .build()

    val policy = if (forced) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
    WorkManager.getInstance(context)
        .enqueueUniqueWork(extractionWorkName(notiKey), policy, workerRequest)
}

/** Shared tag on every per-notiKey extraction job, so the UI can observe "any extraction running". */
const val EXTRACTION_WORK_TAG = "n8n_extraction"

/**
 * Queues the periodic reflection pass (cross-thread merge): grouping (D2) then merge resolution
 * (E2), staged for review. A single KEEP slot bounds scheduler growth.
 */
fun enqueueReflectionPipeline(context: Context) {
    if (!isSignedIn()) {
        Log.d("N8nAPI", "Skipping reflection pipeline: not signed in")
        return
    }
    Log.d("N8nAPI", "enqueueReflectionPipeline")
    val inputData = Data.Builder()
        .putString("api_type", N8N_REFLECTION_PIPELINE)
        .putString("webhook_path", BuildConfig.N8N_EXTRACT_D2_GROUPING_PATH)
        .build()

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workerRequest = OneTimeWorkRequestBuilder<N8nAPIWorker>()
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .setConstraints(constraints)
        .setInputData(inputData)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork("n8n_reflection_pipeline", ExistingWorkPolicy.KEEP, workerRequest)
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

