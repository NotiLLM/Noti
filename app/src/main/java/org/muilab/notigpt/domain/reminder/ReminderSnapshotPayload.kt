package org.muilab.notigpt.domain.reminder

import org.json.JSONObject

object ReminderSnapshotPayload {
    data class RecordIdGrouping(
        val recordIds: List<String>,
        val notiKeyToRecordIds: Map<String, List<String>>,
    )

    /**
     * Parses v2 extraction snapshot payloads that contain recordIds/notiKeyToRecordIds.
     * Returns null if the payload isn't in that format.
     */
    fun parseRecordIdGrouping(payloadJson: String): RecordIdGrouping? {
        return try {
            val root = JSONObject(payloadJson)
            val v = root.optInt("v", 1)
            if (v != 2) return null

            val recordIds = root.optJSONArray("recordIds")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val id = arr.optString(i)
                        if (id.isNotBlank()) add(id)
                    }
                }
            } ?: emptyList()

            val mapObj = root.optJSONObject("notiKeyToRecordIds")
            val mapping: Map<String, List<String>> = if (mapObj != null) {
                val keys = mapObj.keys()
                buildMap {
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val idsArr = mapObj.optJSONArray(k)
                        val ids = if (idsArr != null) {
                            buildList {
                                for (i in 0 until idsArr.length()) {
                                    val id = idsArr.optString(i)
                                    if (id.isNotBlank()) add(id)
                                }
                            }
                        } else emptyList()
                        put(k, ids)
                    }
                }
            } else emptyMap()

            RecordIdGrouping(recordIds = recordIds, notiKeyToRecordIds = mapping)
        } catch (_: Exception) {
            null
        }
    }
}
