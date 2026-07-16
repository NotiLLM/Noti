package org.muilab.notigpt.data.remote.auth

import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds Firebase identity and app-attestation tokens when they are already obtainable.
 *
 * This deliberately fails open while the temporary n8n server ignores these headers. The future
 * GCP service must fail closed, verify both tokens, and derive the UID from the verified ID token;
 * it must never trust the legacy caller-provided `userId` payload field.
 */
class FirebaseTokenInterceptor(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val appCheck: FirebaseAppCheck = FirebaseAppCheck.getInstance(),
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()

        runCatching {
            val user = auth.currentUser ?: return@runCatching
            val tokenResult = Tasks.await(user.getIdToken(false), TOKEN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            tokenResult.token?.takeIf(String::isNotBlank)?.let { token ->
                requestBuilder.header(AUTHORIZATION, "Bearer $token")
            }
        }

        runCatching {
            val tokenResult = Tasks.await(
                appCheck.getAppCheckToken(false),
                TOKEN_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            tokenResult.token.takeIf(String::isNotBlank)?.let { token ->
                requestBuilder.header(APP_CHECK_HEADER, token)
            }
        }

        return chain.proceed(requestBuilder.build())
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
        const val TOKEN_TIMEOUT_SECONDS = 5L
    }
}
