package org.muilab.notigpt.repository.noti

import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiRecord

/**
 * Formatter for one notification unit and optional context records in export JSON.
 *
 * Keep export field names and privacy-sensitive inclusion choices here. Repository code should decide which
 * records to include, then delegate object shape to this formatter.
 */
object NotiExportFormatter {

    fun formatUnit(
        notiKey: String,
        appName: String,
        isPeople: Boolean,
        records: List<NotiRecord>,
        actions: List<NotiAction>,
        includeContext: Boolean,
    ): JSONObject {
        val notificationJson = JSONObject()
        notificationJson.put("id", notiKey)
        notificationJson.put("app", appName)
        notificationJson.put("isPeople", isPeople)

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
                    recordJson.put("title", item.getDisplayedTitle(isPeople))
                    recordJson.put("content", item.content.takeIf { it != "null" } ?: "")
                    recordJson.put("time", item.time)
                    recordJson.put("is_dismissed", item.isDismissed)
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
