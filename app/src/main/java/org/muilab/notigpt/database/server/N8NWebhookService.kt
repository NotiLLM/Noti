package org.muilab.notigpt.database.server

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface N8NWebhookService {
    @POST("update-notification")
    fun updateNotification(@Body updateNotificationRequest: UpdateNotificationRequest): Call<ResponseBody>
}