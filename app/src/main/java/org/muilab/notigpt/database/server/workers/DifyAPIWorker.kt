package org.muilab.notigpt.database.server.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.database.room.DrawerDatabase
import org.muilab.notigpt.database.server.DifyAPIClient
import org.muilab.notigpt.database.server.DifyRequest
import org.muilab.notigpt.database.server.DifyUpdateNotification
import org.muilab.notigpt.util.Constants.Companion.DIFY_UPDATE_NOTIFICATION
import org.muilab.notigpt.util.SharedPreferencesManager

class DifyAPIWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {

        Log.d("N8NWebhook", "Running Worker")

        return try {
            val response = when (inputData.getString("api_type")) {
                DIFY_UPDATE_NOTIFICATION -> updateNotification(inputData)
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

        val difyAPIService = DifyAPIClient.difyAPIService
        val apiKey = BuildConfig.API_KEY_UPDATE_NOTIFICATION
        val authHeader = "Bearer $apiKey"
        val gson = Gson()

        val drawerDatabase = DrawerDatabase.getInstance(applicationContext)
        val drawerDao = drawerDatabase.drawerDao()

        val notiKey = inputData.getString("noti_key") ?: ""
        val existingNotis = drawerDao.getBySbnKey(notiKey)

        if (existingNotis.isEmpty()) {
            return@withContext Result.failure()
        }

        val notiUnit = existingNotis[0]

        val difyUpdateNotification = DifyUpdateNotification(
            SharedPreferencesManager.userId,
            gson.toJson(notiUnit.toDifyNoti())
        )

        val difyRequest = DifyRequest(difyUpdateNotification)


        val json = gson.toJson(difyRequest)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toRequestBody(mediaType)
        val response = difyAPIService.runWorkflow(authHeader, requestBody)

        if (response.isSuccessful) {
            val jsonString = response.body()?.string()

            val status = try {
                JSONObject(jsonString)
                    .getJSONObject("data")
                    .getString("status")
            } catch (e: Exception) {
                ""
            }

            if (status == "succeeded") {
                val jsonOutputs = JSONObject(jsonString)
                    .getJSONObject("data")
                    .getJSONObject("outputs")

                if (jsonOutputs.has("retry"))
                    return@withContext Result.retry()

                val featureStrRaw = jsonOutputs
                    .getString("features")
                    .removePrefix("```json\n")
                    .removeSuffix("\n```")
                val features = JSONObject(featureStrRaw)
                val evaluationStrRaw = jsonOutputs
                    .getString("evaluations")
                    .removePrefix("```json\n")
                    .removeSuffix("\n```")
                val evaluations = JSONObject(evaluationStrRaw)
                Log.d("Dify", "Features: $features")
                Log.d("Dify", "Evaluations: $evaluations")
                val explanationSB = StringBuilder()

                val evaluationsWithScores = listOf(
                    "sort-score-before-notice", "sort-score-after-notice", "sort-score-after-acted"
                )

                evaluationsWithScores.forEach { attr ->
                    evaluations.optJSONObject(attr)?.let {
                        explanationSB.append(
                            "${attr.replace("-", " ").capitalize()} (${it.getDouble("score")})\n${it.getString("reason")}\n"
                        )
                    }
                }

                val textSummary = evaluations.optJSONObject("text-summary")
                textSummary?.let {
                    explanationSB.append("[Text Summary]\n${it.getString("reason")}\n")
                    notiUnit.summary = it.getString("summary")
                }

                val featuresWithScores = listOf(
                    "necessity", "time-relevance", "sender-attractiveness", "content-attractiveness", "urgency", "importance",
                )

                featuresWithScores.forEach { attr ->
                    features.optJSONObject(attr)?.let {
                        explanationSB.append(
                            "${attr.replace("-", " ").capitalize()} (${it.getDouble("score")})\n${it.getString("reason")}\n"
                        )
                    }
                }

                val tasks = features.optJSONArray("tasks")
                tasks?.let {
                    explanationSB.append("Tasks:\n")
                    for (i in 0 until it.length()) {
                        val task = it.getJSONObject(i)
                        explanationSB.append("${i + 1}: ${task.getString("task")}, due ${task.getString("deadline")}\n")
                    }
                    explanationSB.append("\n")
                }

                val keyTimings = features.optJSONArray("key-timings")
                keyTimings?.let {
                    explanationSB.append("Key Timings:\n")
                    for (i in 0 until it.length()) {
                        val timing = it.getJSONObject(i)
                        explanationSB.append("${timing.getString("key-timing")}: ${timing.getString("event")}\n")
                    }
                    explanationSB.append("\n")
                }

                notiUnit.sortScore = evaluations.optJSONObject("sort-score-before-notice")?.getDouble("score") ?: 0.0
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