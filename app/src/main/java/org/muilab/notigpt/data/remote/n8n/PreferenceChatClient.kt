package org.muilab.notigpt.data.remote.n8n

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.data.remote.n8n.dto.N8nChatInteractRequestDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nChatInteractResponseDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nConflictDto
import org.muilab.notigpt.data.remote.n8n.dto.N8nProposedActionDto

/**
 * Direct client for preference-chat interactions with the n8n backend.
 *
 * This client normalizes flexible webhook responses into app DTOs. Keep UI state and persistence outside it so
 * chat flows can evolve without coupling Retrofit parsing to ViewModels.
 */
object PreferenceChatClient {

    private const val TAG = "PrefChatClient"

    /**
     * Send a chat interaction and return the parsed response, or null on failure.
     */
    suspend fun interact(request: N8nChatInteractRequestDto): N8nChatInteractResponseDto? {
        val gson = Gson()
        val json = gson.toJson(request)
        Log.d(TAG, "Request: $json")

        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val webhookPath = BuildConfig.N8N_PREFERENCE_CHAT_INTERACT_PATH

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
                N8nProposedActionDto(
                    actionId = m["actionId"]?.toString() ?: "",
                    type = m["type"]?.toString() ?: "ADD",
                    targetPreferenceId = m["targetPreferenceId"]?.toString(),
                    newStatement = m["newStatement"]?.toString(),
                    newPreferenceType = m["newPreferenceType"]?.toString(),
                    targetType = m["targetType"]?.toString(),
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




