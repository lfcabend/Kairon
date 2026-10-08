package com.kairon.android.core.network

private val SECRET_FIELD_PATTERN = Regex(
    "\"(accessToken|refreshToken|password|token)\"\\s*:\\s*\"[^\"]*\"",
    RegexOption.IGNORE_CASE,
)

/**
 * Defense-in-depth for response bodies we log — see CLAUDE.md "Never log
 * passwords, raw or hashed tokens". None of this app's error responses
 * (RFC 7807 problem+json) are expected to carry these fields, but a
 * success-shaped body logged by mistake (e.g. a proxy/error page echoing the
 * request) shouldn't leak one into logcat.
 */
fun redactSecrets(raw: String): String = SECRET_FIELD_PATTERN.replace(raw) { match ->
    "\"${match.groupValues[1]}\":\"***\""
}
