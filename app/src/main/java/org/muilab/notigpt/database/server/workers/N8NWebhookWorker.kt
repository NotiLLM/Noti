package org.muilab.notigpt.database.server.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.muilab.notigpt.database.room.DrawerDatabase
import org.muilab.notigpt.database.server.N8NRetrofitClient
import org.muilab.notigpt.database.server.UpdateNotificationRequest
import org.muilab.notigpt.util.Constants.Companion.WEBHOOK_UPDATE_NOTIFICATION
import org.muilab.notigpt.util.SharedPreferencesManager

class N8NWebhookWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {

        Log.d("N8NWebhook", "Running Worker")

        return try {
            val response = when (inputData.getString("api_type")) {
                WEBHOOK_UPDATE_NOTIFICATION -> updateNotification(inputData)
                else -> Result.success()
            }
            response
        } catch (e: Exception) {
            Log.d("N8NWebhook", e.stackTraceToString())
            Result.retry()
        }
    }

    suspend fun updateNotification(inputData: Data): Result = withContext(Dispatchers.IO) {

        Log.d("N8NWebhook", "Update Notification")

        val webhookService = N8NRetrofitClient.n8nWebhookService
        val drawerDatabase = DrawerDatabase.getInstance(applicationContext)
        val drawerDao = drawerDatabase.drawerDao()

        val notiKey = inputData.getString("noti_key") ?: ""
        val existingNotis = drawerDao.getBySbnKey(notiKey)

        if (existingNotis.isEmpty()) {
            return@withContext Result.failure()
        }

        val notiUnit = existingNotis[0]

        val updateNotificationRequest = UpdateNotificationRequest(
            SharedPreferencesManager.userId,
            notiUnit.toN8NNoti()
        )

        val response = webhookService.updateNotification(updateNotificationRequest).execute()

        if (response.isSuccessful) {
            val jsonString = response.body()?.string()
            if (jsonString != null) {
                val jsonResponse = JSONObject(jsonString)
                val explanationSB = StringBuilder()

                val tasks = jsonResponse.optJSONArray("tasks")
                tasks?.let {
                    explanationSB.append("Tasks:\n")
                    for (i in 0 until it.length()) {
                        val task = it.getJSONObject(i)
                        explanationSB.append("${i + 1}: ${task.getString("task")}, due ${task.getString("deadline")}\n")
                    }
                    explanationSB.append("\n")
                }

                val keyTimings = jsonResponse.optJSONArray("key-timings")
                keyTimings?.let {
                    explanationSB.append("Key Timings:\n")
                    for (i in 0 until it.length()) {
                        val timing = it.getJSONObject(i)
                        explanationSB.append("${timing.getString("key-timing")}: ${timing.getString("event")}\n")
                    }
                    explanationSB.append("\n")
                }

                val attributes = listOf(
                    "necessity", "time-relevance", "importance", "urgency",
                    "sort-score-before-notice", "sort-score-after-acted",
                    "sort-score-if-pinned"
                )

                attributes.forEach { attr ->
                    jsonResponse.optJSONObject(attr)?.let {
                        explanationSB.append(
                            "${attr.replace("-", " ").capitalize()} (${it.getDouble("score")})\n${it.getString("reason")}\n"
                        )
                    }
                }

                val textSummary = jsonResponse.optJSONObject("text-summary")
                textSummary?.let {
                    explanationSB.append("[Text Summary]\n${it.getString("reason")}\n")
                    notiUnit.summary = it.getString("summary")
                }

                notiUnit.sortScore = jsonResponse.optJSONObject("sort-score-before-notice")?.getDouble("score") ?: 0.0
                notiUnit.explanation = explanationSB.toString()
                drawerDao.update(notiUnit)

                Log.d("N8NWebhook", "Notification ${notiUnit.title} updated.")
            }
        } else {
            Log.e("N8NWebhook", "API call unsuccessful: ${response.code()}")
            return@withContext Result.retry()
        }

        delay(5000)
        Result.success()
    }
}