package com.kairon.assistant.app;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Assistant module settings ({@code kairon.assistant.*}). {@link #available()}
 * is the single "is the module dark or not" check (docs/adr/0002) — {@code
 * enabled} plus a non-blank key; per-user/per-feature opt-in is checked
 * separately via {@code UserAccountApi.assistantPreferences} (M8 D4).
 */
@ConfigurationProperties(prefix = "kairon.assistant")
public record AssistantProperties(
        boolean enabled,
        String apiKey,
        String model,
        Duration requestTimeout,
        long monthlyTokenBudgetPerUser,
        TodoSuggestions todoSuggestions,
        ProjectPlan projectPlan) {

    public AssistantProperties {
        if (model == null || model.isBlank()) {
            model = "claude-sonnet-5";
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            requestTimeout = Duration.ofSeconds(30);
        }
        if (monthlyTokenBudgetPerUser <= 0) {
            monthlyTokenBudgetPerUser = 500_000;
        }
        if (todoSuggestions == null) {
            todoSuggestions = new TodoSuggestions(7, 21, 5, 5, "MEDIUM");
        }
        if (projectPlan == null) {
            projectPlan = new ProjectPlan(40);
        }
    }

    /** Instance-level availability: master switch on and a key configured. */
    public boolean available() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /** Context-window sizing and output shaping for {@code TODO_SUGGESTION} runs (M8 D9/D5). */
    public record TodoSuggestions(
            int historyDays,
            int journalLookbackDays,
            int journalEntryLimit,
            int maxSuggestions,
            String effort) {

        public TodoSuggestions {
            if (historyDays <= 0) {
                historyDays = 7;
            }
            if (journalLookbackDays <= 0) {
                journalLookbackDays = 21;
            }
            if (journalEntryLimit <= 0) {
                journalEntryLimit = 5;
            }
            if (maxSuggestions <= 0) {
                maxSuggestions = 5;
            }
            if (effort == null || effort.isBlank()) {
                effort = "MEDIUM";
            }
        }
    }

    /** Output-size cap for {@code PROJECT_GENERATION} runs (M8.5 D7). */
    public record ProjectPlan(int maxTasks) {

        public ProjectPlan {
            if (maxTasks <= 0) {
                maxTasks = 40;
            }
        }
    }
}
