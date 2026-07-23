package org.muilab.notigpt.data.remote.n8n

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.muilab.notigpt.data.remote.firestore.FirestorePaths

/**
 * Best-effort writer for raw per-call LLM token usage (see plans/3-invitation-and-llm-usage.md).
 *
 * Stores only raw token counts, never a computed cost — Gemini pricing changes independently of
 * app/n8n releases, and cost is computed later by firestore/usage_report.py. A failed write never
 * blocks or fails the pipeline call it's reporting on, mirroring the silent-catch, best-effort
 * style of SavedItemRepository.journalUserEvent.
 */
object UsageLogger {

    private const val TAG = "UsageLogger"
    private val scope = CoroutineScope(Dispatchers.IO)

    internal data class UsageFields(
        val model: String,
        val inputTokens: Long,
        val outputTokens: Long,
        val thinkingTokens: Long,
        val totalTokens: Long,
    )

    /** Parses the `model=..;input=..;output=..;thinking=..;total=..` header format. */
    internal fun parseHeader(headerValue: String): UsageFields {
        val fields = headerValue.split(";")
            .mapNotNull { entry ->
                val separatorIndex = entry.indexOf('=')
                if (separatorIndex < 0) null else entry.substring(0, separatorIndex) to entry.substring(separatorIndex + 1)
            }
            .toMap()
        return UsageFields(
            model = fields["model"]?.takeIf(String::isNotBlank) ?: "unknown",
            inputTokens = fields["input"]?.toLongOrNull() ?: 0L,
            outputTokens = fields["output"]?.toLongOrNull() ?: 0L,
            thinkingTokens = fields["thinking"]?.toLongOrNull() ?: 0L,
            totalTokens = fields["total"]?.toLongOrNull() ?: 0L,
        )
    }

    fun logAsync(stage: String, headerValue: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        val fields = parseHeader(headerValue)

        scope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection(FirestorePaths.COLLECTION_USAGE_LOGS)
                    .document()
                    .set(
                        mapOf(
                            "uid" to uid,
                            "stage" to stage,
                            "model" to fields.model,
                            "inputTokens" to fields.inputTokens,
                            "outputTokens" to fields.outputTokens,
                            "thinkingTokens" to fields.thinkingTokens,
                            "totalTokens" to fields.totalTokens,
                            "calledAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                    .await()
            } catch (t: Throwable) {
                Log.w(TAG, "logAsync failed stage=$stage", t)
            }
        }
    }
}
