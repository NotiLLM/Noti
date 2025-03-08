package org.muilab.notigpt.database.server.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.muilab.notigpt.util.getNotifications

class UploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val uploadApiService = UploadApiService.create()

        val timeDiff = 4 * 60 * 60 * 1000L

        val allNotifications = getNotifications(applicationContext)
        val drawerJSONArray = JSONArray(
            allNotifications.map {
                it.toN8NNoti(timeDiff)
            }.filterNotNull()
        )
        if (drawerJSONArray.length() == 0)
            return Result.success()
        val requestBody = drawerJSONArray.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val uploadCall = uploadApiService.upload(requestBody)
        val uploadResponse = uploadCall.execute()

        return if (uploadResponse.isSuccessful) {
            Log.d("WorkManager", "Upload successful")
            Result.success() // ✅ Success
        } else {
            Log.e("WorkManager", uploadResponse.message())
            Result.failure()
        }

        Log.d("WorkManager", "No upload required")
        return Result.success()
    }
}