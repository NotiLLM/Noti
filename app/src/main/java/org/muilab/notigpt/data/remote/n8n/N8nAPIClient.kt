package org.muilab.notigpt.data.remote.n8n

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.data.remote.auth.FirebaseTokenInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit client holder for the n8n webhook API.
 *
 * Endpoint definitions live in N8nAPIService and workflow-specific request parsing stays in dedicated clients
 * or worker handlers.
 */
object N8nAPIClient {
    @Volatile
    private var retrofit: Retrofit? = null

    private fun createRetrofit(baseUrl: String = BuildConfig.N8N_BASE_URL): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            // Never log notification-derived request/response bodies. Debug builds retain only
            // method/URL/status/timing metadata; release builds emit no OkHttp traffic logs.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(FirebaseTokenInterceptor())
            .addInterceptor(UsageHeaderInterceptor())
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

}
