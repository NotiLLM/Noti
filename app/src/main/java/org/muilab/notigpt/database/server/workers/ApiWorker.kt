
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.JsonParser
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
import org.muilab.notigpt.util.Constants.Companion.API_UPDATE_USER
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.getNotifications
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale


class ApiWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Get the API endpoint or identifier to determine the type of API call
        val apiType = inputData.getString("api_type") ?: return Result.failure()

        return try {
            when (apiType) {
                API_SYNC_DRAWER -> {
                    withContext(Dispatchers.IO) {
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
                        handleResponse(response, apiType)
                    }
                }
                API_SORT_DRAWER -> {
                    val userId = SharedPreferencesManager.userId
                    val requestObject = JSONObject()
                    requestObject.put("user_id", userId)
                    val requestBody = requestObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val response = RetrofitClient.apiService.sortNotifications(requestBody).execute()
                    handleResponse(response, API_SORT_DRAWER)
                }
                API_UPDATE_USER -> {
                    val userId = SharedPreferencesManager.userId
                    val requestObject = JSONObject()
                    requestObject.put("user_id", userId)
                    requestObject.put("locale", Locale.getDefault().toString())
                    val requestBody = requestObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val response = RetrofitClient.apiService.updateUser(requestBody).execute()
                    handleResponse(response, apiType)
                }
                API_INSERT_PREFERENCE -> {

                    val notiKey = inputData.getString("noti_key") ?: return Result.failure()
                    val preferred = inputData.getBoolean("preferred", false)

                    withContext(Dispatchers.IO) {
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
                        handleResponse(response, apiType)
                    }
                }
                API_EXPORT_DB -> {
                    val response = RetrofitClient.apiService.exportDB().execute()
                    handleResponse(response, apiType)
                }
                API_CLEAR_DB -> {
                    val response = RetrofitClient.apiService.clearDB().execute()
                    handleResponse(response, apiType)
                }
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

    private fun handleResponse(response: retrofit2.Response<ResponseBody>, apiType: String): Result {
        return if (response.isSuccessful) {
            Log.d("ApiWorker", "Data sent successfully")

            when (apiType) {
                API_SORT_DRAWER -> {
                    try {
                        val responseBody = response.body()
                        val jsonString = responseBody?.string()
                        val jsonElement = JsonParser.parseString(jsonString)
                        Log.d("Sort", jsonElement.isJsonArray.toString())
                        Log.d("Sort", jsonString.toString())
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
                }
                else -> {

                }
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "$apiType Success", Toast.LENGTH_SHORT).show()
            }

            Result.success()
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "$apiType failed to send data: ${response.errorBody()?.string()}", Toast.LENGTH_LONG).show()
            }
            Log.e("ApiWorker", "Failed to send data: ${response.errorBody()?.string()}")
            Result.retry()
        }
    }
}
