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
        ProjectPlan projectPlan,
        Summary summary,
        JournalReflection journalReflection) {

    public AssistantProperties {
        if (model == null || model.isBlank()) {
            model = "claude-sonnet-5";
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            requestTimeout = Duration.ofSeconds(120);
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
        if (summary == null) {
            summary = new Summary(null, null, null, 0, null);
        }
        if (journalReflection == null) {
            journalReflection = new JournalReflection(0, 0, null);
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

    /**
     * Cron schedule/timezone and context sizing for {@code WEEKLY_SUMMARY}/
     * {@code MONTHLY_SUMMARY} runs (M9 D9/D13). {@code zone} must stay UTC unless
     * {@code SummaryScheduler} is also given a zoned {@link java.time.Clock} — the
     * app's only {@code Clock} bean is {@code Clock.systemUTC()}, and the "yesterday"
     * period resolution in {@code SummaryScheduler} is computed from it.
     */
    public record Summary(String weeklyCron, String monthlyCron, String zone, int maxHighlightItems, String effort) {

        public Summary {
            if (weeklyCron == null || weeklyCron.isBlank()) {
                weeklyCron = "0 0 7 * * MON";
            }
            if (monthlyCron == null || monthlyCron.isBlank()) {
                monthlyCron = "0 0 7 1 * *";
            }
            if (zone == null || zone.isBlank()) {
                zone = "UTC";
            }
            if (maxHighlightItems <= 0) {
                maxHighlightItems = 8;
            }
            if (effort == null || effort.isBlank()) {
                effort = "HIGH";
            }
        }
    }

    /**
     * Context sizing for {@code JOURNAL_REFLECTION} runs (M10 D10/D16). No
     * cron/zone fields — unlike {@link Summary}, this kind has no
     * {@code @Scheduled} auto-trigger (M10 D4), on-demand only.
     */
    public record JournalReflection(int maxEntries, int maxProjects, String effort) {

        public JournalReflection {
            if (maxEntries <= 0) {
                maxEntries = 30; // M10 D16 — pathological-case guard, not an expected-case limit
            }
            if (maxProjects <= 0) {
                maxProjects = 5;
            }
            if (effort == null || effort.isBlank()) {
                effort = "HIGH"; // a reflective-synthesis task, same rationale as M9's summary.effort
            }
        }
    }
}
