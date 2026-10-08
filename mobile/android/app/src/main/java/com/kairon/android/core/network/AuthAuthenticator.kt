package com.kairon.android.core.network

import com.kairon.android.core.auth.TokenStore
import com.kairon.android.core.logging.AppLog
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-flight 401 -> refresh -> retry, mirroring the web app's own
 * `client.ts` wrapper (M11 D12). Uses a plain, authenticator-free OkHttp call
 * to `/auth/refresh` directly (not Retrofit/[com.kairon.android.client.api.AuthControllerApi])
 * so this class has no dependency cycle back through the authenticated
 * [OkHttpClient] it is itself installed on.
 *
 * <p>Exactly one refresh happens per 401 wave, guarded by [refreshMutex]:
 * every concurrent 401'd request blocks on the same mutex and, once it's
 * released, re-reads whatever token the winner stored rather than refreshing
 * again. A 401 on the *refresh call itself* (an expired/reused/revoked
 * refresh token) clears the session instead of retrying.
 */
private const val TAG = "AuthAuthenticator"

@Singleton
class AuthAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    @RawHttpClient private val rawHttpClient: OkHttpClient,
    private val json: Json,
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        val url = response.request.url.toString()
        if (responseCount(response) >= 2) {
            // Already retried once for this chain of requests — a fresh refresh still 401'd.
            AppLog.w(TAG, "session.cleared", mapOf("reason" to "secondUnauthorized", "url" to url))
            tokenStore.clear()
            return null
        }
        AppLog.d(TAG, "authenticate.unauthorized", mapOf("url" to url))
        val attemptedWithToken = response.request.header("Authorization")
        val refreshedToken = runBlocking {
            refreshMutex.withLock {
                val current = tokenStore.currentAccessToken()
                // Someone else already refreshed while we were waiting for the lock.
                if (current != null && "Bearer $current" != attemptedWithToken) {
                    AppLog.d(TAG, "authenticate.reusingConcurrentRefresh", mapOf("url" to url))
                    current
                } else {
                    refreshAccessToken()
                }
            }
        }
        if (refreshedToken == null) {
            AppLog.w(TAG, "authenticate.giveUp", mapOf("url" to url))
            return null
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer $refreshedToken")
            .build()
    }

    private fun refreshAccessToken(): String? {
        val refreshToken = tokenStore.currentRefreshToken()
        if (refreshToken == null) {
            AppLog.w(TAG, "session.cleared", mapOf("reason" to "noRefreshTokenStored"))
            return null
        }
        AppLog.d(TAG, "refresh.start", emptyMap())
        val requestBody = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(mapOf("refreshToken" to kotlinx.serialization.json.JsonPrimitive(refreshToken))),
        ).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(NetworkConfig.BASE_URL + "api/v1/auth/refresh")
            .post(requestBody)
            .build()

        return try {
            rawHttpClient.newCall(request).execute().use { httpResponse ->
                // HttpErrorLoggingInterceptor (installed on this same rawHttpClient)
                // already logged the status/body if httpResponse isn't successful.
                if (!httpResponse.isSuccessful) {
                    AppLog.w(TAG, "session.cleared", mapOf("reason" to "refreshRejected", "code" to httpResponse.code))
                    tokenStore.clear()
                    return null
                }
                val bodyText = httpResponse.body.string()
                val parsed = json.parseToJsonElement(bodyText) as? JsonObject
                val newAccessToken = parsed?.get("accessToken")?.jsonPrimitive?.content
                if (parsed == null || newAccessToken == null) {
                    AppLog.e(TAG, "refresh.unparseableResponse", mapOf("code" to httpResponse.code))
                    return null
                }
                val newRefreshToken = parsed["refreshToken"]?.jsonPrimitive?.content ?: refreshToken
                tokenStore.store(newAccessToken, newRefreshToken)
                AppLog.i(TAG, "refresh.success", emptyMap())
                newAccessToken
            }
        } catch (ex: Exception) {
            // IOException (connect/timeout) is already logged by
            // HttpErrorLoggingInterceptor; this also catches a malformed JSON
            // body, which isn't.
            AppLog.e(TAG, "refresh.failed", throwable = ex)
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
