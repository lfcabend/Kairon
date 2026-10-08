package com.kairon.android.core.logging

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Structured logging facade over [Log] — the Android-side counterpart to the
 * backend's "every controller/service logs" convention (see CLAUDE.md
 * "Logging"): each line is `event {"field":"value",...}` so a single logcat
 * line stays greppable/parseable instead of free-form prose, and an
 * exception's class/message/throw-site/cause-chain are always broken out as
 * fields rather than buried in a stack dump.
 *
 * Plain `android.util.Log`, not Timber — keeps this module dependency-free.
 * `app/build.gradle.kts` sets `testOptions.unitTests.isReturnDefaultValues`
 * so the unmocked [Log] calls return quietly under plain JUnit instead of
 * throwing "not mocked".
 *
 * Never pass passwords, tokens, or full request bodies as fields — response
 * bodies logged by [com.kairon.android.core.network.HttpErrorLoggingInterceptor]
 * and [com.kairon.android.core.network.bodyOrThrow] are run through
 * [com.kairon.android.core.network.redactSecrets] first as defense-in-depth.
 */
object AppLog {

    fun d(tag: String, event: String, fields: Map<String, Any?> = emptyMap()) {
        Log.d(tag, line(event, fields))
    }

    fun i(tag: String, event: String, fields: Map<String, Any?> = emptyMap()) {
        Log.i(tag, line(event, fields))
    }

    fun w(tag: String, event: String, fields: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) {
        Log.w(tag, line(event, withThrowableFields(fields, throwable)), throwable)
    }

    fun e(tag: String, event: String, fields: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) {
        Log.e(tag, line(event, withThrowableFields(fields, throwable)), throwable)
    }

    private fun withThrowableFields(fields: Map<String, Any?>, throwable: Throwable?): Map<String, Any?> =
        if (throwable == null) fields else fields + throwable.describe()

    private fun line(event: String, fields: Map<String, Any?>): String {
        if (fields.isEmpty()) return event
        val json = buildJsonObject { fields.forEach { (key, value) -> put(key, value.toJsonElement()) } }
        return "$event $json"
    }
}

/** `exceptionType`/`exceptionMessage`/[Throwable.origin]/[Throwable.causeChain], ready to merge into a log call's fields. */
fun Throwable.describe(): Map<String, Any?> = mapOf(
    "exceptionType" to this::class.qualifiedName,
    "exceptionMessage" to message,
    "origin" to origin(),
    "causeChain" to causeChain(),
)

/** The deepest stack frame still inside our own code — the actual throw site, not OkHttp/Retrofit/coroutine plumbing. */
fun Throwable.origin(): String? = stackTrace
    .firstOrNull { it.className.startsWith("com.kairon.android") }
    ?.let { "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }

/** "Type: message" for this throwable and every [Throwable.cause] below it, capped so a cyclical/deep chain can't run away. */
fun Throwable.causeChain(limit: Int = 6): List<String> {
    val chain = mutableListOf<String>()
    var current: Throwable? = this
    while (current != null && chain.size < limit) {
        chain += "${current::class.simpleName}: ${current.message}"
        current = current.cause.takeIf { it !== current }
    }
    return chain
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is List<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}
