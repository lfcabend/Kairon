package com.kairon.android.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Contextual serializers for the `@Contextual`-annotated fields the
 * `:openapi-client` generator emits for every non-multiplatform JDK type
 * (UUID/LocalDate/OffsetDateTime/BigDecimal — kotlinx.serialization ships
 * none of these itself). [NetworkModule.json] registers all four on its
 * `Json`'s `serializersModule`; without that, decoding *any* real response
 * containing one of these types throws
 * `SerializationException: Serializer for class '...' is not found` —
 * invisible in this app's existing unit tests, which all build Retrofit
 * `Response`s from hand-constructed Kotlin objects rather than real JSON,
 * but fatal the moment a live response is decoded (surfaced 2026-10-08 via
 * the new structured HTTP-error logging, on `register`).
 *
 * UUID/LocalDate/OffsetDateTime round-trip as JSON strings (Jackson's
 * default on the backend); BigDecimal fields are `type: number` in
 * `openapi.yaml` — a bare JSON numeric literal, not a string — so that one
 * serializer goes through [JsonEncoder]/[JsonDecoder] directly instead of
 * `encodeString`/`decodeString`, which would reject an unquoted token.
 */
object UuidSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("java.util.UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("java.time.OffsetDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: OffsetDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): OffsetDateTime = OffsetDateTime.parse(decoder.decodeString())
}

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("java.math.BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        (encoder as JsonEncoder).encodeJsonElement(JsonPrimitive(value))
    }

    override fun deserialize(decoder: Decoder): BigDecimal =
        BigDecimal((decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content)
}
