package com.kairon.android.core.auth

import com.kairon.android.client.api.AuthControllerApi
import com.kairon.android.client.model.LoginRequest
import com.kairon.android.client.model.LogoutRequest
import com.kairon.android.client.model.RegisterRequest
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps the generated auth endpoints; success stores both tokens (D6/§6.2). */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthControllerApi,
    private val tokenStore: TokenStore,
) {

    val signedIn get() = tokenStore.signedIn

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val response = authApi.login(LoginRequest(email = email, password = password))
        val body = response.body()
        val accessToken = body?.accessToken
        val refreshToken = body?.refreshToken
        if (!response.isSuccessful || accessToken == null || refreshToken == null) {
            error("Login failed: HTTP ${response.code()}")
        }
        tokenStore.store(accessToken, refreshToken)
    }

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        timezone: String?,
    ): Result<Unit> = runCatching {
        val response = authApi.register(
            RegisterRequest(email = email, password = password, displayName = displayName, timezone = timezone),
        )
        val body = response.body()
        val accessToken = body?.accessToken
        val refreshToken = body?.refreshToken
        if (!response.isSuccessful || accessToken == null || refreshToken == null) {
            error("Registration failed: HTTP ${response.code()}")
        }
        tokenStore.store(accessToken, refreshToken)
    }

    suspend fun logout() {
        val refreshToken = tokenStore.currentRefreshToken()
        runCatching { authApi.logout(LogoutRequest(refreshToken = refreshToken)) }
        tokenStore.clear()
    }

    suspend fun logoutAll() {
        runCatching { authApi.logoutAll() }
        tokenStore.clear()
    }
}
