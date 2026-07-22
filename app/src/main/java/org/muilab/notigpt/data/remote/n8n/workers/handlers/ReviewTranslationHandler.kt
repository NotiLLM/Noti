package org.muilab.notigpt.data.remote.n8n.workers.handlers

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.data.remote.n8n.formatter.N8nRecordFormatter
import org.muilab.notigpt.data.remote.n8n.workers.N8nWorkerInput
import org.muilab.notigpt.model.features.ReviewTranslationState
import org.muilab.notigpt.model.features.TodoStep
import org.muilab.notigpt.util.SharedPreferencesManager
import java.util.Locale
import java.util.TimeZone

/** Pipeline F: translate review text only, then store a validated preview override for review. */
internal object ReviewTranslationHandler {
    private const val TAG = "N8nReviewTranslation"
    private const val MAX_ATTEMPTS = 3

    suspend fun handle(
        ctx: N8nWorkerContext,
        input: N8nWorkerInput.ReviewTranslation,
        runAttemptCount: Int,
    ): androidx.work.ListenableWorker.Result = try {
        handleInternal(ctx, input, runAttemptCount)
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Log.e(TAG, "Pipeline F failed before completing ${input.reviewKey}", t)
        clearPending(ctx, input.reviewKey)
        ctx.failure()
    }

    private suspend fun handleInternal(
        ctx: N8nWorkerContext,
        input: N8nWorkerInput.ReviewTranslation,
        runAttemptCount: Int,
    ): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        val dao = ctx.database.pendingReviewDraftDao()
        val draft = dao.getByKey(input.reviewKey) ?: return@withContext ctx.success()
        val state = ReviewTranslationState.fromJson(draft.translationStateJson)
            ?.takeIf { it.isPending }
            ?: return@withContext ctx.success()

