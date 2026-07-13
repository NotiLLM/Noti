package org.muilab.notigpt.data.repository.notification

import org.json.JSONArray
import org.muilab.notigpt.model.features.NotiCategory
import org.muilab.notigpt.model.features.NotiLlmState
import org.muilab.notigpt.model.notifications.NotiUnit

/**
 * Routes a notification thread to the Communication or Content bucket backing the home screen's two
 * notification pages.
 *
 * The LLM scan is the source of truth: [NotiLlmState.categories] persists per notiKey across
 * dismissals, so a thread keeps its category even after its records clear, and a fresh notification
 * on the same key inherits it until the next scan reclassifies. Before a thread has been scanned (or
 * if the model emitted only custom categories), fall back to the framework messaging-style signal
 * ([NotiUnit.isPeople], derived from MessagingStyle / call / people extras). Anything still
 * unresolved is Content, so nothing is unroutable.
 */
object NotiClassificationRepository {

    /** Returns [NotiCategory.Communication] or [NotiCategory.Content] for one thread. */
    fun categoryOf(unit: NotiUnit, llmState: NotiLlmState?): String {
        val categories = parseCategories(llmState?.categories)
        return when {
            NotiCategory.Communication in categories -> NotiCategory.Communication
            NotiCategory.Content in categories -> NotiCategory.Content
            unit.isPeople -> NotiCategory.Communication
            else -> NotiCategory.Content
        }
    }

    private fun parseCategories(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val arr = JSONArray(json)
            buildSet { for (i in 0 until arr.length()) add(arr.optString(i)) }
        } catch (_: Exception) {
            emptySet()
        }
    }
}
