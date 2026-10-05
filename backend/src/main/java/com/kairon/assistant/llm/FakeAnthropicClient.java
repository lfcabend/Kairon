package com.kairon.assistant.llm;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    // Keyed by the fake batch id this stub hands back from submitBatch — just
    // enough state so pollBatch/retrieveBatchResults can answer for the same
    // batch later, entirely in-memory (never a real Anthropic batch).
    private final Map<String, List<String>> fakeBatches = new ConcurrentHashMap<>();

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
                "Home", tasks, dependencies);
        return new ProjectPlanResult(plan, request.model(), 100, 80);
    }

    @Override
    public ProjectEditResult generateProjectEdit(ProjectEditRequest request) {
        log.info("FakeAnthropicClient.generateProjectEdit (local e2e stub) model={}", request.model());
        LocalDate day = parseFirstDate(request.userContent());
        TaskOperationPayload addTask = new TaskOperationPayload("ADD", null, "n1", null,
                "Canned e2e task", null, false, day, day.plusDays(1), null);
        ProjectEditPayload diff = new ProjectEditPayload(null, List.of(addTask), List.of(), List.of());
        return new ProjectEditResult(diff, request.model(), 90, 40);
    }

    @Override
    public SummaryResult generateSummary(SummaryRequest request) {
        log.info("FakeAnthropicClient.generateSummary (local e2e stub) model={}", request.model());
        String markdown = """
                ## This period: a canned e2e summary

                **What got done.** This is a fixed narrative stub for Playwright e2e —
                no real Anthropic call was made.

                **Where things slipped.** Nothing slipped; this is a stub.

                **Suggestions.**
                - This is a canned suggestion from FakeAnthropicClient.
                """;
        return new SummaryResult(markdown, request.model(), 120, 60);
    }

    @Override
    public ReflectionResult generateReflection(ReflectionRequest request) {
        log.info("FakeAnthropicClient.generateReflection (local e2e stub) model={}", request.model());
        String markdown = """
                This week reads steady, with a couple of specific threads worth naming.

                ### Patterns
                This is a canned e2e reflection stub for Playwright — no real Anthropic call
                was made.

                ### Worth noticing
                Same canned note — nothing computed here.

                ### A question to sit with
                What would make next week feel different?
                """;
        return new ReflectionResult(markdown, request.model(), 140, 90);
    }

    // M10 D17/§4.10 — resolves "ended" on first poll, so `task e2e` doesn't
    // need real wall-clock delay to exercise the scheduled-sweep batch path.
    @Override
    public BatchHandle submitBatch(List<BatchRequestItem> requests) {
        String fakeBatchId = "fake-batch-" + UUID.randomUUID();
        fakeBatches.put(fakeBatchId, requests.stream().map(BatchRequestItem::customId).toList());
        log.info("FakeAnthropicClient.submitBatch (local e2e stub) id={} requests={}", fakeBatchId, requests.size());
        return new BatchHandle(fakeBatchId);
    }

    @Override
    public BatchPollResult pollBatch(String anthropicBatchId) {
        return new BatchPollResult(true, "ENDED");
    }

    @Override
    public List<BatchResultItem> retrieveBatchResults(String anthropicBatchId) {
        List<String> customIds = fakeBatches.getOrDefault(anthropicBatchId, List.of());
        return customIds.stream()
                .map(customId -> new BatchResultItem(customId, true,
                        "## This period: a canned e2e batch summary\n\nNo real Anthropic call was made.",
                        150, 70, null))
                .toList();
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
