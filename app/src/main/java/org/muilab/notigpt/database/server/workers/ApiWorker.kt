package org.muilab.notigpt.database.server.workers
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.database.room.DrawerDatabase
import org.muilab.notigpt.database.server.RetrofitClient
import org.muilab.notigpt.model.server.SortOutcome
import org.muilab.notigpt.util.Constants.Companion.API_CLEAR_DB
import org.muilab.notigpt.util.Constants.Companion.API_EXPORT_DB
import org.muilab.notigpt.util.Constants.Companion.API_INSERT_PREFERENCE
import org.muilab.notigpt.util.Constants.Companion.API_SORT_DRAWER
import org.muilab.notigpt.util.Constants.Companion.API_SYNC_DRAWER
import org.muilab.notigpt.util.Constants.Companion.API_SYNC_NOTI
import org.muilab.notigpt.util.Constants.Companion.API_SYNC_QUERY
import org.muilab.notigpt.util.Constants.Companion.API_UPDATE_USER
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.doubleArrayToCompressedBase64
import org.muilab.notigpt.util.getNotifications
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.Locale


class ApiWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Get the API endpoint or identifier to determine the type of API call
        val apiType = inputData.getString("api_type") ?: return Result.failure()

        return try {
            when (apiType) {
                API_SYNC_NOTI -> doSyncNoti()
                API_SYNC_QUERY -> doSyncQuery()
                API_SYNC_DRAWER -> doSyncDrawer()
                API_SORT_DRAWER -> doSortDrawer()
                API_UPDATE_USER -> doUpdateUser()
                API_INSERT_PREFERENCE -> doInsertPreference()
                API_EXPORT_DB -> doExportDB()
                API_CLEAR_DB -> doClearDB()
                else -> {
                    Log.e("ApiWorker", "Unknown API type: $apiType")
                    Result.failure()
                }
            }
        } catch (e: HttpException) {
            Log.e("ApiWorker", "Error: ${e.message}")
            Result.retry()
        } catch (e: Exception) {
            Log.e("ApiWorker", "Error: ${e.message}")
            Result.failure()
        }
    }

    private suspend fun doSyncNoti(): Result {
        val notiKey = inputData.getString("noti_key") ?: return Result.failure()
        return withContext(Dispatchers.IO) {
            val userId = SharedPreferencesManager.userId
            val drawerDatabase = DrawerDatabase.getInstance(applicationContext)
            val drawerDao = drawerDatabase.drawerDao()
            val notification = drawerDao.getBySbnKey(notiKey)

            val requestObject = JSONObject(notification[0].toServerNoti(userId))
            val requestBody = requestObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val response = RetrofitClient.apiService.syncNotification(requestBody).execute()
            handleResponse(response)
        }
    }

    private fun doSyncQuery(): Result {
        val queryString = inputData.getString("query_string") ?: return Result.failure()

        val requestObject = JSONObject()
        requestObject.put("query_string", queryString)

        val requestBody = requestObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val response = RetrofitClient.apiService.syncQueryEmbedding(requestBody).execute()
        return handleResponse(response)
    }

    private suspend fun doSyncDrawer(): Result {
        return withContext(Dispatchers.IO) {
            val userId = SharedPreferencesManager.userId

            val allNotifications = getNotifications(applicationContext)
            val drawerJSONArray = JSONArray(
                allNotifications.map {
                    it.toServerNoti(userId)
                }
            )

            val requestObject = JSONObject()
            requestObject.put("user_id", userId)
            requestObject.put("notifications", drawerJSONArray)

            val requestBody = requestObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val response = RetrofitClient.apiService.updateNotifications(requestBody).execute()
            handleResponse(response)
        }
    }

    private fun doSortDrawer(): Result {
        val userId = SharedPreferencesManager.userId
        val requestObject = JSONObject()
        requestObject.put("user_id", userId)
        val requestBody = requestObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val response = RetrofitClient.apiService.sortNotifications(requestBody).execute()
        return handleResponse(response)
    }

    private fun doUpdateUser(): Result {
        val userId = SharedPreferencesManager.userId
        val requestObject = JSONObject()
        requestObject.put("user_id", userId)
        requestObject.put("locale", Locale.getDefault().toString())
        val requestBody = requestObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val response = RetrofitClient.apiService.updateUser(requestBody).execute()
        return handleResponse(response)
    }

    private fun doExportDB(): Result {
        val response = RetrofitClient.apiService.exportDB().execute()
        return handleResponse(response)
    }

    private fun doClearDB(): Result {
        val response = RetrofitClient.apiService.clearDB().execute()
        return handleResponse(response)
    }

    private suspend fun doInsertPreference(): Result {
        val notiKey = inputData.getString("noti_key") ?: return Result.failure()
        val preferred = inputData.getBoolean("preferred", false)

        return withContext(Dispatchers.IO) {
            val userId = SharedPreferencesManager.userId
            val drawerDatabase = DrawerDatabase.getInstance(applicationContext)
            val drawerDao = drawerDatabase.drawerDao()
            val notification = drawerDao.getBySbnKey(notiKey)

            val requestObject = JSONObject()
            requestObject.put("user_id", userId)
            requestObject.put("notification", JSONObject(notification[0].toServerNoti(userId)))
            requestObject.put("preferred", preferred)

            val requestBody = requestObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val response = RetrofitClient.apiService.insertPreference(requestBody).execute()
            handleResponse(response)
        }
    }

    private fun handleResponse(response: Response<ResponseBody>): Result {
        val apiType = inputData.getString("api_type") ?: return Result.failure()

        return if (response.isSuccessful) {
            Log.d("ApiWorker", "Data sent successfully")
            val outputData = when (apiType) {
                API_SORT_DRAWER -> handleSortDrawer(response)
                API_SYNC_NOTI -> handleSyncNoti(response)
                API_SYNC_QUERY -> handleSyncQuery(response)
                else -> Data.Builder().build()
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "$apiType Success", Toast.LENGTH_SHORT).show()
            }
            Result.success(outputData)
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "$apiType failed to send data: ${response.errorBody()?.string()}", Toast.LENGTH_LONG).show()
            }
            Log.e("ApiWorker", "Failed to send data: ${response.errorBody()?.string()}")
            Result.retry()
        }
    }

    private fun handleSortDrawer(response: Response<ResponseBody>): Data {
        try {
            val responseBody = response.body()
            val jsonString = responseBody?.string()
            val type = object : TypeToken<ArrayList<SortOutcome>>() {}.type
            val sortOutcomes: ArrayList<SortOutcome> = Gson().fromJson(jsonString, type)

            CoroutineScope(Dispatchers.IO).launch {
                val drawerDatabase = DrawerDatabase.getInstance(applicationContext)
                val drawerDao = drawerDatabase.drawerDao()
                drawerDao.updateSorting(sortOutcomes)
            }

        } catch (e: IOException) {
            Log.d("Sort", e.stackTraceToString())
        }
        return Data.Builder().build()
    }

    private fun handleSyncNoti(response: Response<ResponseBody>): Data {
        try {
            val responseBody = response.body()
            val jsonString = responseBody?.string()
            val jsonObject = JSONObject(jsonString)
            val notiKey = jsonObject.getString("noti_key")
            val jsonArray = jsonObject.getJSONArray("notification_embedding")
            val doubleArray = DoubleArray(jsonArray.length()) { index -> jsonArray.getDouble(index) }
            val embeddingString = doubleArrayToCompressedBase64(doubleArray)
            CoroutineScope(Dispatchers.IO).launch {
                val drawerDatabase = DrawerDatabase.getInstance(applicationContext)
                val drawerDao = drawerDatabase.drawerDao()
                drawerDao.updateEmbedding(notiKey, embeddingString)
            }
        } catch (e: IOException) {
            Log.d("Sync Noti", e.stackTraceToString())
        }
        return Data.Builder().build()
    }

    private fun handleSyncQuery(response: Response<ResponseBody>): Data {
        try {
            val responseBody = response.body()
            val jsonString = responseBody?.string()
            val jsonObject = JSONObject(jsonString)
            val jsonArray = jsonObject.getJSONArray("query_embedding")
            val doubleArray = DoubleArray(jsonArray.length()) { index -> jsonArray.getDouble(index) }
            val embeddingString = doubleArrayToCompressedBase64(doubleArray)

            Log.d("Query", embeddingString)
            Log.d("Query", embeddingString.length.toString())
            return Data.Builder()
                .putString("embeddingString", embeddingString)
                .build()
        } catch (e: IOException) {
            Log.d("Sync Noti", e.stackTraceToString())
        }
        return Data.Builder().build()
    }
}
