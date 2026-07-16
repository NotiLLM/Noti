package org.muilab.notigpt.data.remote.n8n

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.data.remote.n8n.dto.N8nChatInteractResponseDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nConflictDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nContextDiscoverRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nProposedActionDto

/**
 * Direct client for context-discovery requests used by preference flows.
 *
 * This client shares response normalization with preference chat but represents a separate backend intent. Keep
 * discovery parsing here and store accepted contexts through the repository/ViewModel layer.
 */
object PreferenceContextDiscoverClient {

    private const val TAG = "CtxDiscoverClient"

    suspend fun discover(request: N8nContextDiscoverRequestDto): N8nChatInteractResponseDto? {
        val gson = Gson()
        val json = gson.toJson(request)
        Log.d(TAG, "Request bytes=${json.length}")

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

        Log.d(TAG, "Response bytes=${bodyStr.length}")

        return try {
            val root = gson.fromJson(bodyStr, Map::class.java)
            val assistantMessage = root["assistantMessage"]?.toString() ?: ""

            @Suppress("UNCHECKED_CAST")
            val actionsRaw = root["proposedActions"] as? List<Map<String, Any?>> ?: emptyList()
            val actions = actionsRaw.map { m ->
                N8nProposedActionDto(
                    actionId = m["actionId"]?.toString() ?: "",
                    type = m["type"]?.toString() ?: "ADD",
                    targetPreferenceId = m["targetPreferenceId"]?.toString(),
                    newStatement = m["newStatement"]?.toString(),
                    newPreferenceType = m["newPreferenceType"]?.toString(),
                    targetType = m["targetType"]?.toString() ?: "CONTEXT",
                )
            }
            N8nChatInteractResponseDto(
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
    private fun parseConflicts(root: Map<*, *>): List<N8nConflictDto> {
        val raw = root["conflicts"] as? List<Map<String, Any?>> ?: return emptyList()
        return raw.map { c ->
            val ids = (c["involvedPreferenceIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            N8nConflictDto(
                conflictId = c["conflictId"]?.toString() ?: "",
                description = c["description"]?.toString() ?: "",
                involvedPreferenceIds = ids,
            )
        }
    }
}
