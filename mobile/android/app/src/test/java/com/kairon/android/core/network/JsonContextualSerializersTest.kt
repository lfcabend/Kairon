package com.kairon.android.core.network

import com.kairon.android.client.model.AuthResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Regression coverage for the bug these serializers fix: every other test
 * in this app builds a Retrofit `Response` from a hand-constructed Kotlin
 * object (`Response.success(AuthResponse(...))`), which never exercises
 * real JSON decoding — so the generated client's `@Contextual` fields
 * silently had no registered serializer until this was caught live against
 * `register` (2026-10-08). This mirrors [NetworkModule.json]'s own `Json`
 * configuration against an actual response body.
 */
class JsonContextualSerializersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule {
            contextual(UUID::class, UuidSerializer)
            contextual(LocalDate::class, LocalDateSerializer)
            contextual(OffsetDateTime::class, OffsetDateTimeSerializer)
            contextual(BigDecimal::class, BigDecimalSerializer)
        }
    }

    @Test
    fun decodesAnAuthResponseWithANestedUuid() {
        val body = """
            {"accessToken":"a","refreshToken":"r","user":{"id":"123e4567-e89b-12d3-a456-426614174000","email":"ada@example.com"}}
        """.trimIndent()

        val response = json.decodeFromString<AuthResponse>(body)

        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), response.user?.id)
    }

    @Test
    fun decodesABareJsonNumberIntoABigDecimal() {
        assertEquals(BigDecimal("5.5"), json.decodeFromString(BigDecimalSerializer, "5.5"))
    }

    @Test
    fun decodesQuotedIsoStringsIntoLocalDateAndOffsetDateTime() {
        assertEquals(LocalDate.of(2026, 10, 8), json.decodeFromString(LocalDateSerializer, "\"2026-10-08\""))
        assertEquals(
            OffsetDateTime.parse("2026-10-08T10:00:00Z"),
            json.decodeFromString(OffsetDateTimeSerializer, "\"2026-10-08T10:00:00Z\""),
        )
    }
}
