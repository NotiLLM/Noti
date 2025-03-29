package org.muilab.notigpt.database.server

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.muilab.notigpt.util.SharedPreferencesManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object DifyAPIClient {
    @Volatile
    private var retrofit: Retrofit? = null

    private fun createRetrofit(serverIP: String = SharedPreferencesManager.serverIP): Retrofit {
        val baseUrl = "http://$serverIP/"

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
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

    val difyAPIService: DifyAPIService
        get() {
            if (retrofit == null) {
                synchronized(this) {
                    if (retrofit == null) {
                        retrofit = createRetrofit()
                    }
                }
            }
            return retrofit!!.create(DifyAPIService::class.java)
        }

    fun updateBaseUrl(newIp: String) {
        synchronized(this) {
            retrofit = createRetrofit(newIp)  // ✅ Always recreate with the latest IP
        }
    }
}