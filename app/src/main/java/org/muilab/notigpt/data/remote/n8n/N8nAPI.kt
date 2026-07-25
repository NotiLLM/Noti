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
import org.muilab.notigpt.util.Constants.Companion.N8N_REVIEW_TRANSLATION
import org.muilab.notigpt.util.Constants.Companion.N8N_REGENERATE_ONE
import org.muilab.notigpt.util.Constants.Companion.N8N_SPLIT_ONE
import org.muilab.notigpt.util.Constants.Companion.N8N_SUGGESTION_REFRESH
import org.muilab.notigpt.data.repository.suggestion.SuggestionSnapshotStore
import org.muilab.notigpt.util.SharedPreferencesManager
import java.util.concurrent.TimeUnit
import androidx.work.ExistingWorkPolicy

// === WorkManager enqueuers ===

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
 * `pending_proposed_op` for review.
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
 * (E2), staged for review. Change-driven calls replace and restart a delayed request; the daily
 * safety net keeps any already-bounded request in the same unique slot.
 */
fun enqueueReflectionPipeline(
    context: Context,
    initialDelayMs: Long = 0L,
    replaceExisting: Boolean = false,
    now: Long = System.currentTimeMillis(),
) {
    if (!isSignedIn()) {
        Log.d("N8nAPI", "Skipping reflection pipeline: not signed in")
        return
    }
    Log.d("N8nAPI", "enqueueReflectionPipeline: delayMs=$initialDelayMs replace=$replaceExisting")
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
        .setInitialDelay(initialDelayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork(
            "n8n_reflection_pipeline",
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            workerRequest,
        )
    SharedPreferencesManager.lastReflectionAttemptTime = now
}

/**
 * Queues one G→H Suggested evaluation. Automatic callers respect the six-hour snapshot window;
 * opening/refreshing Suggested may force an immediate run. KEEP prevents overlapping evaluations.
 */
fun enqueueSuggestionRefresh(context: Context, force: Boolean = false) {
    if (!isSignedIn()) {
        Log.d("N8nAPI", "Skipping suggestion refresh: not signed in")
        return
    }
    val store = SuggestionSnapshotStore.getInstance(context)
    if (!force && !store.isRefreshDue()) return
    // Reflect queued/waiting work immediately so opening an empty Suggested page never flashes a
    // misleading empty state while WorkManager is waiting for its network constraint.
    store.beginRefresh()

    val inputData = Data.Builder()
        .putString("api_type", N8N_SUGGESTION_REFRESH)
        .putString("webhook_path", BuildConfig.N8N_SUGGEST_G_SHORTLIST_PATH)
        .build()
    val request = OneTimeWorkRequestBuilder<N8nAPIWorker>()
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInputData(inputData)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "n8n_suggestion_refresh",
        ExistingWorkPolicy.KEEP,
        request,
    )
}

/** Queues translation-only Pipeline F for one durable review-draft snapshot. */
fun enqueueReviewTranslation(context: Context, reviewKey: String) {
    if (reviewKey.isBlank()) return
    val inputData = Data.Builder()
        .putString("api_type", N8N_REVIEW_TRANSLATION)
        .putString("webhook_path", BuildConfig.N8N_EXTRACT_F_TRANSLATION_PATH)
        .putString("review_key", reviewKey)
        .build()
    val request = OneTimeWorkRequestBuilder<N8nAPIWorker>()
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        )
        .setInputData(inputData)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "n8n_review_translation_$reviewKey",
        ExistingWorkPolicy.REPLACE,
        request,
    )
}

/** Queues regeneration for one SavedItem from its stored notification context. */
fun enqueueRegenerateOne(context: Context, savedItemId: String) {
    Log.d("N8nAPI", "enqueueRegenerateOne: savedItemId=$savedItemId")
    val inputData = Data.Builder()
        .putString("api_type", N8N_REGENERATE_ONE)
        .putString("webhook_path", BuildConfig.N8N_REGENERATE_ONE_PATH)
        .putString("saved_item_id", savedItemId)
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

/** Queues one user-requested Split. KEEP enforces one in-flight transform per source. */
fun enqueueSplitOne(context: Context, savedItemId: String) {
    if (!isSignedIn() || savedItemId.isBlank()) return
    Log.d("N8nAPI", "enqueueSplitOne: savedItemId=$savedItemId")
    val inputData = Data.Builder()
        .putString("api_type", N8N_SPLIT_ONE)
        .putString("webhook_path", BuildConfig.N8N_SPLIT_ONE_PATH)
        .putString("saved_item_id", savedItemId)
        .build()
    val request = OneTimeWorkRequestBuilder<N8nAPIWorker>()
        // The workflow owns its single per-pass repair attempt. Do not restart the full two-pass
        // pipeline after a terminal response or malformed result.
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInputData(inputData)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "n8n_split_one_$savedItemId",
        ExistingWorkPolicy.KEEP,
        request,
    )
}
