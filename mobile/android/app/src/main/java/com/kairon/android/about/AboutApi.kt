package com.kairon.android.about

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.GET

/**
 * `/actuator/info` — hand-written, not generated: actuator endpoints aren't
 * springdoc-documented controllers, so they don't appear in the backend's
 * OpenAPI spec (same reasoning the web app's own `about.ts` gives for
 * calling this endpoint directly rather than through the generated client).
 */
interface AboutApi {
    @GET("actuator/info")
    suspend fun info(): Response<JsonObject>
}
