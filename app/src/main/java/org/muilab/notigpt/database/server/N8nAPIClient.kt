package org.muilab.notigpt.database.server

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object N8nAPIClient {
    @Volatile
    private var retrofit: Retrofit? = null

    // If you want to keep it configurable, you can still read from SharedPreferencesManager,
    // but since you said prefix is n8n.udchen.tw/webhook-test/, we hard-code the domain here.
    private const val BASE_URL = "https://n8n.udchen.tw/"

    private fun createRetrofit(baseUrl: String = BASE_URL): Retrofit {
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
            .baseUrl(baseUrl) // e.g. https://n8n.udchen.tw/
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }

    val n8nAPIService: N8nAPIService
        get() {
            if (retrofit == null) {
                synchronized(this) {
                    if (retrofit == null) {
                        retrofit = createRetrofit()
                    }
                }
            }
            return retrofit!!.create(N8nAPIService::class.java)
        }

    fun updateBaseUrl(newBaseUrl: String) {
        synchronized(this) {
            retrofit = createRetrofit(newBaseUrl)
        }
    }
}