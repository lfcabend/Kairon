package com.kairon.android.core.network

import com.kairon.android.core.auth.TokenStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthAuthenticatorTest {

    private val tokenStore = mockk<TokenStore>(relaxed = true)
    private val rawHttpClient = mockk<OkHttpClient>()
    private val call = mockk<Call>()
    private lateinit var authenticator: AuthAuthenticator

    // Backed by a mutable var rather than a flat `returns old-access`: the real TokenStore's
    // currentAccessToken() DOES reflect the winning thread's store() call, and the
    // single-flight "did someone already refresh while I waited?" check in
    // AuthAuthenticator depends on that — a static mock would make every thread refresh.
    private var currentAccess = "old-access"

    @Before
    fun setUp() {
        authenticator = AuthAuthenticator(tokenStore, rawHttpClient, Json { ignoreUnknownKeys = true })
        every { tokenStore.currentRefreshToken() } returns "old-refresh"
        every { tokenStore.currentAccessToken() } answers { currentAccess }
        every { tokenStore.store(any(), any()) } answers { currentAccess = firstArg() }
        every { rawHttpClient.newCall(any()) } returns call
        every { call.execute() } returns refreshResponse()
    }

    private fun originalRequest(): Request = Request.Builder()
        .url("http://10.0.2.2:8080/kairon/api/v1/todo")
        .header("Authorization", "Bearer old-access")
        .build()

    private fun unauthorizedResponse(request: Request, priorResponse: Response? = null): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(priorResponse)
            .build()

    private fun refreshResponse(): Response {
        val body = """{"accessToken":"new-access","refreshToken":"new-refresh"}"""
            .toResponseBody("application/json".toMediaType())
        return Response.Builder()
            .request(Request.Builder().url("http://10.0.2.2:8080/kairon/api/v1/auth/refresh").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    @Test
    fun aFirstFortyOneRefreshesOnceAndRetriesWithTheNewToken() {
        val request = originalRequest()

        val retried = authenticator.authenticate(null, unauthorizedResponse(request))

        assertEquals("Bearer new-access", retried?.header("Authorization"))
        verify(exactly = 1) { rawHttpClient.newCall(any()) }
    }

    @Test
    fun aSecondFortyOneRightAfterARefreshClearsTheSessionInsteadOfRetrying() {
        val request = originalRequest()
        val first = unauthorizedResponse(request)
        val second = unauthorizedResponse(request, priorResponse = first)

        val retried = authenticator.authenticate(null, second)

        assertNull(retried)
        verify(exactly = 1) { tokenStore.clear() }
    }

    @Test
    fun concurrentFortyOnesOnlyTriggerOneRefreshCall() {
        val request = originalRequest()
        val response = unauthorizedResponse(request)

        val threads = (1..8).map {
            Thread { authenticator.authenticate(null, response) }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        verify(exactly = 1) { rawHttpClient.newCall(any()) }
    }
}
