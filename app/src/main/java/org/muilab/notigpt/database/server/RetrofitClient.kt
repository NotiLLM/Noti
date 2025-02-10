package org.muilab.notigpt.database.server

import okhttp3.OkHttpClient
import org.muilab.notigpt.util.SharedPreferencesManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object RetrofitClient {
    @Volatile
    private var baseUrl: String = "http:/${SharedPreferencesManager.serverIP}:8000"
    private var retrofit: Retrofit? = null

    private fun createRetrofit(): Retrofit {
        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }

    val apiService: ApiService
        get() {
            if (retrofit == null) {
                retrofit = createRetrofit()
            }
            return retrofit!!.create(ApiService::class.java)
        }

    fun updateBaseUrl(newIp: String) {
        baseUrl = "http://$newIp:8000"
        retrofit = createRetrofit()  // Recreate Retrofit instance
    }
}
