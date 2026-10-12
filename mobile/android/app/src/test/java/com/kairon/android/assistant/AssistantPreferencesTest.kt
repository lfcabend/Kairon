package com.kairon.android.assistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun parseJson(raw: String): Map<String, JsonElement> = Json.parseToJsonElement(raw).jsonObject

class AssistantPreferencesTest {

    @Test
    fun missingAssistantKeyFallsBackToAllDefaults() {
        val prefs = parseAssistantPreferences(preferences = null)

        assertFalse(prefs.todoSuggestions.enabled)
        assertFalse(prefs.projectGeneration.enabled)
        assertFalse(prefs.projectEditing.enabled)
        assertFalse(prefs.executionSummaries.enabled)
        assertFalse(prefs.journalReflection.enabled)
        assertNull(prefs.modelOverride)
        assertEquals("balanced", prefs.tone)
    }

    @Test
    fun aFullyPopulatedAssistantObjectParsesEveryField() {
        val preferences = parseJson(
            """
            {
              "assistant": {
                "todoSuggestions": {"enabled": true},
                "projectGeneration": {"enabled": true},
                "projectEditing": {"enabled": false},
                "executionSummaries": {"enabled": true},
                "journalReflection": {"enabled": true},
                "modelOverride": "claude-opus-5",
                "tone": "direct"
              }
            }
            """.trimIndent(),
        )

        val prefs = parseAssistantPreferences(preferences)

        assertTrue(prefs.todoSuggestions.enabled)
        assertTrue(prefs.projectGeneration.enabled)
        assertFalse(prefs.projectEditing.enabled)
        assertTrue(prefs.executionSummaries.enabled)
        assertTrue(prefs.journalReflection.enabled)
        assertEquals("claude-opus-5", prefs.modelOverride)
        assertEquals("direct", prefs.tone)
    }

    @Test
    fun aPartiallyPopulatedAssistantObjectFallsBackOnlyForMissingSubFields() {
        val preferences = parseJson(
            """{ "assistant": { "todoSuggestions": {"enabled": true} } }""",
        )

        val prefs = parseAssistantPreferences(preferences)

        assertTrue(prefs.todoSuggestions.enabled)
        assertFalse(prefs.projectGeneration.enabled)
        assertNull(prefs.modelOverride)
        assertEquals("balanced", prefs.tone)
    }

    @Test
    fun aWrongShapedAssistantValueFallsBackToDefaults() {
        val preferences = parseJson("""{ "assistant": "not-an-object" }""")

        val prefs = parseAssistantPreferences(preferences)

        assertFalse(prefs.todoSuggestions.enabled)
        assertEquals("balanced", prefs.tone)
    }

    @Test
    fun aWrongShapedToggleSubFieldFallsBackToDisabled() {
        val preferences = parseJson("""{ "assistant": { "todoSuggestions": "yes" } }""")

        val prefs = parseAssistantPreferences(preferences)

        assertFalse(prefs.todoSuggestions.enabled)
    }

    @Test
    fun aNullModelOverrideParsesAsNullNotTheLiteralStringNull() {
        val preferences = parseJson("""{ "assistant": { "modelOverride": null } }""")

        val prefs = parseAssistantPreferences(preferences)

        assertNull(prefs.modelOverride)
    }

    @Test
    fun mergedPatchPreservesOtherTopLevelPreferencesKeysUntouched() {
        val current = parseJson(
            """{ "timezone": {"value": "UTC"}, "assistant": { "tone": "balanced" } }""",
        )
        val updated = AssistantPreferences(tone = "direct")

        val merged = mergedAssistantPreferencesPatch(current, updated)

        assertTrue(merged.containsKey("timezone"))
        assertEquals("UTC", (merged["timezone"] as JsonObject)["value"]?.jsonPrimitive?.content)
        val assistant = merged["assistant"] as JsonObject
        assertEquals("direct", assistant["tone"]?.jsonPrimitive?.content)
    }

    @Test
    fun mergedPatchWithNoExistingPreferencesProducesOnlyTheAssistantKey() {
        val updated = AssistantPreferences(todoSuggestions = AssistantFeatureToggle(true))

        val merged = mergedAssistantPreferencesPatch(current = null, updated = updated)

        assertEquals(setOf("assistant"), merged.keys)
        val assistant = merged["assistant"] as JsonObject
        assertTrue((assistant["todoSuggestions"] as JsonObject)["enabled"]?.jsonPrimitive?.content.toBoolean())
    }

    @Test
    fun aRoundTripThroughMergeAndParsePreservesEveryField() {
        val updated = AssistantPreferences(
            todoSuggestions = AssistantFeatureToggle(true),
            projectGeneration = AssistantFeatureToggle(false),
            projectEditing = AssistantFeatureToggle(true),
            executionSummaries = AssistantFeatureToggle(false),
            journalReflection = AssistantFeatureToggle(true),
            modelOverride = "claude-sonnet-5",
            tone = "encouraging",
        )

        val merged = mergedAssistantPreferencesPatch(current = null, updated = updated)
        val roundTripped = parseAssistantPreferences(merged)

        assertEquals(updated, roundTripped)
    }
}
