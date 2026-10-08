package com.kairon.android.core.network

import com.kairon.android.core.logging.AppLog
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

private const val TAG = "HttpClient"
private const val MAX_LOGGED_BODY_BYTES = 4096L

/**
 * Installed on both [OkHttpClient]s in [NetworkModule] (D7's "every network
 * call, including the authenticator's own raw refresh call" — see M11
 * CLAUDE.md note on the `RawHttpClient`). [HttpLoggingInterceptor] in this
 * app only runs at `BASIC` (method/url/code, no bodies — logging request
 * bodies would print the login password in plaintext), so this interceptor
 * is the only thing that logs a response body, and only for a non-2xx
 * response, via [Response.peekBody] so the real body stream Retrofit/callers
 * read afterwards is untouched.
 *
 * Also logs (and rethrows unchanged) a transport-level [IOException] that
 * happens before any [Response] exists at all — DNS failure, connection
 * refused, timeout — which nothing else in this app logs; without this,
 * those failures previously surfaced only as a bare `ex.message` in whatever
 * ViewModel's `runCatching` caught them.
 */
class HttpErrorLoggingInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = try {
            chain.proceed(request)
        } catch (ex: IOException) {
            AppLog.e(
                TAG,
                "request.failed",
                mapOf("method" to request.method, "url" to request.url.toString()),
                throwable = ex,
            )
            throw ex
        }

        if (response.isSuccessful) {
            AppLog.d(
                TAG,
                "response.ok",
                mapOf("method" to request.method, "url" to request.url.toString(), "code" to response.code),
            )
            return response
        }

        val body = response.peekBody(MAX_LOGGED_BODY_BYTES).string().let(::redactSecrets)
        AppLog.e(
            TAG,
            "response.httpError",
            mapOf(
                "method" to request.method,
                "url" to request.url.toString(),
                "code" to response.code,
                "requestId" to response.header("X-Request-Id"),
                "body" to body,
            ),
        )
        return response
    }
}
