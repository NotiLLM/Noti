package org.muilab.notigpt.domain.esm

import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.components.NotiDisplayState
import org.muilab.notigpt.model.notifications.components.NotiMetadata
import org.muilab.notigpt.model.notifications.components.NotiReminderAttr

/**
 * Lightweight, user-facing snapshot structures decoded from EsmExtractionSnapshot.payloadJson.
 *
 * These are intentionally minimal and safe for display: they avoid internal IDs in UI.
 */
object EsmUserSnapshot {

    data class ReminderPreview(
        val title: String,
        val content: String,
        val isTask: Boolean,
        val deadlineTimestamp: Long,
        val estimatedCompletionMinutes: Long,
    )

    data class NotiPreview(
        val displayUnit: NotiDisplayUnit,
    )

    data class SurveyContext(
        val reminder: ReminderPreview,
        val notis: List<NotiPreview>,
    )

    data class RecordIdGrouping(
        val recordIds: List<String>,
        val notiKeyToRecordIds: Map<String, List<String>>,
    )

    fun parse(payloadJson: String): SurveyContext? {
        return try {
            val root = JSONObject(payloadJson)

            val reminderObj = root.optJSONObject("reminder")
            val reminder = if (reminderObj != null) {
                ReminderPreview(
                    title = reminderObj.optString("title", "(Untitled)"),
                    content = reminderObj.optString("content", ""),
                    isTask = reminderObj.optBoolean("isTask", true),
                    deadlineTimestamp = reminderObj.optLong("deadlineTimestamp", 0L),
                    estimatedCompletionMinutes = reminderObj.optLong("estimatedCompletionMinutes", 0L),
                )
            } else {
                // Some snapshots (e.g. extraction v2/v2.1) intentionally store only recordIds mappings.
                // In that case there's no reminder title/content in the payload.
                // Try best-effort legacy keys; otherwise show a safe placeholder.
                val legacyTitle = root.optString("reminderTitle", "")
                val legacyContent = root.optString("reminderContent", "")
                ReminderPreview(
                    title = legacyTitle.ifBlank { "(Untitled)" },
                    content = legacyContent,
                    isTask = root.optBoolean("isTask", true),
                    deadlineTimestamp = root.optLong("deadlineTimestamp", 0L),
                    estimatedCompletionMinutes = root.optLong("estimatedCompletionMinutes", 0L),
                )
            }

            val notis = mutableListOf<NotiPreview>()
            val arr: JSONArray = root.optJSONArray("notis") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val u = item.optJSONObject("notiUnit")
                val rArr = item.optJSONArray("notiRecords") ?: JSONArray()

                // If notiUnit is missing, skip rendering.
                if (u == null) continue

                val notiKey = u.optString("notiKey", "")
                val pkgName = u.optString("pkgName", "")
                val appName = u.optString("appName", "")

                val iconStr = u.optString("icon", "")
                val largeIconStr = u.optString("largeIcon", "")

                val metadata = NotiMetadata(
                    pkgName = pkgName,
                    hashKey = notiKey.hashCode(),
                    groupKey = "",
                    isAppGroup = false,
                    isGroupChat = false,
                    sortKey = "",
                    appName = if (appName.isBlank()) "Unknown App" else appName,
                    lastUpdateTime = u.optLong("lastUpdateTime", 0L),
                    lastSyncTime = u.optLong("lastSyncTime", 0L),
                    icon = iconStr.ifBlank { "null" },
                    largeIcon = largeIconStr.ifBlank { "null" },
                    isPeople = u.optBoolean("isPeople", false),
                )

                val displayState = NotiDisplayState(
                    isPinned = u.optBoolean("isPinned", false),
                    isArchived = false,
                    isDismissed = u.optBoolean("isDismissed", false),
                    isRead = u.optBoolean("isRead", true),
                    isSetToTop = false,
                    setToTopTime = 0L,
                    sortPosition = u.optInt("sortPosition", -1),
                    explanation = "",
                    summary = u.optString("summary", ""),
                    sortScore = u.optDouble("sortScore", 0.0),
                )

                val notiUnit = NotiUnit(
                    notiKey = notiKey,
                    metadata = metadata,
                    displayState = displayState,
                    reminderAttr = NotiReminderAttr(),
                    groupId = u.optString("groupId").takeIf { it.isNotBlank() && it != "null" },
                )

                val records = mutableListOf<NotiRecord>()
                for (j in 0 until rArr.length()) {
                    val ro = rArr.optJSONObject(j) ?: continue
                    records.add(
                        NotiRecord(
                            notiRecordId = ro.optString("notiRecordId", ""),
                            notiKey = ro.optString("notiKey", notiUnit.notiKey),
                            whenTime = ro.optLong("whenTime", 0L),
                            postTime = ro.optLong("postTime", 0L),
                            person = ro.optString("person", ""),
                            extraTitle = ro.optString("extraTitle", ""),
                            extraBigTitle = ro.optString("extraBigTitle", ""),
                            extraConversationTitle = ro.optString("extraConversationTitle", ""),
                            extraBigText = ro.optString("extraBigText", ""),
                            extraText = ro.optString("extraText", ""),
                            extraTextLines = ro.optString("extraTextLines", ""),
                            extraSummaryText = ro.optString("extraSummaryText", ""),
                            extraInfoText = ro.optString("extraInfoText", ""),
                            extraSubText = ro.optString("extraSubText", ""),
                            isDismissed = ro.optBoolean("isDismissed", false),
                            taskScanned = false,
                            taskExtracted = false,
                            taskExtractionClaimed = false,
                            taskExtractionClaimedAt = 0L,
                        )
                    )
                }

                val displayUnit = NotiDisplayUnit(notiUnit, records)
                notis.add(NotiPreview(displayUnit))
            }

            SurveyContext(reminder = reminder, notis = notis)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse snapshot JSON and override reminder information from the current reminder row.
     *
     * Use this when payloadJson comes from an extraction snapshot that may not embed reminder text.
     */
    fun parse(payloadJson: String, reminder: org.muilab.notigpt.model.features.ReminderUnit?): SurveyContext? {
        val base = parse(payloadJson) ?: return null
        if (reminder == null) return base

        val mergedReminder = ReminderPreview(
            title = reminder.reminderTitle.ifBlank { "(Untitled)" },
            content = reminder.reminderContent,
            isTask = reminder.isTask,
            deadlineTimestamp = reminder.deadlineTimestamp,
            estimatedCompletionMinutes = reminder.estimatedCompletionTime,
        )

        return base.copy(reminder = mergedReminder)
    }

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
