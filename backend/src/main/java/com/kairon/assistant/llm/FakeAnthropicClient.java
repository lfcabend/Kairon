package com.kairon.assistant.llm;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * A canned stand-in for {@link AnthropicClientImpl}, active only under the
 * {@code local} profile with {@code kairon.assistant.fake-client=true} — the
 * Playwright e2e run (docs/milestones/M8-assistant-foundations.md §9 Q3) sets
 * both so `task e2e` never makes a real network call to Anthropic. Imports
 * nothing from {@code com.anthropic..}, so it doesn't touch the
 * SDK-containment ArchUnit rule either way.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "kairon.assistant", name = "fake-client", havingValue = "true")
class FakeAnthropicClient implements AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(FakeAnthropicClient.class);

    @Override
    public TodoSuggestionsResult suggestTodos(TodoSuggestionRequest request) {
        log.info("FakeAnthropicClient.suggestTodos (local e2e stub) model={}", request.model());
        LocalDate day = parseFirstDate(request.userContent());
        List<SuggestedTaskPayload> suggestions = List.of(
                new SuggestedTaskPayload("Order cabinet hardware", null,
                        "Overdue kitchen-remodel task, unblocked", day, 20, null));
        return new TodoSuggestionsResult(suggestions, request.model(), 100, 50);
    }

    @Override
    public ProjectPlanResult generateProjectPlan(ProjectPlanRequest request) {
        log.info("FakeAnthropicClient.generateProjectPlan (local e2e stub) model={}", request.model());
        LocalDate start = parseFirstDate(request.userContent());
        List<PlannedTaskPayload> tasks = List.of(
                new PlannedTaskPayload("t1", null, "Design", null, false, start, start.plusDays(6), null),
                new PlannedTaskPayload("t2", "t1", "Pick materials", null, false, start, start.plusDays(2), null),
                new PlannedTaskPayload("m1", null, "Design approved", null, true,
                        start.plusDays(6), start.plusDays(6), null));
        List<PlannedDependencyPayload> dependencies = List.of(
                new PlannedDependencyPayload("t1", "m1", "FS", 0));
        ProjectPlanPayload plan = new ProjectPlanPayload("Generated project", "A canned e2e plan.", "M",
                tasks, dependencies);
        return new ProjectPlanResult(plan, request.model(), 100, 80);
    }

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    // The user content always opens with the requested day as the first ISO date
    // it mentions, regardless of DAY/WEEK phrasing — a regex is simpler and more
    // robust than assuming a fixed word position in that opening sentence.
    private static LocalDate parseFirstDate(String userContent) {
        Matcher m = ISO_DATE.matcher(userContent);
        if (m.find()) {
            return LocalDate.parse(m.group());
        }
        return LocalDate.now();
    }
}