        val records = if (state.evidenceRecordIds.isEmpty()) emptyList() else {
            ctx.database.recordDao().getRecordsByIds(state.evidenceRecordIds)
        }
        val keys = records.map { it.notiKey }.distinct()
        val units = if (keys.isEmpty()) emptyMap() else {
            ctx.database.drawerDao().getByNotiKeys(keys).associateBy { it.notiKey }
        }
        val payload = mapOf(
            "userId" to SharedPreferencesManager.userId,
            "language" to Locale.getDefault().toLanguageTag(),
            "timezone" to TimeZone.getDefault().id,
            "contractVersion" to 2,
            "reviewKey" to input.reviewKey,
            "targetLanguage" to state.targetLanguage,
            "currentItem" to state.sourceItem,
            "steps" to state.sourceSteps,
            "notiRecords" to records.sortedBy { it.time }.map { record ->
                N8nRecordFormatter.format(record, units[record.notiKey]?.isPeople ?: false)
            },
        )
        val body = Gson().toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())
        Log.d(TAG, "Posting reviewKey=${input.reviewKey} records=${records.size} attempt=$runAttemptCount")

        val response = try {
            ctx.n8nApiService.postToWebhook(input.webhookPath, body)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.w(TAG, "Pipeline F network failure for ${input.reviewKey}", t)
            return@withContext retryOrRestore(ctx, input.reviewKey, state, runAttemptCount)
        }
        if (!response.isSuccessful) {
            Log.w(TAG, "Pipeline F HTTP ${response.code()} for ${input.reviewKey}")
            return@withContext if (response.code() == 429 || response.code() in 500..599) {
                retryOrRestore(ctx, input.reviewKey, state, runAttemptCount)
            } else {
                clearIfMatching(ctx, input.reviewKey, state)
                ctx.failure()
            }
        }

        val translated = try {
            mergeTranslatedText(input.reviewKey, state, response.body()?.string().orEmpty())
        } catch (t: Throwable) {
            Log.w(TAG, "Pipeline F returned an invalid translation for ${input.reviewKey}", t)
            clearIfMatching(ctx, input.reviewKey, state)
            return@withContext ctx.failure()
        }

        val current = dao.getByKey(input.reviewKey) ?: return@withContext ctx.success()
        val currentState = ReviewTranslationState.fromJson(current.translationStateJson)
        if (currentState?.isPending == true && currentState.targetLanguage == state.targetLanguage) {
            dao.upsert(
                current.copy(
                    translationStateJson = ReviewTranslationState.toJson(translated),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        ctx.success()
    }

    private suspend fun retryOrRestore(
        ctx: N8nWorkerContext,
        reviewKey: String,
        state: ReviewTranslationState,
        runAttemptCount: Int,
    ): androidx.work.ListenableWorker.Result {
        if (runAttemptCount + 1 < MAX_ATTEMPTS) return ctx.retry()
        clearIfMatching(ctx, reviewKey, state)
        return ctx.failure()
    }

    private suspend fun clearIfMatching(
        ctx: N8nWorkerContext,
        reviewKey: String,
        expected: ReviewTranslationState,
    ) {
        val dao = ctx.database.pendingReviewDraftDao()
        val current = dao.getByKey(reviewKey) ?: return
        val state = ReviewTranslationState.fromJson(current.translationStateJson) ?: return
        if (state.isPending && state.targetLanguage == expected.targetLanguage) {
            dao.upsert(current.copy(translationStateJson = null, updatedAt = System.currentTimeMillis()))
        }
    }

    private suspend fun clearPending(ctx: N8nWorkerContext, reviewKey: String) {
        val dao = ctx.database.pendingReviewDraftDao()
        val current = dao.getByKey(reviewKey) ?: return
        val state = ReviewTranslationState.fromJson(current.translationStateJson) ?: return
        if (state.isPending) {
            dao.upsert(current.copy(translationStateJson = null, updatedAt = System.currentTimeMillis()))
        }
    }

    /** Copies translated strings onto the exact source snapshot; every structural field stays local. */
    internal fun mergeTranslatedText(
        expectedReviewKey: String,
        source: ReviewTranslationState,
        responseJson: String,
    ): ReviewTranslationState {
        val response = JsonParser.parseString(responseJson).asJsonObject
        require(requiredString(response, "reviewKey") == expectedReviewKey) { "reviewKey mismatch" }
        require(requiredString(response, "targetLanguage") == source.targetLanguage) { "targetLanguage mismatch" }
        val title = requiredString(response, "title")
        val content = requiredString(response, "content")

        val sourceSubs = source.sourceSteps.associateBy { it.todoStepId }
        val translatedSubs = response.getAsJsonArray("steps") ?: error("steps is required")
        val subText = mutableMapOf<String, String>()
        for (element in translatedSubs) {
            val obj = element.asJsonObject
            val id = requiredString(obj, "todoStepId")
            require(id in sourceSubs && id !in subText) { "Unknown or duplicate step" }
            subText[id] = TodoStep.normalizeText(requiredString(obj, "text"))
        }
        require(subText.keys == sourceSubs.keys) { "Step set changed" }

        val sourceButtons = JsonParser.parseString(source.sourceItem.buttons).asJsonArray
        val translatedButtons = response.getAsJsonArray("buttons") ?: error("buttons is required")
        val labels = mutableMapOf<Int, String>()
        for (element in translatedButtons) {
            val obj = element.asJsonObject
            val buttonIndex = obj.get("index")?.asInt ?: error("index is required")
            require(buttonIndex in 0 until sourceButtons.size() && buttonIndex !in labels) {
                "Unknown or duplicate button"
            }
            labels[buttonIndex] = requiredString(obj, "buttonText")
        }
        require(labels.keys == (0 until sourceButtons.size()).toSet()) { "Button set changed" }
        val mergedButtons = sourceButtons.deepCopy()
        for (index in 0 until mergedButtons.size()) {
            mergedButtons[index].asJsonObject.addProperty("buttonText", labels.getValue(index))
        }

        return source.copy(
            status = ReviewTranslationState.STATUS_READY,
            translatedItem = source.sourceItem.copy(
                title = title,
                content = content,
                buttons = mergedButtons.toString(),
            ),
            translatedSteps = source.sourceSteps.map { sub ->
                sub.copy(text = subText.getValue(sub.todoStepId))
            },
        )
    }

    private fun requiredString(obj: JsonObject, key: String): String {
        val value = obj.get(key)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$key is required" }
        return value.asString
    }
}
