package org.muilab.notigpt.domain.reminder

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure merge helper for reminder-to-notification-record associations.
 *
 * This owns the rule for combining backend association output with existing reminder context. Keep it free of
 * Room and JSON persistence details so extraction/regeneration handlers can share the same merge semantics.
 */
object ReminderAssociationMerger {
    fun associationIdsFrom(response: JSONObject): Set<String> {
        val arr: JSONArray = response.optJSONArray("sourceNotiRecordIds")
            ?: response.optJSONArray("associatedNotiRecords")
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
        existingRecordIds: Set<String>,
        responseAssociationIds: Set<String>,
        requestRecordIds: Set<String> = emptySet(),
    ): Set<String> = buildSet {
        addAll(existingRecordIds.filter { it.isNotBlank() })
        addAll(requestRecordIds.filter { it.isNotBlank() })
        addAll(responseAssociationIds.filter { it.isNotBlank() })
    }
}
