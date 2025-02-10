package org.muilab.notigpt.database.server

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @POST("/sync_notification/")
    fun syncNotification(@Body data: RequestBody): Call<ResponseBody>
    @POST("/sync_query_embedding/")
    fun syncQueryEmbedding(@Body data: RequestBody): Call<ResponseBody>
    @POST("/fetch_baseline_embedding/")
    fun fetchBaselineEmbedding(): Call<ResponseBody>
    @POST("/update_notifications/")
    fun updateNotifications(@Body data: RequestBody): Call<ResponseBody>
    @POST("/insert_preference/")
    fun insertPreference(@Body data: RequestBody): Call<ResponseBody>
    @POST("/sort_notifications/")
    fun sortNotifications(@Body data: RequestBody): Call<ResponseBody>
    @GET("/export_db/")
    fun exportDB(): Call<ResponseBody>
    @DELETE("/clear_db/")
    fun clearDB(): Call<ResponseBody>
    @POST("/update_user/")
    fun updateUser(@Body data: RequestBody): Call<ResponseBody>
}
