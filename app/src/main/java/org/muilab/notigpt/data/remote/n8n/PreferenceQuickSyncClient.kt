package org.muilab.notigpt.data.remote.n8n

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.model.features.ConflictDto
import org.muilab.notigpt.model.features.ExtractionPreference
import org.muilab.notigpt.model.features.QuickSyncRequest
import org.muilab.notigpt.model.features.QuickSyncResponse
import org.muilab.notigpt.model.features.PreferencePlain

/**
 * Direct client for quick-syncing local preference selections to n8n.
 *
 * Keep this as the synchronous network path for preference replacement responses. Background scheduling and
 * conflict application belong to the worker handler or preference repository layer.
 */
object PreferenceQuickSyncClient {

    private const val TAG = "PrefQuickSyncClient"

    suspend fun sync(request: QuickSyncRequest): QuickSyncResponse? {
        val gson = Gson()
        val json = gson.toJson(request)
        Log.d(TAG, "Request: $json")

        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val webhookPath = BuildConfig.N8N_PREFERENCE_QUICK_SYNC_PATH

        val response = try {
            N8nAPIClient.n8nAPIService.postToWebhook(webhookPath, requestBody)
        } catch (t: Throwable) {
            Log.e(TAG, "Network error", t)
            return null
        }

        if (!response.isSuccessful) {
            Log.e(TAG, "HTTP ${response.code()}")
            return null
        }

        val bodyStr = response.body()?.string()
        if (bodyStr.isNullOrBlank()) {
            Log.w(TAG, "Empty response body")
            return null
        }

        Log.d(TAG, "Response: $bodyStr")

        return try {
            val root = gson.fromJson(bodyStr, Map::class.java)
            val status = root["status"]?.toString() ?: "unknown"
            val toastMessage = root["toastMessage"]?.toString()

            @Suppress("UNCHECKED_CAST")
            val prefsRaw = root["updatedPreferences"] as? List<Map<String, Any>> ?: emptyList()
            val prefs = prefsRaw.map { m ->
                PreferencePlain(
                    id = m["id"]?.toString() ?: "",
                    statement = m["statement"]?.toString() ?: "",
                    type = m["type"]?.toString() ?: "",
                )
            }

            @Suppress("UNCHECKED_CAST")
            val conflictsRaw = root["conflicts"] as? List<Map<String, Any?>> ?: emptyList()
            val conflicts = conflictsRaw.map { c ->
                @Suppress("UNCHECKED_CAST")
                val ids = (c["involvedPreferenceIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                ConflictDto(
                    conflictId = c["conflictId"]?.toString() ?: "",
                    description = c["description"]?.toString() ?: "",
                    involvedPreferenceIds = ids,
                )
            }

            QuickSyncResponse(
                status = status,
                updatedPreferences = prefs,
                toastMessage = toastMessage,
                conflicts = conflicts,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
            null
        }
    }
}



