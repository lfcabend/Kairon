package com.kairon.assistant.app;

import java.util.Set;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.AssistantPreferencesView;
import com.kairon.identity.api.UserAccountApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The five {@code requireXAvailable} per-feature opt-in gates plus
 * {@link #resolveModel}, extracted out of {@link AssistantRunService} (D19)
 * so {@link McpAssistantService} can reuse exactly the same checks for the
 * MCP entry point (docs/milestones/M10.5-mcp-server.md D7) instead of
 * duplicating them. Same precedent as M9.5's {@code CategoryResolver}
 * extraction.
 */
@Component
class AssistantAvailability {

    private static final Logger log = LoggerFactory.getLogger(AssistantAvailability.class);

    // Validated model overrides (M8 §4.4 conversation) — an invalid per-user
    // override falls back to the instance default rather than being sent to the
    // SDK as-is and failing the call.
    private static final Set<String> KNOWN_MODELS =
            Set.of("claude-sonnet-5", "claude-opus-5", "claude-haiku-4-5");

    private final AssistantProperties properties;
    private final UserAccountApi accounts;

    AssistantAvailability(AssistantProperties properties, UserAccountApi accounts) {
        this.properties = properties;
        this.accounts = accounts;
    }

    AssistantPreferencesView requireTodoSuggestionsAvailable(UserId userId) {
        AssistantPreferencesView prefs = requireInstanceAvailable(userId);
        if (!prefs.todoSuggestionsEnabled()) {
            log.warn("Todo suggestions not opted into userId={}", userId.value());
            throw ApiException.forbidden("You haven't enabled todo suggestions in Settings.");
        }
        return prefs;
    }

    AssistantPreferencesView requireProjectGenerationAvailable(UserId userId) {
        AssistantPreferencesView prefs = requireInstanceAvailable(userId);
        if (!prefs.projectGenerationEnabled()) {
            log.warn("Project generation not opted into userId={}", userId.value());
            throw ApiException.forbidden("You haven't enabled project generation in Settings.");
        }
        return prefs;
    }

    AssistantPreferencesView requireProjectEditingAvailable(UserId userId) {
        AssistantPreferencesView prefs = requireInstanceAvailable(userId);
        if (!prefs.projectEditingEnabled()) {
            log.warn("Project editing not opted into userId={}", userId.value());
            throw ApiException.forbidden("You haven't enabled project editing in Settings.");
        }
        return prefs;
    }

    AssistantPreferencesView requireExecutionSummariesAvailable(UserId userId) {
        AssistantPreferencesView prefs = requireInstanceAvailable(userId);
        if (!prefs.executionSummariesEnabled()) {
            log.warn("Execution summaries not opted into userId={}", userId.value());
            throw ApiException.forbidden("You haven't enabled execution summaries in Settings.");
        }
        return prefs;
    }

    AssistantPreferencesView requireJournalReflectionAvailable(UserId userId) {
        AssistantPreferencesView prefs = requireInstanceAvailable(userId);
        if (!prefs.journalReflectionEnabled()) {
            log.warn("Journal reflection not opted into userId={}", userId.value());
            throw ApiException.forbidden("You haven't enabled journal reflection in Settings.");
        }
        return prefs;
    }

    String resolveModel(AssistantPreferencesView prefs) {
        if (prefs.modelOverride() != null && KNOWN_MODELS.contains(prefs.modelOverride())) {
            return prefs.modelOverride();
        }
        if (prefs.modelOverride() != null) {
            log.warn("Ignoring unknown modelOverride '{}', falling back to instance default", prefs.modelOverride());
        }
        return properties.model();
    }

    private AssistantPreferencesView requireInstanceAvailable(UserId userId) {
        if (!properties.available()) {
            log.warn("Assistant unavailable (master switch off or no API key) userId={}", userId.value());
            throw ApiException.forbidden("Assistant features are not enabled on this instance.");
        }
        return accounts.assistantPreferences(userId);
    }
}
