package org.muilab.notigpt.database.server

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface N8nAPIService {

    // Example: webhookPath = "webhook-test/update-notification"
    // Full URL = BASE_URL + webhookPath
    @POST
    suspend fun postToWebhook(
        @Url webhookPath: String,
        @Body body: RequestBody
    ): Response<ResponseBody>
}