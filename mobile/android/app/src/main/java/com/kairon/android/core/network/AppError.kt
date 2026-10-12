package com.kairon.android.core.network

import java.io.IOException

/**
 * The single mapping every call site in the app uses from now on in place of
 * the old `ex.message ?: "<fallback>"` pattern (M11.7). Categories come from
 * the exception type + parsed [ProblemDetail], never from string-matching
 * `.message` — see docs/milestones/M11.7-android-error-handling.md D1/D2.
 */
sealed interface AppError {
    data object Connectivity : AppError
    data class ServerError(val status: Int) : AppError
    data class Validation(val fieldErrors: List<FieldError>) : AppError
    data class LoginRejected(val detail: String) : AppError
    data object SessionExpired : AppError
    data class NotFound(val detail: String) : AppError
    data class Conflict(val detail: String) : AppError
    data class Domain(val detail: String) : AppError
    data object Unknown : AppError
}

data class FieldError(val field: String, val message: String)

/**
 * [isLoginOrRegister] should be passed `true` only from `LoginViewModel`/
 * `RegisterViewModel`'s own `onFailure` — every other call site uses the
 * default, so a stray 401 elsewhere (e.g. `AuthAuthenticator` giving up)
 * never gets misread as a credentials rejection.
 */
fun Throwable.toAppError(isLoginOrRegister: Boolean = false): AppError = when (this) {
    is ApiException -> when {
        httpStatus >= 500 -> AppError.ServerError(httpStatus)
        else -> {
            val problem = ProblemDetail.parse(problemBody)
            val fieldErrors = problem?.errors
            val detail = problem?.detail
            when {
                httpStatus == 400 && !fieldErrors.isNullOrEmpty() ->
                    AppError.Validation(fieldErrors.map { FieldError(it.field, it.message) })
                httpStatus == 401 && isLoginOrRegister ->
                    AppError.LoginRejected(detail ?: "Invalid email or password.")
                httpStatus == 404 && !detail.isNullOrBlank() -> AppError.NotFound(detail)
                httpStatus == 409 && !detail.isNullOrBlank() -> AppError.Conflict(detail)
                !detail.isNullOrBlank() -> AppError.Domain(detail)
                else -> AppError.Unknown
            }
        }
    }
    is IOException -> AppError.Connectivity
    else -> AppError.Unknown
}

fun AppError.userMessage(): String = when (this) {
    AppError.Connectivity -> "Couldn't connect to the server. Check your connection and try again."
    is AppError.ServerError -> "Kairon's server ran into a problem. Please try again later."
    is AppError.Validation -> fieldErrors.joinToString("\n") { it.message }
    is AppError.LoginRejected -> detail
    AppError.SessionExpired -> "Your session has expired. Please log in again."
    is AppError.NotFound -> detail
    is AppError.Conflict -> detail
    is AppError.Domain -> detail
    AppError.Unknown -> "Something went wrong. Please try again."
}

/**
 * [userMessage] with [AppError.Connectivity]'s D3 sync-context suffix —
 * used by every background-sync [com.kairon.android.core.ui.ErrorBanner]
 * (D6), never by a foreground action's [com.kairon.android.core.ui.InlineError].
 */
fun AppError.syncBannerMessage(): String = when (this) {
    AppError.Connectivity -> "${userMessage()} Showing your last saved data."
    else -> userMessage()
}
