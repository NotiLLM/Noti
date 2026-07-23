package org.muilab.notigpt.data.remote.n8n

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Reads the `X-Noti-Usage` response header that every n8n workflow and `local_n8n_server` attach
 * (see plans/3-invitation-and-llm-usage.md) and hands it to [UsageLogger].
 *
 * This is the single shared place usage capture happens; it never touches the response body, so
 * it works identically across the ~10 differently-shaped per-stage response parsers without
 * changing any of them.
 */
class UsageHeaderInterceptor(
    private val onUsage: (stage: String, headerValue: String) -> Unit = UsageLogger::logAsync,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        response.header(USAGE_HEADER)?.takeIf(String::isNotBlank)?.let { headerValue ->
            onUsage(stageFromPath(request.url.encodedPath), headerValue)
        }
        return response
    }

    internal fun stageFromPath(encodedPath: String): String =
        encodedPath.removePrefix("/").removePrefix("webhook/")

    private companion object {
        const val USAGE_HEADER = "X-Noti-Usage"
    }
}
