package com.kairon.android.core.auth

import com.kairon.android.client.api.AuthControllerApi
import com.kairon.android.client.model.AuthResponse
import com.kairon.android.client.model.LoginRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AuthRepositoryTest {

    private val authApi = mockk<AuthControllerApi>()
    private val tokenStore = mockk<TokenStore>(relaxed = true)
    private val sessionClearer = mockk<SessionClearer>(relaxed = true)
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        repository = AuthRepository(authApi, tokenStore, sessionClearer)
    }

    @Test
    fun loginStoresBothTokens() = runTest {
        coEvery { authApi.login(LoginRequest(email = "ada@example.com", password = "secret")) } returns
            Response.success(AuthResponse(accessToken = "access-1", refreshToken = "refresh-1"))

        val result = repository.login("ada@example.com", "secret")

        assertTrue(result.isSuccess)
        coVerify { tokenStore.store("access-1", "refresh-1") }
    }

    @Test
    fun loginWithoutATokenInTheBodyFails() = runTest {
        coEvery { authApi.login(LoginRequest(email = "ada@example.com", password = "wrong")) } returns
            successEmptyBody()

        val result = repository.login("ada@example.com", "wrong")

        assertFalse(result.isSuccess)
    }

    private fun successEmptyBody(): Response<AuthResponse> =
        Response.success(AuthResponse(accessToken = null, refreshToken = null))
}
