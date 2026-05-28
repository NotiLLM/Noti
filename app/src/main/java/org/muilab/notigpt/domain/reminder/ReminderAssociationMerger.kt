package org.muilab.notigpt.domain.reminder

import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.model.features.ReminderUnit

/**
 * Pure helper for notification provenance attached to generated reminders.
 *
 * n8n output can be partial or empty. Treat backend associations as additive,
 * not as an authoritative replacement for provenance already stored locally.
 */
object ReminderAssociationMerger {
    fun associationIdsFrom(response: JSONObject): Set<String> {
        val arr: JSONArray = response.optJSONArray("associatedNotiRecords")
            ?: response.optJSONArray("associatedNotis")
            ?: return emptySet()

        return buildSet {
            for (i in 0 until arr.length()) {
                val id = arr.optString(i).trim()
                if (id.isNotBlank()) add(id)
            }
        }
    }

    fun merge(
        existing: ReminderUnit?,
        responseAssociationIds: Set<String>,
        requestRecordIds: Set<String> = emptySet(),
    ): Set<String> = buildSet {
        existing?.associatedNotiRecords?.let(::addAll)
        addAll(requestRecordIds.filter { it.isNotBlank() })
        addAll(responseAssociationIds.filter { it.isNotBlank() })
    }
}
