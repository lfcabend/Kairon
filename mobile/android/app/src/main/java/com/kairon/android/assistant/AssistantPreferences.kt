package com.kairon.android.assistant

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class AssistantFeatureToggle(val enabled: Boolean = false)

/** Mirrors the backend's `app_user.preferences.assistant` shape (`identity.app.AssistantPreferenceMapper`). */
data class AssistantPreferences(
    val todoSuggestions: AssistantFeatureToggle = AssistantFeatureToggle(),
    val projectGeneration: AssistantFeatureToggle = AssistantFeatureToggle(),
    val projectEditing: AssistantFeatureToggle = AssistantFeatureToggle(),
    val executionSummaries: AssistantFeatureToggle = AssistantFeatureToggle(),
    val journalReflection: AssistantFeatureToggle = AssistantFeatureToggle(),
    val modelOverride: String? = null,
    val tone: String = "balanced",
)

/**
 * Defensive parse of `MeResponse.preferences["assistant"]` — a missing key, a
 * wrong-shaped value, or a missing sub-field all fall back to the same
 * defaults a brand-new account would show, mirroring the backend's own
 * `AssistantPreferenceMapper` posture (M11.6 D7).
 */
fun parseAssistantPreferences(preferences: Map<String, JsonElement>?): AssistantPreferences {
    val assistant = preferences?.get("assistant") as? JsonObject ?: return AssistantPreferences()
    val toneElement = assistant["tone"] as? JsonPrimitive
    val modelOverrideElement = assistant["modelOverride"] as? JsonPrimitive
    return AssistantPreferences(
        todoSuggestions = assistant.toggle("todoSuggestions"),
        projectGeneration = assistant.toggle("projectGeneration"),
        projectEditing = assistant.toggle("projectEditing"),
        executionSummaries = assistant.toggle("executionSummaries"),
        journalReflection = assistant.toggle("journalReflection"),
        modelOverride = modelOverrideElement?.takeIf { it !is JsonNull }?.content,
        tone = toneElement?.takeIf { it !is JsonNull }?.content ?: "balanced",
    )
}

private fun JsonObject.toggle(field: String): AssistantFeatureToggle {
    val sub = this[field] as? JsonObject ?: return AssistantFeatureToggle()
    val enabled = (sub["enabled"] as? JsonPrimitive)?.booleanOrNull ?: false
    return AssistantFeatureToggle(enabled)
}

private fun AssistantFeatureToggle.toJson(): JsonObject = buildJsonObject { put("enabled", enabled) }

private fun AssistantPreferences.toJson(): JsonObject = buildJsonObject {
    put("todoSuggestions", todoSuggestions.toJson())
    put("projectGeneration", projectGeneration.toJson())
    put("projectEditing", projectEditing.toJson())
    put("executionSummaries", executionSummaries.toJson())
    put("journalReflection", journalReflection.toJson())
    put("tone", tone)
    if (modelOverride != null) put("modelOverride", modelOverride) else put("modelOverride", JsonNull)
}

/**
 * Builds the full `PATCH /me` `preferences` body for a single-field change to
 * the assistant sub-object, preserving every other top-level preferences key
 * untouched — Kotlin/kotlinx.serialization has no object-spread over a
 * loosely-typed JSON map the way web's `{...prefs, ...patch}` does (M11.6 D7).
 */
fun mergedAssistantPreferencesPatch(current: Map<String, JsonElement>?, updated: AssistantPreferences): Map<String, JsonElement> {
    val result = current?.toMutableMap() ?: mutableMapOf()
    result["assistant"] = updated.toJson()
    return result
}
