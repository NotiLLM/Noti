package org.muilab.notigpt.data.remote.n8n

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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

    // If you want to keep it configurable, you can still read from SharedPreferencesManager,
    // but since the webhook prefix is fixed (webhook/ and webhook-test/), we hard-code the domain here.
    // Pointed at the locally-hosted n8n instance on port 5678. A prior LAN IP (10.50.148.125) hit a
    // Wi-Fi-specific quirk where the app's own socket could not complete a TCP handshake to the LAN IP
    // even though shell/adb and Chrome on the same phone reached it fine at the same moment — root
    // cause undetermined after ruling out SELinux, Doze/standby, Data Saver, VPN/lockdown, per-UID
    // ip-rule routing, and per-network proxy. If this IP hits the same issue, fall back to
    // `adb reverse tcp:5678 tcp:5678` (USB) and point BASE_URL at 127.0.0.1 instead.
    // Swap to "https://n8n.udchen.tw/" (or whatever public host you settle on) once the workflows move.
    // Locally-hosted n8n on the LAN. On Android 16+ (API 36) this requires the ACCESS_LOCAL_NETWORK
    // runtime permission (Local Network Protection) — declared in the manifest and requested in
    // MainActivity. Without it, connections here silently time out while internet access still works.
    // Swap to "https://n8n.udchen.tw/" (or whatever public host you settle on) once the workflows move.
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