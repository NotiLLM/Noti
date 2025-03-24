package org.muilab.notigpt.database.server

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object N8NRetrofitClient {
    @Volatile
    private var retrofit: Retrofit? = null

    private fun createRetrofit(): Retrofit {
        val baseUrl = "https://n8n.udchen.tw/webhook/"

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

    val n8nWebhookService: N8NWebhookService
        get() {
            if (retrofit == null) {
                synchronized(this) {
                    if (retrofit == null) {
                        retrofit = createRetrofit()
                    }
                }
            }
            return retrofit!!.create(N8NWebhookService::class.java)
        }
}
