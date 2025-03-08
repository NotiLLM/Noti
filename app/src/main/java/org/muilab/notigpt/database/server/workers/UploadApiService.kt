package org.muilab.notigpt.database.server.workers;

import okhttp3.RequestBody

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface UploadApiService {

    @POST("test-upload")
    fun upload(@Body data: RequestBody): Call<Void>

    companion object {
        fun create(): UploadApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://n8n.udchen.tw/webhook/") // Replace with your server URL
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(UploadApiService::class.java)
        }
    }
}