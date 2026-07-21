package org.muilab.notigpt.data.remote.n8n.workers.handlers

import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import com.google.gson.Gson
import com.google.firebase.auth.FirebaseAuth
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.model.features.ExtractionPreference
import org.muilab.notigpt.model.features.FirestoreOutboxOp
import org.muilab.notigpt.work.FirestoreOutboxWork

/**
 * Worker handler for applying n8n preference quick-sync responses.
 *
 * This handler sends the queued preference payload, interprets conflicts, and updates local preference state.
 * Keep request scheduling in N8nAPI and durable rule writes behind repository APIs.
 */
internal object PreferenceQuickSyncHandler {

    private const val TAG = "PrefQuickSync"

    suspend fun handle(ctx: N8nWorkerContext, inputData: Data): ListenableWorker.Result {
        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e(TAG, "Missing webhook_path")
            return ctx.failure()
        }
        val payloadJson = inputData.getString("payload_json") ?: run {
            Log.e(TAG, "Missing payload_json")
            return ctx.failure()
        }

        val requestBody = payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType())

        val response = try {
            ctx.n8nApiService.postToWebhook(webhookPath, requestBody)
        } catch (t: Throwable) {
            Log.e(TAG, "Network error", t)
            return ctx.retry()
        }

        if (!response.isSuccessful) {
            return when {
                response.code() == 429 -> ctx.retry()
                response.code() in 500..599 -> ctx.retry()
                else -> ctx.failure()
            }
        }

        val bodyStr = response.body()?.string()
        if (bodyStr.isNullOrBlank()) {
            Log.w(TAG, "Empty response body; treating as success with no changes")
            return ctx.success()
        }

        Log.d(TAG, "Response bytes=${bodyStr.length}")

        try {
            val gson = Gson()
            val root = gson.fromJson(bodyStr, Map::class.java)

            @Suppress("UNCHECKED_CAST")
            val prefsRaw = root["updatedPreferences"] as? List<Map<String, Any>> ?: emptyList()

            val now = System.currentTimeMillis()
            val prefs = prefsRaw.map { m ->
                ExtractionPreference(
                    id = m["id"]?.toString() ?: "",
                    statement = m["statement"]?.toString() ?: "",
                    preferenceType = m["type"]?.toString() ?: "",
                    createdAt = now,
                    updatedAt = now,
                )
            }

            val prefDao = ctx.database.extractionPreferenceDao()
            prefDao.replacePreferences(prefs)
            Log.d(TAG, "Replaced local preferences with ${prefs.size} rules")
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            if (uid.isNotBlank()) {
                ctx.database.firestoreOutboxDao().upsert(
                    FirestoreOutboxOp.preferencesAndContexts(uid, System.currentTimeMillis())
                )
                FirestoreOutboxWork.enqueue(ctx.appContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing quick-sync response", e)
            // Don't fail the worker for a parsing error; the preference push itself succeeded.
        }

        return ctx.success()
    }
}
