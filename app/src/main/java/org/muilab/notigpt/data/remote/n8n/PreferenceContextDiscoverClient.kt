package org.muilab.notigpt.data.remote.n8n

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.model.features.ChatInteractResponse
import org.muilab.notigpt.model.features.ConflictDto
import org.muilab.notigpt.model.features.ContextDiscoverRequest
import org.muilab.notigpt.model.features.ProposedActionDto

/**
 * Direct client for context-discovery requests used by preference flows.
 *
 * This client shares response normalization with preference chat but represents a separate backend intent. Keep
 * discovery parsing here and store accepted contexts through the repository/ViewModel layer.
 */
object PreferenceContextDiscoverClient {

    private const val TAG = "CtxDiscoverClient"

    suspend fun discover(request: ContextDiscoverRequest): ChatInteractResponse? {
        val gson = Gson()
        val json = gson.toJson(request)
        Log.d(TAG, "Request: $json")

        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val webhookPath = BuildConfig.N8N_CONTEXT_DISCOVER_PATH

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
            val assistantMessage = root["assistantMessage"]?.toString() ?: ""

            @Suppress("UNCHECKED_CAST")
            val actionsRaw = root["proposedActions"] as? List<Map<String, Any?>> ?: emptyList()
            val actions = actionsRaw.map { m ->
                ProposedActionDto(
                    actionId = m["actionId"]?.toString() ?: "",
                    type = m["type"]?.toString() ?: "ADD",
                    targetPreferenceId = m["targetPreferenceId"]?.toString(),
                    newStatement = m["newStatement"]?.toString(),
                    newPreferenceType = m["newPreferenceType"]?.toString(),
                    targetType = m["targetType"]?.toString() ?: "CONTEXT",
                )
            }
            ChatInteractResponse(
                assistantMessage = assistantMessage,
                proposedActions = actions,
                conflicts = parseConflicts(root),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseConflicts(root: Map<*, *>): List<ConflictDto> {
        val raw = root["conflicts"] as? List<Map<String, Any?>> ?: return emptyList()
        return raw.map { c ->
            val ids = (c["involvedPreferenceIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            ConflictDto(
                conflictId = c["conflictId"]?.toString() ?: "",
                description = c["description"]?.toString() ?: "",
                involvedPreferenceIds = ids,
            )
        }
    }
}

