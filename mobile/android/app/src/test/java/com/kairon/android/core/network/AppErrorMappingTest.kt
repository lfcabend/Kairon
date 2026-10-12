package com.kairon.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private fun apiException(status: Int, body: String?): ApiException =
    ApiException(httpStatus = status, requestId = "req-1", problemBody = body, message = "failed")

class AppErrorMappingTest {

    @Test
    fun aSocketTimeoutMapsToConnectivity() {
        assertEquals(AppError.Connectivity, SocketTimeoutException().toAppError())
    }

    @Test
    fun anUnknownHostMapsToConnectivity() {
        assertEquals(AppError.Connectivity, UnknownHostException().toAppError())
    }

    @Test
    fun aConnectExceptionMapsToConnectivity() {
        assertEquals(AppError.Connectivity, ConnectException().toAppError())
    }

    @Test
    fun aPlainIOExceptionMapsToConnectivity() {
        assertEquals(AppError.Connectivity, IOException("boom").toAppError())
    }

    @Test
    fun aFiveHundredMapsToServerErrorRegardlessOfItsDetail() {
        val ex = apiException(500, """{"status":500,"detail":"An unexpected error occurred."}""")

        val error = ex.toAppError()

        assertEquals(AppError.ServerError(500), error)
        // D3 — the backend's own detail is ignored for this category.
        assertEquals("Kairon's server ran into a problem. Please try again later.", error.userMessage())
    }

    @Test
    fun aFourHundredWithFieldErrorsMapsToValidation() {
        val ex = apiException(
            400,
            """{"status":400,"detail":"Request validation failed.",
                "errors":[{"field":"password","message":"size must be between 8 and 100"}]}""",
        )

        val error = ex.toAppError()

        assertTrue(error is AppError.Validation)
        val fieldErrors = (error as AppError.Validation).fieldErrors
        assertEquals(1, fieldErrors.size)
        assertEquals("password", fieldErrors[0].field)
        assertEquals("size must be between 8 and 100", fieldErrors[0].message)
    }

    @Test
    fun aFourHundredWithAMalformedEmailShapedValidationBodyFromLoginIsStillValidationNeverLoginRejected() {
        // D2 — the 400-before-401 branch order must win even when isLoginOrRegister=true,
        // so a bad-request shape from the login form is never misread as a credentials rejection.
        val ex = apiException(
            400,
            """{"status":400,"detail":"Request validation failed.",
                "errors":[{"field":"email","message":"must be a well-formed email address"}]}""",
        )

        val error = ex.toAppError(isLoginOrRegister = true)

        assertTrue(error is AppError.Validation)
        assertEquals("email", (error as AppError.Validation).fieldErrors.single().field)
    }

    @Test
    fun aFourHundredWithNoFieldErrorsMapsToDomain() {
        val ex = apiException(400, """{"status":400,"detail":"This would create a circular dependency."}""")

        val error = ex.toAppError()

        assertEquals(AppError.Domain("This would create a circular dependency."), error)
    }

    @Test
    fun aFourOhOneFromLoginMapsToLoginRejected() {
        val ex = apiException(401, """{"status":401,"detail":"Invalid email or password."}""")

        val error = ex.toAppError(isLoginOrRegister = true)

        assertEquals(AppError.LoginRejected("Invalid email or password."), error)
    }

    @Test
    fun theSameFourOhOneWithoutTheLoginFlagNeverBecomesLoginRejected() {
        val ex = apiException(401, """{"status":401,"detail":"Invalid email or password."}""")

        val error = ex.toAppError(isLoginOrRegister = false)

        assertTrue(error !is AppError.LoginRejected)
        assertEquals(AppError.Domain("Invalid email or password."), error)
    }

    @Test
    fun aFourOhFourWithDetailMapsToNotFound() {
        val ex = apiException(404, """{"status":404,"detail":"Task not found."}""")

        assertEquals(AppError.NotFound("Task not found."), ex.toAppError())
    }

    @Test
    fun aFourOhNineWithDetailMapsToConflict() {
        val ex = apiException(409, """{"status":409,"detail":"This task was modified by another request. Reload and try again."}""")

        assertEquals(
            AppError.Conflict("This task was modified by another request. Reload and try again."),
            ex.toAppError(),
        )
    }

    @Test
    fun aMalformedBodyMapsToUnknown() {
        val ex = apiException(400, "not json")

        assertEquals(AppError.Unknown, ex.toAppError())
    }

    @Test
    fun anAbsentBodyMapsToUnknown() {
        val ex = apiException(403, null)

        assertEquals(AppError.Unknown, ex.toAppError())
    }

    @Test
    fun anUnexpectedRuntimeExceptionMapsToUnknown() {
        assertEquals(AppError.Unknown, RuntimeException("boom").toAppError())
    }

    @Test
    fun connectivitySyncBannerMessageAppendsTheLastSavedDataSuffix() {
        assertTrue(AppError.Connectivity.syncBannerMessage().endsWith("Showing your last saved data."))
    }

    @Test
    fun nonConnectivitySyncBannerMessageHasNoSuffix() {
        assertEquals(AppError.ServerError(500).userMessage(), AppError.ServerError(500).syncBannerMessage())
    }
}
