package com.kairon.android.core.auth

import com.kairon.android.client.api.AuthControllerApi
import com.kairon.android.client.model.LoginRequest
import com.kairon.android.client.model.LogoutRequest
import com.kairon.android.client.model.RegisterRequest
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"

/** Wraps the generated auth endpoints; success stores both tokens (D6/§6.2). */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthControllerApi,
    private val tokenStore: TokenStore,
    private val sessionClearer: SessionClearer,
) {

    val signedIn get() = tokenStore.signedIn

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val response = authApi.login(LoginRequest(email = email, password = password))
        val body = response.bodyOrThrow(TAG, "login")
        val accessToken = body.accessToken
        val refreshToken = body.refreshToken
        if (accessToken == null || refreshToken == null) {
            error("Login succeeded but response carried no tokens")
        }
        tokenStore.store(accessToken, refreshToken)
        AppLog.i(TAG, "login.success")
    }.onFailure { ex -> AppLog.e(TAG, "login.failed", throwable = ex) }

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        timezone: String?,
    ): Result<Unit> = runCatching {
        val response = authApi.register(
            RegisterRequest(email = email, password = password, displayName = displayName, timezone = timezone),
        )
        val body = response.bodyOrThrow(TAG, "register")
        val accessToken = body.accessToken
        val refreshToken = body.refreshToken
        if (accessToken == null || refreshToken == null) {
            error("Registration succeeded but response carried no tokens")
        }
        tokenStore.store(accessToken, refreshToken)
        AppLog.i(TAG, "register.success")
    }.onFailure { ex -> AppLog.e(TAG, "register.failed", throwable = ex) }

    suspend fun logout() {
        val refreshToken = tokenStore.currentRefreshToken()
        runCatching { authApi.logout(LogoutRequest(refreshToken = refreshToken)) }
            .onFailure { ex -> AppLog.w(TAG, "logout.requestFailed", throwable = ex) }
        sessionClearer.clearSession()
        AppLog.i(TAG, "logout.done")
    }

    suspend fun logoutAll() {
        runCatching { authApi.logoutAll() }
            .onFailure { ex -> AppLog.w(TAG, "logoutAll.requestFailed", throwable = ex) }
        sessionClearer.clearSession()
        AppLog.i(TAG, "logoutAll.done")
    }
}
