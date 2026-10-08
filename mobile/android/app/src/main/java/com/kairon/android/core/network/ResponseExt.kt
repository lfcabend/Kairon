package com.kairon.android.core.network

import com.kairon.android.core.logging.AppLog
import retrofit2.Response

/**
 * Unwraps a successful [Response] body, or logs the HTTP error in full
 * (status, URL, the backend's own `X-Request-Id`, and the response body)
 * and throws [ApiException] — the single place every call site in this app
 * extracts/logs a failed response, replacing the old pattern of a bare
 * `error("HTTP ${response.code()}")` with no detail and no log line at all.
 *
 * [event] should name the calling operation (e.g. "quickAdd", "sync") so the
 * log line reads as `event.httpError {...}` — call sites pass their own tag
 * (their class's simple name) so every line is still attributable.
 */
fun <T> Response<T>.bodyOrThrow(tag: String, event: String): T {
    requireSuccessful(tag, event)
    return body() ?: run {
        val requestId = headers()["X-Request-Id"]
        AppLog.e(
            tag,
            "$event.emptyBody",
            mapOf("code" to code(), "url" to raw().request.url.toString(), "requestId" to requestId),
        )
        throw ApiException(code(), requestId, null, "$event: empty response body (HTTP ${code()})")
    }
}

/**
 * Same error extraction/logging as [bodyOrThrow], for a call whose success
 * response carries no body (e.g. a `204 No Content` delete) — Retrofit
 * returns a `null` body for those even on success, so [bodyOrThrow] can't be
 * used for them without misreporting a successful delete as an empty-body
 * failure.
 */
fun <T> Response<T>.requireSuccessful(tag: String, event: String) {
    if (isSuccessful) return
    val requestId = headers()["X-Request-Id"]
    val problemBody = errorBody()?.string()?.let(::redactSecrets)
    AppLog.e(
        tag,
        "$event.httpError",
        mapOf(
            "code" to code(),
            "url" to raw().request.url.toString(),
            "requestId" to requestId,
            "body" to problemBody,
        ),
    )
    val requestIdSuffix = requestId?.let { " (requestId=$it)" } ?: ""
    throw ApiException(code(), requestId, problemBody, "$event failed: HTTP ${code()}$requestIdSuffix")
}
