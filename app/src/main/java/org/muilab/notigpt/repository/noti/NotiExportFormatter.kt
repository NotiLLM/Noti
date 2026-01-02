package org.muilab.notigpt.repository.noti

import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit

/**
 * Pure-ish formatter for the export log JSON.
 *
 * Keeping this out of [org.muilab.notigpt.repository.NotiRepository] reduces noise
 * and makes it easier to extend export formats later.
 */
object NotiExportFormatter {

    fun formatUnit(
        notiUnit: NotiUnit,
        records: List<NotiRecord>,
        actions: List<NotiAction>,
        includeContext: Boolean,
    ): JSONObject {
        val notificationJson = JSONObject()
        notificationJson.put("id", notiUnit.notiKey)
        notificationJson.put("app", notiUnit.appName)
        notificationJson.put("isPeople", notiUnit.isPeople)
        notificationJson.put("category", notiUnit.category)
        notificationJson.put("appCategory", notiUnit.appCategory)

        val lastRecord = records.lastOrNull()
        val lastTitle = lastRecord?.title ?: ""
        val notiOverallTitle = when {
            lastRecord != null && lastRecord.extraConversationTitle != "null" -> lastRecord.extraConversationTitle
            lastTitle != "null" -> lastTitle
            lastRecord != null && lastRecord.extraSubText != "null" -> lastRecord.extraSubText
            else -> ""
        }
        val notiSecondOverallTitle = when {
            lastRecord != null && lastRecord.extraConversationTitle != "null" && lastTitle != "null" -> lastTitle
            lastRecord != null && lastRecord.extraConversationTitle == "null" && lastTitle != "null" && lastRecord.extraSubText != "null" -> lastRecord.extraSubText
            lastRecord != null && lastRecord.extraConversationTitle == "null" && lastTitle != "null" -> ""
            else -> ""
        }

        notificationJson.put("overall_title", notiOverallTitle)
        notificationJson.put("second_title", notiSecondOverallTitle)

        val mergedData = (records.map { it.time to it } + actions.map { it.time to it })
            .sortedBy { it.first }

        val timelineDataArray = JSONArray()
        var logActions = includeContext

        mergedData.forEach { (_, item) ->
            when (item) {
                is NotiRecord -> {
                    val recordJson = JSONObject()
                    recordJson.put("type", "noti")
                    recordJson.put("title", item.getDisplayedTitle(notiUnit.isPeople))
                    recordJson.put("content", item.content.takeIf { it != "null" } ?: "")
                    recordJson.put("time", item.time)
                    recordJson.put("is_visible", item.isVisible)
                    timelineDataArray.put(recordJson)
                    logActions = true
                }
                is NotiAction -> {
                    if (logActions) {
                        val actionJson = JSONObject()
                        actionJson.put("type", "action")
                        actionJson.put("time", item.time)
                        actionJson.put("last_resume_time", item.lastAppResumeTime)
                        actionJson.put("action", item.actionType)
                        actionJson.put("metadata", item.metadata)
                        timelineDataArray.put(actionJson)
                    }
                }
            }
        }

        notificationJson.put("timeline_data", timelineDataArray)
        return notificationJson
    }
}

