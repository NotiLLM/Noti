package org.muilab.notigpt.database.server

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.annotations.SerializedName
import org.muilab.notigpt.database.server.workers.DifyAPIWorker
import org.muilab.notigpt.util.Constants.Companion.DIFY_POST_NOTIFICATION_ACTION
import org.muilab.notigpt.util.Constants.Companion.DIFY_UPDATE_NOTIFICATION
import org.muilab.notigpt.util.SharedPreferencesManager
import java.util.concurrent.TimeUnit

data class DifyUpdateNotification(
    val userId: String,
    val notificationJsonStr: String
)

data class DifyPostNotificationAction(
    val user_id: String,
    val action_type: String,
    val action_time: Long,
    val notification_json_str: String,
)

data class DifyRequest<T>(
    @SerializedName("inputs")
    val inputs: T, // Can be any data class

    @SerializedName("response_mode")
    val responseMode: String = "blocking",

    @SerializedName("user")
    val user: String = SharedPreferencesManager.userId
)

fun enqueueUpdateNotification(context: Context, notiKey: String) {
    val inputData = Data.Builder()
        .putString("api_type", DIFY_UPDATE_NOTIFICATION)
        .putString("noti_key", notiKey)
        .build()
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val difyAPIWorkerRequest = OneTimeWorkRequestBuilder<DifyAPIWorker>()
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setInputData(inputData)
        .build()
    WorkManager.getInstance(context).enqueue(
        difyAPIWorkerRequest
    )
}

fun enqueueNotificationAction(context: Context, notiKey: String, actionType: String, actionTime: Long = System.currentTimeMillis()) {
    val inputData = Data.Builder()
        .putString("api_type", DIFY_POST_NOTIFICATION_ACTION)
        .putString("noti_key", notiKey)
        .putString("action_type", actionType)
        .putLong("action_time", actionTime)
        .build()
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val difyAPIWorkerRequest = OneTimeWorkRequestBuilder<DifyAPIWorker>()
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setInputData(inputData)
        .build()
    WorkManager.getInstance(context).enqueue(
        difyAPIWorkerRequest
    )
}