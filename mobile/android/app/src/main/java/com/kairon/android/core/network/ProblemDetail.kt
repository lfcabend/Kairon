package com.kairon.android.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * RFC 7807 `application/problem+json` body shape, matching
 * `GlobalExceptionHandler`/`ApiException` on the backend (`type`/`title`/
 * `status`/`detail`/`instance`, plus the `errors[]` array added for Bean
 * Validation failures). See docs/milestones/M11.7-android-error-handling.md.
 */
@Serializable
data class ProblemDetail(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<FieldProblem>? = null,
) {
    companion object {
        /**
         * Lenient parse of [ApiException.problemBody] — returns `null` on any
         * parse failure (malformed JSON, absent body) rather than throwing,
         * so a bad body degrades to [AppError.Unknown] instead of crashing
         * the error-handling path itself.
         */
        fun parse(raw: String?): ProblemDetail? {
            if (raw.isNullOrBlank()) return null
            return runCatching { lenientJson.decodeFromString<ProblemDetail>(raw) }.getOrNull()
        }
    }
}

@Serializable
data class FieldProblem(val field: String, val message: String)
