package org.muilab.notigpt.data.remote.n8n

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit endpoint contract for posting payloads to n8n webhook paths.
 *
 * Keep this interface transport-only. Workflow payload shape, retries, and response interpretation should live
 * in clients or WorkManager handlers so the network contract stays small.
 */
interface N8nAPIService {

    @POST
    suspend fun postToWebhook(
        @Url webhookPath: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

}
