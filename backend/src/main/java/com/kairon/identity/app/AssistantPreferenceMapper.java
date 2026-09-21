package com.kairon.identity.app;

import java.util.Map;

import com.kairon.identity.api.AssistantPreferencesView;

/**
 * Parses the schema-less {@code app_user.preferences.assistant} sub-object
 * defensively — every field falls back to a safe default if missing or the
 * wrong shape, so a brand-new account (empty {@code preferences}) needs no
 * migration-time backfill (docs/milestones/M8-assistant-foundations.md D3).
 */
final class AssistantPreferenceMapper {

    private static final String DEFAULT_TONE = "balanced";

    private AssistantPreferenceMapper() {
    }

    @SuppressWarnings("unchecked")
    static AssistantPreferencesView of(Map<String, Object> preferences) {
        Object raw = preferences == null ? null : preferences.get("assistant");
        Map<String, Object> assistant = raw instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        return new AssistantPreferencesView(
                featureEnabled(assistant, "todoSuggestions"),
                featureEnabled(assistant, "executionSummaries"),
                featureEnabled(assistant, "journalReflection"),
                stringOrNull(assistant.get("modelOverride")),
                stringOrDefault(assistant.get("tone"), DEFAULT_TONE));
    }

    @SuppressWarnings("unchecked")
    private static boolean featureEnabled(Map<String, Object> assistant, String feature) {
        Object raw = assistant.get(feature);
        if (raw instanceof Map<?, ?> m) {
            Object enabled = ((Map<String, Object>) m).get("enabled");
            return enabled instanceof Boolean b && b;
        }
        return false;
    }

    private static String stringOrNull(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private static String stringOrDefault(Object value, String fallback) {
        return value instanceof String s && !s.isBlank() ? s : fallback;
    }
}
