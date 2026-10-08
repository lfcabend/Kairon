package com.kairon.android.core.network

/**
 * Thrown by [bodyOrThrow] for a non-2xx, or unexpectedly empty-body, Retrofit
 * response. Carries the server's own correlation id (`X-Request-Id`, echoed
 * by every backend response — see CLAUDE.md "Logging") and the raw (redacted)
 * response body so a caller — or whoever reads the log line this was already
 * reported under — can match this failure straight back to the backend's own
 * logs for that request.
 */
class ApiException(
    val httpStatus: Int,
    val requestId: String?,
    val problemBody: String?,
    message: String,
) : Exception(message)
