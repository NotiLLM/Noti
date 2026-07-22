package org.muilab.notigpt.data.repository.suggestion

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.util.SharedPreferencesManager

data class SuggestedItem(
    val savedItemId: String,
    val reason: String,
)

data class SuggestionSnapshot(
    val generatedAtMs: Long,
    val items: List<SuggestedItem>,
)

enum class SuggestionRefreshError { Network, InvalidResponse, Unknown }

data class SuggestionUiState(
    val snapshot: SuggestionSnapshot? = null,
    val isRefreshing: Boolean = false,
    val error: SuggestionRefreshError? = null,
)

/** Local-only, account-scoped snapshot. This data is intentionally absent from Room and Firestore. */
class SuggestionSnapshotStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(SuggestionUiState(snapshot = readSnapshot()))
    val state: StateFlow<SuggestionUiState> = _state.asStateFlow()

    @Synchronized
    fun syncAccount() {
        val storedOwner = prefs.getString(KEY_OWNER, "").orEmpty()
        val owner = currentOwner()
        if (storedOwner != owner || (owner.isBlank() && _state.value.snapshot != null)) {
            _state.value = SuggestionUiState(snapshot = readSnapshot())
        }
    }

    @Synchronized
    fun beginRefresh() {
        syncAccount()
        _state.value = _state.value.copy(isRefreshing = true, error = null)
    }

    @Synchronized
    fun replace(items: List<SuggestedItem>, generatedAtMs: Long = System.currentTimeMillis()) {
        val owner = currentOwner()
        val normalized = items
            .asSequence()
            .filter { it.savedItemId.isNotBlank() && it.reason.isNotBlank() }
            .distinctBy { it.savedItemId }
            .take(SuggestionConstants.H_MAX_SUGGESTIONS)
            .map { it.copy(reason = it.reason.trim().take(MAX_REASON_CHARS)) }
            .toList()
        val snapshot = SuggestionSnapshot(generatedAtMs, normalized)
        val json = JSONObject().apply {
            put("generatedAtMs", generatedAtMs)
            put("items", JSONArray().apply {
                normalized.forEach { item ->
                    put(JSONObject().put("savedItemId", item.savedItemId).put("reason", item.reason))
                }
            })
        }
        prefs.edit()
            .putString(KEY_OWNER, owner)
            .putString(KEY_SNAPSHOT, json.toString())
            .apply()
        _state.value = SuggestionUiState(snapshot = snapshot)
    }

    @Synchronized
    fun fail(error: SuggestionRefreshError) {
        _state.value = _state.value.copy(isRefreshing = false, error = error)
    }

    /** Removes only from the current snapshot; the next H replacement may suggest it again. */
    @Synchronized
    fun dismiss(savedItemId: String) {
        val current = _state.value.snapshot ?: return
        replace(current.items.filterNot { it.savedItemId == savedItemId }, current.generatedAtMs)
    }

    fun isRefreshDue(now: Long = System.currentTimeMillis()): Boolean {
        syncAccount()
        val generatedAt = _state.value.snapshot?.generatedAtMs ?: return true
        return now - generatedAt >= SuggestionConstants.REFRESH_INTERVAL_MS
    }

    private fun readSnapshot(): SuggestionSnapshot? {
        val owner = currentOwner()
        if (owner.isBlank() || prefs.getString(KEY_OWNER, "").orEmpty() != owner) return null
        return runCatching {
            val root = JSONObject(prefs.getString(KEY_SNAPSHOT, null) ?: return null)
            val generatedAtMs = root.getLong("generatedAtMs")
            val arr = root.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (index in 0 until arr.length()) {
                    val item = arr.optJSONObject(index) ?: continue
                    val id = item.optString("savedItemId")
                    val reason = item.optString("reason")
                    if (id.isNotBlank() && reason.isNotBlank()) add(SuggestedItem(id, reason))
                }
            }
            SuggestionSnapshot(generatedAtMs, items.take(SuggestionConstants.H_MAX_SUGGESTIONS))
        }.getOrNull()
    }

    private fun currentOwner(): String =
        FirebaseAuth.getInstance().currentUser?.uid.orEmpty().ifBlank { SharedPreferencesManager.userId }

    companion object {
        private const val PREFS_NAME = "suggestion_snapshot"
        private const val KEY_OWNER = "owner_user_id"
        private const val KEY_SNAPSHOT = "snapshot_json"
        private const val MAX_REASON_CHARS = 280

        @Volatile private var instance: SuggestionSnapshotStore? = null

        fun getInstance(context: Context): SuggestionSnapshotStore =
            instance ?: synchronized(this) {
                instance ?: SuggestionSnapshotStore(context.applicationContext).also { instance = it }
            }
    }
}
