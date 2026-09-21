package com.kairon.identity.api;

/**
 * A typed projection of {@code app_user.preferences.assistant} for the
 * {@code assistant} module — it cannot parse the raw {@code Map<String,Object>}
 * preferences blob itself (ArchUnit forbids reaching into {@code identity.domain}),
 * so {@code identity} does that parsing once and hands back a stable shape.
 * Every field defaults if the user has never touched assistant settings, so a
 * brand-new account needs no preferences backfill
 * (docs/milestones/M8-assistant-foundations.md D3).
 */
public record AssistantPreferencesView(
        boolean todoSuggestionsEnabled,
        boolean executionSummariesEnabled,
        boolean journalReflectionEnabled,
        String modelOverride,
        String tone) {
}
