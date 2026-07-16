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
 * This object owns the mutable base URL used by settings/debug flows. Keep endpoint definitions in
 * N8nAPIService and workflow-specific request parsing in the dedicated clients or worker handlers.
 */
object N8nAPIClient {
    @Volatile
    private var retrofit: Retrofit? = null

    // The current temporary n8n host is public HTTPS. Endpoint paths remain BuildConfig values so
    // the Android client can migrate to the planned GCP service without reintroducing LAN access.
    private const val BASE_URL = "https://n8n.udchen.tw/"

    private fun createRetrofit(baseUrl: String = BASE_URL): Retrofit {
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
