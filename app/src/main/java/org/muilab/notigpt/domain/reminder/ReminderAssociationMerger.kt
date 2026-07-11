package org.muilab.notigpt.domain.reminder

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure helper for deriving evidence links from n8n responses.
 *
 * Contract v2: the workflow cites `evidenceRecordIds` — the exact record ids that directly
 * evidence a saved item. Only ids we actually sent in the request are accepted (LLMs can cite
 * bogus ids); an op whose citation list has no valid ids should be dropped entirely by the caller.
 * The old union-with-request semantics (every record in the batch linked to every returned item)
 * are gone by design.
 *
 * Keep this free of Room and persistence details so extraction/regeneration handlers share the
 * same evidence semantics.
 */
object ReminderAssociationMerger {

    /** Reads cited evidence ids from a response op, tolerating pre-v2 field names. */
    fun evidenceIdsFrom(response: JSONObject): Set<String> {
        val arr: JSONArray = response.optJSONArray("evidenceRecordIds")
            ?: response.optJSONArray("sourceNotiRecordIds")
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

    /**
     * Filters cited ids down to those actually present in the request (hallucination guard).
     *
     * Returns the valid evidence set; empty means the op cited nothing real and must be discarded.
     */
    fun filterValidEvidence(citedIds: Collection<String>, validRequestIds: Set<String>): Set<String> =
        citedIds.mapNotNull { it.trim().takeIf { id -> id.isNotBlank() && id in validRequestIds } }.toSet()
}
