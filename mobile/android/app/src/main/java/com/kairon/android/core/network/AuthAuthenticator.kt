package com.kairon.android.core.network

import com.kairon.android.core.auth.TokenStore
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
@Singleton
class AuthAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    @RawHttpClient private val rawHttpClient: OkHttpClient,
    private val json: Json,
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) {
            // Already retried once for this chain of requests — a fresh refresh still 401'd.
            tokenStore.clear()
            return null
        }
        val attemptedWithToken = response.request.header("Authorization")
        val refreshedToken = runBlocking {
            refreshMutex.withLock {
                val current = tokenStore.currentAccessToken()
                // Someone else already refreshed while we were waiting for the lock.
                if (current != null && "Bearer $current" != attemptedWithToken) {
                    current
                } else {
                    refreshAccessToken()
                }
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $refreshedToken")
            .build()
    }

    private fun refreshAccessToken(): String? {
        val refreshToken = tokenStore.currentRefreshToken() ?: return null
        val requestBody = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(mapOf("refreshToken" to kotlinx.serialization.json.JsonPrimitive(refreshToken))),
        ).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(NetworkConfig.BASE_URL + "api/v1/auth/refresh")
            .post(requestBody)
            .build()

        rawHttpClient.newCall(request).execute().use { httpResponse ->
            if (!httpResponse.isSuccessful) {
                tokenStore.clear()
                return null
            }
            val bodyText = httpResponse.body.string()
            val parsed = json.parseToJsonElement(bodyText) as? JsonObject ?: return null
            val newAccessToken = parsed["accessToken"]?.jsonPrimitive?.content ?: return null
            val newRefreshToken = parsed["refreshToken"]?.jsonPrimitive?.content ?: refreshToken
            tokenStore.store(newAccessToken, newRefreshToken)
            return newAccessToken
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
