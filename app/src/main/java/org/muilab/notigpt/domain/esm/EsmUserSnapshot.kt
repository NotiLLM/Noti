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

    fun parse(payloadJson: String): SurveyContext? {
        return try {
            val root = JSONObject(payloadJson)
            val reminderObj = root.getJSONObject("reminder")
            val reminder = ReminderPreview(
                title = reminderObj.optString("title", "(Untitled)"),
                content = reminderObj.optString("content", ""),
                isTask = reminderObj.optBoolean("isTask", true),
                deadlineTimestamp = reminderObj.optLong("deadlineTimestamp", 0L),
                estimatedCompletionMinutes = reminderObj.optLong("estimatedCompletionMinutes", 0L),
            )

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
}
