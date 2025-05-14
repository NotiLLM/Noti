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
import org.muilab.notigpt.database.server.DifyPostNotificationAction
import org.muilab.notigpt.database.server.DifyPostNotificationPreference
import org.muilab.notigpt.database.server.DifyRequest
import org.muilab.notigpt.database.server.DifyUpdateNotification
import org.muilab.notigpt.util.Constants.Companion.DIFY_POST_NOTIFICATION_ACTION
import org.muilab.notigpt.util.Constants.Companion.DIFY_POST_NOTIFICATION_PREFERENCE
import org.muilab.notigpt.util.Constants.Companion.DIFY_UPDATE_NOTIFICATION
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_DELETED
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
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
                DIFY_POST_NOTIFICATION_PREFERENCE -> postNotificationPreference(inputData)
                DIFY_POST_NOTIFICATION_ACTION -> postNotificationAction(inputData)
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
        val notiUnit = drawerDao.getBySbnKey(notiKey)

        if (notiUnit == null) {
            return@withContext Result.failure()
        }

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
                    "sort-score-before-notice", "sort-score-after-notice", "sort-score-after-pinned", "sort-score-after-acted"
                )

                evaluationsWithScores.forEach { attr ->
                    evaluations.optJSONObject(attr)?.let {
                        explanationSB.append(
                            "${attr.replace("-", " ").capitalize()} (${it.getDouble("score")})\n${it.getString("reason")}\n"
                        )
                    }
                }

                val assignedCategory = evaluations.optJSONObject("assigned-category")
                assignedCategory?.let {
                    val newCategory = it.getString("category").toString()
                    explanationSB.append("[Assigned Category] ${newCategory}\n${it.getString("reason")}\n")
                    if (SharedPreferencesManager.autoArchive && newCategory == NOTI_CATEGORY_ARCHIVE) {
                        notiUnit.category = NOTI_CATEGORY_ARCHIVE
                    } else if (SharedPreferencesManager.autoDelete && newCategory == NOTI_CATEGORY_DELETED) {
                        notiUnit.category = NOTI_CATEGORY_DELETED
                    } else {
                        notiUnit.category =  NOTI_CATEGORY_GENERAL
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

    suspend fun postNotificationPreference(inputData: Data): Result = withContext(Dispatchers.IO) {

        val difyAPIService = DifyAPIClient.difyAPIService
        val apiKey = BuildConfig.API_KEY_POST_NOTIFICATION_PREFERENCE
        val authHeader = "Bearer $apiKey"
        val gson = Gson()

        val drawerDatabase = DrawerDatabase.getInstance(applicationContext)
        val drawerDao = drawerDatabase.drawerDao()

        val notiKey = inputData.getString("noti_key") ?: ""
        val preference = inputData.getInt("preference", 0)
        val notiUnit = drawerDao.getBySbnKey(notiKey)

        if (notiUnit == null) {
            return@withContext Result.failure()
        }

        val difyUpdateNotification = DifyPostNotificationPreference(
            SharedPreferencesManager.userId,
            gson.toJson(notiUnit.toDifyNoti()),
            preference
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
                Log.d("N8NWebhook", "Notification ${notiUnit.title} preference updated.")
            }
        } else {
            Log.e("N8NWebhook", "API call unsuccessful: ${response.code()}")
            return@withContext Result.retry()
        }

        delay(5000)
        Result.success()
    }

    suspend fun postNotificationAction(inputData: Data): Result = withContext(Dispatchers.IO) {

        val difyAPIService = DifyAPIClient.difyAPIService
        val apiKey = BuildConfig.API_KEY_POST_NOTIFICATION_ACTION
        val authHeader = "Bearer $apiKey"
        val gson = Gson()

        val drawerDatabase = DrawerDatabase.getInstance(applicationContext)
        val drawerDao = drawerDatabase.drawerDao()

        val notiKey = inputData.getString("noti_key") ?: ""
        val action = inputData.getString("action") ?: ""
        val notiUnit = drawerDao.getBySbnKey(notiKey)

        if (notiUnit == null) {
            return@withContext Result.failure()
        }

        val difyUpdateNotification = DifyPostNotificationAction(
            SharedPreferencesManager.userId,
            gson.toJson(notiUnit.toDifyNoti()),
            action
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
                Log.d("N8NWebhook", "Notification ${notiUnit.title} action updated.")
            }
        } else {
            Log.e("N8NWebhook", "API call unsuccessful: ${response.code()}")
            return@withContext Result.retry()
        }

        delay(5000)
        Result.success()
    }
}